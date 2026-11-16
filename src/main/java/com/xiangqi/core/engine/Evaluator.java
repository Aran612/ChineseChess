package com.xiangqi.core.engine;

import com.xiangqi.core.Board;
import com.xiangqi.core.PieceType;
import com.xiangqi.core.Side;
import com.xiangqi.core.piece.Piece;

/**
 * 局面评估：子力价值 + 位置分（以红方视角计算，黑方镜像）。
 */
public final class Evaluator {

    private static final int[] MATERIAL = {
            100_000, // 帥/將
            200,     // 仕/士
            200,     // 相/象
            400,     // 馬
            900,     // 車
            450,     // 炮
            100      // 兵/卒
    };

    /** 兵/卒位置分（红方视角，row 0 为黑方底线）。 */
    private static final int[][] SOLDIER_PST = {
            {20, 20, 30, 40, 40, 40, 30, 20, 20},
            {20, 30, 40, 50, 50, 50, 40, 30, 20},
            {20, 30, 40, 50, 50, 50, 40, 30, 20},
            {20, 25, 35, 45, 45, 45, 35, 25, 20},
            {15, 20, 30, 40, 40, 40, 30, 20, 15},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0}
    };

    private static final int[][] CHARIOT_PST = {
            {10, 12, 14, 16, 18, 16, 14, 12, 10},
            {12, 14, 18, 22, 24, 22, 18, 14, 12},
            {12, 16, 20, 26, 28, 26, 20, 16, 12},
            {12, 16, 22, 28, 30, 28, 22, 16, 12},
            {12, 16, 22, 28, 30, 28, 22, 16, 12},
            {12, 16, 20, 26, 28, 26, 20, 16, 12},
            {10, 14, 18, 24, 26, 24, 18, 14, 10},
            {8, 12, 16, 20, 22, 20, 16, 12, 8},
            {6, 10, 14, 16, 18, 16, 14, 10, 6},
            {4, 8, 10, 12, 14, 12, 10, 8, 4}
    };

    private static final int[][] HORSE_PST = {
            {0, 2, 4, 6, 8, 6, 4, 2, 0},
            {2, 6, 10, 14, 16, 14, 10, 6, 2},
            {4, 10, 16, 20, 24, 20, 16, 10, 4},
            {6, 12, 18, 24, 28, 24, 18, 12, 6},
            {6, 12, 18, 24, 28, 24, 18, 12, 6},
            {6, 12, 18, 24, 28, 24, 18, 12, 6},
            {5, 10, 15, 20, 22, 20, 15, 10, 5},
            {4, 8, 12, 16, 18, 16, 12, 8, 4},
            {2, 4, 8, 10, 12, 10, 8, 4, 2},
            {0, 2, 4, 6, 8, 6, 4, 2, 0}
    };

    private static final int[][] CANNON_PST = {
            {0, 2, 4, 6, 8, 6, 4, 2, 0},
            {2, 4, 8, 12, 14, 12, 8, 4, 2},
            {4, 8, 12, 18, 20, 18, 12, 8, 4},
            {4, 8, 14, 20, 22, 20, 14, 8, 4},
            {4, 8, 14, 20, 22, 20, 14, 8, 4},
            {4, 8, 12, 18, 20, 18, 12, 8, 4},
            {2, 6, 10, 14, 16, 14, 10, 6, 2},
            {2, 4, 8, 12, 14, 12, 8, 4, 2},
            {0, 2, 4, 6, 8, 6, 4, 2, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0}
    };

    private static final int[][] ADVISOR_PST = new int[10][9];
    private static final int[][] ELEPHANT_PST = new int[10][9];
    private static final int[][] GENERAL_PST = new int[10][9];

    static {
        ADVISOR_PST[8][4] = 8;
        ELEPHANT_PST[7][2] = 6;
        ELEPHANT_PST[7][6] = 6;
    }

    private Evaluator() {
    }

    public static int materialValue(PieceType type) {
        return MATERIAL[type.ordinal()];
    }

    public static int evaluate(Board board, Side perspective) {
        int redScore = 0;
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.pieceAt(row, col);
                if (piece == null) {
                    continue;
                }
                int pst = pstValue(piece, row, col);
                int value = materialValue(piece.type()) + pst;
                redScore += piece.side() == Side.RED ? value : -value;
            }
        }
        return perspective == Side.RED ? redScore : -redScore;
    }

    private static int pstValue(Piece piece, int row, int col) {
        int mirrorRow = piece.side() == Side.RED ? row : Board.ROWS - 1 - row;
        return switch (piece.type()) {
            case SOLDIER -> SOLDIER_PST[mirrorRow][col];
            case CHARIOT -> CHARIOT_PST[mirrorRow][col];
            case HORSE -> HORSE_PST[mirrorRow][col];
            case CANNON -> CANNON_PST[mirrorRow][col];
            case ADVISOR -> ADVISOR_PST[mirrorRow][col];
            case ELEPHANT -> ELEPHANT_PST[mirrorRow][col];
            case GENERAL -> GENERAL_PST[mirrorRow][col];
        };
    }
}
