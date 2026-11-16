package com.xiangqi.core.engine;

import com.xiangqi.core.Board;
import com.xiangqi.core.Move;
import com.xiangqi.core.Rules;
import com.xiangqi.core.Side;
import com.xiangqi.core.piece.Piece;

import java.util.ArrayList;
import java.util.List;

/**
 * Negamax + Alpha-Beta 搜索引擎。
 * <p>
 * 增强项：
 * <ul>
 *   <li>迭代加深：超时后使用上一层完整结果；</li>
 *   <li>Zobrist 置换表：缓存精确值/上下界，并提供最佳走法用于排序；</li>
 *   <li>静态搜索：叶子节点只扩展吃子，避免“地平线效应”；</li>
 *   <li>走法排序：置换表最佳走法 → MVV-LVA 吃子 → 杀手走法 → 历史启发。</li>
 * </ul>
 * 引擎实例持有搜索状态，请每次搜索使用独立实例（服务层按请求创建）。
 */
public final class SearchEngine {

    public static final int INF = 1_000_000;
    private static final int MATE_SCORE = 500_000;
    private static final int MATE_THRESHOLD = MATE_SCORE - 1_000;
    private static final int MAX_PLY = 128;
    private static final int MAX_QUIESCENCE_PLY = 32;
    private static final int SQUARES = Board.ROWS * Board.COLS;

    private final TranspositionTable table;
    private final Move[][] killers = new Move[MAX_PLY][2];
    private final int[][] history = new int[2][SQUARES * SQUARES];

    private long deadline;
    private int nodes;

    public SearchEngine() {
        this(new TranspositionTable(16));
    }

    public SearchEngine(TranspositionTable table) {
        this.table = table;
    }

    public SearchResult search(Board board, Side side, int maxDepth, long timeLimitMs) {
        long startNanos = System.nanoTime();
        deadline = startNanos + Math.max(50, timeLimitMs) * 1_000_000L;
        nodes = 0;

        List<Move> rootMoves = Rules.legalMoves(board, side);
        if (rootMoves.isEmpty()) {
            return new SearchResult(null, -MATE_SCORE, 0, 0, 0);
        }

        Move bestMove = rootMoves.get(0);
        int bestScore = -INF;
        int completedDepth = 0;

        try {
            for (int depth = 1; depth <= Math.max(1, maxDepth); depth++) {
                List<Move> ordered = orderMoves(board, rootMoves, 0, bestMove);
                int alpha = -INF;
                int localBestScore = -INF;
                Move localBest = ordered.get(0);

                for (Move move : ordered) {
                    Piece captured = board.make(move);
                    int score;
                    try {
                        score = -negamax(board, side.opposite(), depth - 1, -INF, -alpha, 1);
                    } finally {
                        // 超时中断也必须回滚，否则棋盘会被留在半途状态
                        board.unmake(move, captured);
                    }
                    if (score > localBestScore) {
                        localBestScore = score;
                        localBest = move;
                    }
                    if (score > alpha) {
                        alpha = score;
                    }
                }

                bestMove = localBest;
                bestScore = localBestScore;
                completedDepth = depth;
                if (bestScore > MATE_THRESHOLD) {
                    // 已找到可强制将杀，无需继续加深
                    break;
                }
            }
        } catch (SearchAborted ignored) {
            // 超时中断，保留上一层完整迭代的结果
        }

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        return new SearchResult(bestMove, bestScore, nodes, elapsedMs, completedDepth);
    }

    private int negamax(Board board, Side side, int depth, int alpha, int beta, int ply) {
        if ((++nodes & 1023) == 0 && System.nanoTime() > deadline) {
            throw SearchAborted.INSTANCE;
        }
        if (ply >= MAX_PLY - 2) {
            return Evaluator.evaluate(board, side);
        }

        long key = board.zobristHash(side);
        TranspositionTable.Entry entry = table.probe(key);
        Move ttMove = entry == null ? null : entry.bestMove();
        int alphaOriginal = alpha;

        if (entry != null && entry.depth() >= depth) {
            int ttScore = entry.score();
            // 置换表中的“将杀分数”保存时已按 ply 换算，取回时需要还原
            if (ttScore > MATE_THRESHOLD) {
                ttScore -= ply;
            } else if (ttScore < -MATE_THRESHOLD) {
                ttScore += ply;
            }
            switch (entry.bound()) {
                case EXACT -> {
                    return ttScore;
                }
                case LOWER -> {
                    if (ttScore > alpha) {
                        alpha = ttScore;
                    }
                }
                case UPPER -> {
                    if (ttScore < beta) {
                        beta = ttScore;
                    }
                }
            }
            if (alpha >= beta) {
                return ttScore;
            }
        }

        if (depth <= 0) {
            return quiescence(board, side, alpha, beta, ply, 0);
        }

        List<Move> moves = Rules.legalMoves(board, side);
        if (moves.isEmpty()) {
            // 将死或困毙：当前行棋方判负，越早发生分越低
            return -MATE_SCORE + ply;
        }

        int bestScore = -INF;
        Move bestMove = null;

        for (Move move : orderMoves(board, moves, ply, ttMove)) {
            Piece captured = board.make(move);
            int score;
            try {
                score = -negamax(board, side.opposite(), depth - 1, -beta, -alpha, ply + 1);
            } finally {
                board.unmake(move, captured);
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            if (score >= beta) {
                if (captured == null) {
                    rememberKiller(ply, move);
                    int index = squareIndex(move.from()) * SQUARES + squareIndex(move.to());
                    history[side.ordinal()][index] += depth * depth;
                }
                store(key, depth, beta, TranspositionTable.Bound.LOWER, move, ply);
                return beta;
            }
            if (score > alpha) {
                alpha = score;
            }
        }

        TranspositionTable.Bound bound = bestScore <= alphaOriginal
                ? TranspositionTable.Bound.UPPER
                : TranspositionTable.Bound.EXACT;
        store(key, depth, bestScore, bound, bestMove, ply);
        return bestScore;
    }

    /**
     * 静态搜索：只考虑吃子，并以“当前局面静态评估”作为下界（stand pat）。
     */
    private int quiescence(Board board, Side side, int alpha, int beta, int ply, int qDepth) {
        if ((++nodes & 1023) == 0 && System.nanoTime() > deadline) {
            throw SearchAborted.INSTANCE;
        }

        int standPat = Evaluator.evaluate(board, side);
        if (standPat >= beta) {
            return beta;
        }
        if (standPat > alpha) {
            alpha = standPat;
        }
        if (qDepth >= MAX_QUIESCENCE_PLY) {
            return alpha;
        }

        List<Move> captures = Rules.legalCaptureMoves(board, side);
        if (captures.isEmpty()) {
            return alpha;
        }
        for (Move move : orderMoves(board, captures, ply, null)) {
            Piece captured = board.make(move);
            int score;
            try {
                score = -quiescence(board, side.opposite(), -beta, -alpha, ply + 1, qDepth + 1);
            } finally {
                board.unmake(move, captured);
            }
            if (score >= beta) {
                return beta;
            }
            if (score > alpha) {
                alpha = score;
            }
        }
        return alpha;
    }

    private void store(long key, int depth, int score, TranspositionTable.Bound bound,
                       Move bestMove, int ply) {
        int storedScore = score;
        if (storedScore > MATE_THRESHOLD) {
            storedScore += ply;
        } else if (storedScore < -MATE_THRESHOLD) {
            storedScore -= ply;
        }
        table.store(key, depth, storedScore, bound, bestMove);
    }

    private void rememberKiller(int ply, Move move) {
        Move[] slot = killers[Math.min(ply, MAX_PLY - 1)];
        if (slot[0] != null && slot[0].equals(move)) {
            return;
        }
        slot[1] = slot[0];
        slot[0] = move;
    }

    private List<Move> orderMoves(Board board, List<Move> moves, int ply, Move ttMove) {
        List<Move> ordered = new ArrayList<>(moves);
        ordered.sort((first, second) -> Integer.compare(
                moveScore(board, second, ply, ttMove),
                moveScore(board, first, ply, ttMove)));
        return ordered;
    }

    private int moveScore(Board board, Move move, int ply, Move ttMove) {
        if (ttMove != null && move.equals(ttMove)) {
            return 1_000_000;
        }
        Piece attacker = board.pieceAt(move.from());
        Piece victim = board.pieceAt(move.to());
        if (attacker != null && victim != null) {
            return 900_000 + Evaluator.materialValue(victim.type()) * 10
                    - Evaluator.materialValue(attacker.type());
        }
        Move[] killer = killers[Math.min(ply, MAX_PLY - 1)];
        if (killer[0] != null && killer[0].equals(move)) {
            return 800_000;
        }
        if (killer[1] != null && killer[1].equals(move)) {
            return 790_000;
        }
        if (attacker == null) {
            return 0;
        }
        int index = squareIndex(move.from()) * SQUARES + squareIndex(move.to());
        return history[attacker.side().ordinal()][index];
    }

    private int squareIndex(com.xiangqi.core.Position position) {
        return position.row() * Board.COLS + position.col();
    }

    private static final class SearchAborted extends RuntimeException {
        private static final SearchAborted INSTANCE = new SearchAborted();

        private SearchAborted() {
            super(null, null, false, false);
        }
    }
}
