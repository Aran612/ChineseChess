package com.xiangqi.core;

import com.xiangqi.core.piece.Piece;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 中文棋谱记谱法。
 * <ul>
 *   <li>红方列号从右到左为 一~九，黑方从左到右为 1~9；</li>
 *   <li>同一列有多个同种棋子时用“前/中/后”代替列号；</li>
 *   <li>车炮兵帅进退写步数，马相仕进退写目标列号。</li>
 * </ul>
 */
public final class Notation {

    private static final String[] CHINESE_DIGITS = {"一", "二", "三", "四", "五", "六", "七", "八", "九"};

    private Notation() {
    }

    public static String render(Board board, Move move, Side side) {
        Piece piece = board.pieceAt(move.from());
        if (piece == null || piece.side() != side) {
            return "";
        }
        PieceType type = piece.type();
        String pieceName = String.valueOf(type.symbol(side));

        String prefix = rankPrefix(board, move.from(), side, type);
        String head = prefix != null
                ? prefix + pieceName
                : pieceName + fileLabel(move.from().col(), side);

        if (move.from().row() == move.to().row()) {
            return head + "平" + fileLabel(move.to().col(), side);
        }

        boolean forward = side == Side.RED
                ? move.to().row() < move.from().row()
                : move.to().row() > move.from().row();
        String action = forward ? "进" : "退";

        if (type == PieceType.HORSE || type == PieceType.ELEPHANT || type == PieceType.ADVISOR) {
            return head + action + fileLabel(move.to().col(), side);
        }
        int rows = Math.abs(move.to().row() - move.from().row());
        return head + action + numberLabel(rows, side);
    }

    private static String fileLabel(int col, Side side) {
        return side == Side.RED ? CHINESE_DIGITS[Board.COLS - 1 - col] : String.valueOf(col + 1);
    }

    private static String numberLabel(int number, Side side) {
        if (side == Side.BLACK) {
            return String.valueOf(number);
        }
        return number >= 1 && number <= 9 ? CHINESE_DIGITS[number - 1] : String.valueOf(number);
    }

    /**
     * 同列同种棋子达到 2~3 枚时返回“前/中/后”，否则返回 null。
     */
    private static String rankPrefix(Board board, Position from, Side side, PieceType type) {
        List<Position> sameFile = new ArrayList<>(3);
        for (int row = 0; row < Board.ROWS; row++) {
            Piece piece = board.pieceAt(row, from.col());
            if (piece != null && piece.side() == side && piece.type() == type) {
                sameFile.add(new Position(row, from.col()));
            }
        }
        if (sameFile.size() < 2 || sameFile.size() > 3) {
            return null;
        }
        Comparator<Position> frontFirst = Comparator.comparingInt(
                position -> side == Side.RED ? position.row() : -position.row());
        sameFile.sort(frontFirst);
        int index = sameFile.indexOf(from);
        if (sameFile.size() == 2) {
            return index == 0 ? "前" : "后";
        }
        return index == 0 ? "前" : index == 1 ? "中" : "后";
    }
}
