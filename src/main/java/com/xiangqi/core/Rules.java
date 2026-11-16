package com.xiangqi.core;

import com.xiangqi.core.piece.Piece;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则裁判：
 * 1. 生成伪合法走法（几何走法）；
 * 2. 过滤掉会导致己方将帅被将军、或将帅照面的走法；
 * 3. 提供将军检测（供胜负判定使用）。
 */
public final class Rules {

    private Rules() {
    }

    public static List<Move> legalMoves(Board board, Side side) {
        List<Move> moves = new ArrayList<>(64);
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.pieceAt(row, col);
                if (piece == null || piece.side() != side) {
                    continue;
                }
                Position from = new Position(row, col);
                for (Position target : piece.pseudoTargets(board, from)) {
                    Move move = new Move(from, target);
                    if (isSafeAfterMove(board, side, move)) {
                        moves.add(move);
                    }
                }
            }
        }
        return moves;
    }

    /**
     * 只生成吃子走法（静态搜索使用）。与 {@link #legalMoves} 一样已过滤“走后被将军”。
     */
    public static List<Move> legalCaptureMoves(Board board, Side side) {
        List<Move> moves = new ArrayList<>(16);
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.pieceAt(row, col);
                if (piece == null || piece.side() != side) {
                    continue;
                }
                Position from = new Position(row, col);
                for (Position target : piece.pseudoTargets(board, from)) {
                    if (board.pieceAt(target) == null) {
                        continue;
                    }
                    Move move = new Move(from, target);
                    if (isSafeAfterMove(board, side, move)) {
                        moves.add(move);
                    }
                }
            }
        }
        return moves;
    }

    public static List<Position> legalTargets(Board board, Side side, Position from) {
        Piece piece = board.pieceAt(from);
        if (piece == null || piece.side() != side) {
            return List.of();
        }
        List<Position> targets = new ArrayList<>(16);
        for (Position target : piece.pseudoTargets(board, from)) {
            if (isSafeAfterMove(board, side, new Move(from, target))) {
                targets.add(target);
            }
        }
        return targets;
    }

    /**
     * 完整合法性判断：先校验几何走法，再校验走后己方将帅安全。
     * 外部（例如对局状态机）必须使用该方法，防止客户端提交“瞬移”走法。
     */
    public static boolean isLegal(Board board, Side side, Move move) {
        Piece mover = board.pieceAt(move.from());
        if (mover == null || mover.side() != side) {
            return false;
        }
        if (!mover.pseudoTargets(board, move.from()).contains(move.to())) {
            return false;
        }
        return isSafeAfterMove(board, side, move);
    }

    private static boolean isSafeAfterMove(Board board, Side side, Move move) {
        Piece occupant = board.pieceAt(move.to());
        if (occupant != null && occupant.side() == side) {
            return false;
        }
        Piece captured = board.make(move);
        try {
            return !isInCheck(board, side);
        } finally {
            board.unmake(move, captured);
        }
    }

    /**
     * 当前是否被将军；将帅照面同样视为非法（本方“被将”）。
     * 找不到将帅时按“已被将死”处理，避免残局出现无限递归。
     */
    public static boolean isInCheck(Board board, Side side) {
        Position general = findGeneral(board, side);
        if (general == null) {
            return true;
        }
        return generalsFacing(board) || isSquareAttacked(board, general, side.opposite());
    }

    /**
     * 将帅是否在同一直线上且中间无子（“照面”）。
     */
    public static boolean generalsFacing(Board board) {
        Position red = findGeneral(board, Side.RED);
        Position black = findGeneral(board, Side.BLACK);
        if (red == null || black == null || red.col() != black.col()) {
            return false;
        }
        int from = Math.min(red.row(), black.row()) + 1;
        int to = Math.max(red.row(), black.row());
        for (int row = from; row < to; row++) {
            if (board.pieceAt(row, red.col()) != null) {
                return false;
            }
        }
        return true;
    }

    public static Position findGeneral(Board board, Side side) {
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.pieceAt(row, col);
                if (piece != null && piece.side() == side && piece.type() == PieceType.GENERAL) {
                    return new Position(row, col);
                }
            }
        }
        return null;
    }

    /**
     * 判断目标格是否被 bySide 攻击。
     * <p>
     * 仅用于将帅安全检查。仕/相受九宫与河界限制，不可能攻击到对方将帅，
     * 因此这里只枚举车、炮、马、兵卒的攻击，以及将帅照面。
     */
    public static boolean isSquareAttacked(Board board, Position target, Side bySide) {
        // 车与炮：沿四条直线扫描
        int[][] lines = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] line : lines) {
            int row = target.row() + line[0];
            int col = target.col() + line[1];
            boolean screenFound = false;
            while (Board.isInside(row, col)) {
                Piece piece = board.pieceAt(row, col);
                if (piece != null) {
                    if (!screenFound) {
                        if (piece.side() == bySide && piece.type() == PieceType.CHARIOT) {
                            return true;
                        }
                        screenFound = true;
                    } else {
                        if (piece.side() == bySide && piece.type() == PieceType.CANNON) {
                            return true;
                        }
                        break;
                    }
                }
                row += line[0];
                col += line[1];
            }
        }

        // 马：从 8 个可能的马位反推马腿
        int[][] horseOffsets = {
                {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
                {1, -2}, {1, 2}, {2, -1}, {2, 1}
        };
        for (int[] offset : horseOffsets) {
            int horseRow = target.row() + offset[0];
            int horseCol = target.col() + offset[1];
            if (!Board.isInside(horseRow, horseCol)) {
                continue;
            }
            Piece piece = board.pieceAt(horseRow, horseCol);
            if (piece == null || piece.side() != bySide || piece.type() != PieceType.HORSE) {
                continue;
            }
            int deltaRow = target.row() - horseRow;
            int deltaCol = target.col() - horseCol;
            int legRow = horseRow + (deltaRow == 2 ? 1 : deltaRow == -2 ? -1 : 0);
            int legCol = horseCol + (deltaCol == 2 ? 1 : deltaCol == -2 ? -1 : 0);
            if (board.pieceAt(legRow, legCol) == null) {
                return true;
            }
        }

        // 兵/卒：正面一步 + 过河后的横向一步
        if (bySide == Side.RED) {
            Piece forward = board.pieceAt(target.row() + 1, target.col());
            if (forward != null && forward.side() == Side.RED && forward.type() == PieceType.SOLDIER) {
                return true;
            }
            boolean crossed = target.row() <= 4;
            if (crossed && isSoldierAt(board, target.row(), target.col() - 1, Side.RED)) {
                return true;
            }
            if (crossed && isSoldierAt(board, target.row(), target.col() + 1, Side.RED)) {
                return true;
            }
        } else {
            Piece forward = board.pieceAt(target.row() - 1, target.col());
            if (forward != null && forward.side() == Side.BLACK && forward.type() == PieceType.SOLDIER) {
                return true;
            }
            boolean crossed = target.row() >= 5;
            if (crossed && isSoldierAt(board, target.row(), target.col() - 1, Side.BLACK)) {
                return true;
            }
            if (crossed && isSoldierAt(board, target.row(), target.col() + 1, Side.BLACK)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isSoldierAt(Board board, int row, int col, Side side) {
        if (!Board.isInside(row, col)) {
            return false;
        }
        Piece piece = board.pieceAt(row, col);
        return piece != null && piece.side() == side && piece.type() == PieceType.SOLDIER;
    }
}
