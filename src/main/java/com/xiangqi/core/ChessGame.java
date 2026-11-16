package com.xiangqi.core;

import com.xiangqi.core.piece.Piece;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一局棋的完整状态机。
 * <p>
 * 所有对外接口都只接受“起终点”两步走法，是否合法由服务端规则权威判定。
 */
public final class ChessGame {

    /** 连续 60 回合（120 个半回合）无吃子判和。 */
    public static final int MAX_PLIES_WITHOUT_CAPTURE = 120;
    /** 同一局面出现三次判和（长将/长捉按和棋处理）。 */
    public static final int REPETITION_DRAW_LIMIT = 3;

    private final Board board;
    private final List<HistoryEntry> history = new ArrayList<>();
    private final Map<String, Integer> repetition = new HashMap<>();

    private Side turn = Side.RED;
    private GameStatus status = GameStatus.PLAYING;
    private String resultReason = "";
    private Move lastMove;
    private int noCapturePlies;

    private ChessGame(Board board) {
        this.board = board;
        repetition.put(positionKey(), 1);
    }

    public static ChessGame start() {
        return new ChessGame(Board.initial());
    }

    /*用于测试或残局推演：以指定棋盘与行棋方开局。*/
    public static ChessGame fromPosition(Board board, Side turn) {
        ChessGame game = new ChessGame(board);
        game.turn = turn;
        game.repetition.clear();
        game.repetition.put(game.positionKey(), 1);
        return game;
    }
    //棋盘，回合，状态，结果，最后一步，无吃子回合数，历史记录
    public Board board() {return board;}
    public Side turn() {return turn;}
    public GameStatus status() {return status;}
    public String resultReason() {return resultReason;}
    public Move lastMove() {return lastMove;}
    public int plyCount() {return history.size();}
    public List<HistoryEntry> history() {return List.copyOf(history);}
    //是否被将
    public boolean isInCheck() {return Rules.isInCheck(board, turn);}
    //合法走法
    public List<Move> legalMoves() {
        if (status != GameStatus.PLAYING) {
            return List.of();
        }
        return Rules.legalMoves(board, turn);
    }

    public boolean isFinished() {return status != GameStatus.PLAYING;}

    public void play(Move move) {
        if (status != GameStatus.PLAYING) {
            throw new IllegalMoveException("对局已结束，无法继续走棋");
        }
        if (!Rules.isLegal(board, turn, move)) {
            throw new IllegalMoveException("该走法不合法");
        }

        String notation = Notation.render(board, move, turn);
        int noCaptureBefore = noCapturePlies;
        Piece captured = board.make(move);
        history.add(new HistoryEntry(history.size() + 1, turn, move, captured, notation, noCaptureBefore));
        lastMove = move;
        noCapturePlies = captured == null ? noCapturePlies + 1 : 0;

        Side mover = turn;
        turn = turn.opposite();
        repetition.merge(positionKey(), 1, Integer::sum);
        updateStatus(mover, captured);
    }

    public void undo() {
        if (history.isEmpty()) {
            throw new IllegalMoveException("当前没有可悔的棋步");
        }
        HistoryEntry last = history.remove(history.size() - 1);
        String currentKey = positionKey();
        repetition.computeIfPresent(currentKey, (key, count) -> count <= 1 ? null : count - 1);

        board.unmake(last.move(), last.captured());
        turn = last.side();
        noCapturePlies = last.noCaptureBefore();
        status = GameStatus.PLAYING;
        resultReason = "";
        lastMove = history.isEmpty() ? null : history.get(history.size() - 1).move();
    }

    public void resign(Side side) {
        if (status != GameStatus.PLAYING) {
            throw new IllegalMoveException("对局已结束");
        }
        status = side == Side.RED ? GameStatus.BLACK_WIN : GameStatus.RED_WIN;
        resultReason = side.label() + "认输";
    }

    private void updateStatus(Side mover, Piece captured) {
        if (captured != null && captured.type() == PieceType.GENERAL) {
            status = mover == Side.RED ? GameStatus.RED_WIN : GameStatus.BLACK_WIN;
            resultReason = "主将被擒";
            return;
        }
        if (Rules.legalMoves(board, turn).isEmpty()) {
            status = mover == Side.RED ? GameStatus.RED_WIN : GameStatus.BLACK_WIN;
            resultReason = Rules.isInCheck(board, turn) ? "将死" : "困毙";
            return;
        }
        if (repetition.getOrDefault(positionKey(), 0) >= REPETITION_DRAW_LIMIT) {
            status = GameStatus.DRAW;
            resultReason = "三次重复局面";
            return;
        }
        if (noCapturePlies >= MAX_PLIES_WITHOUT_CAPTURE) {
            status = GameStatus.DRAW;
            resultReason = "连续 60 回合未吃子";
        }
    }

    private String positionKey() {return board.positionKey(turn);}
}
