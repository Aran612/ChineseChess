package com.xiangqi.core;

import com.xiangqi.core.piece.Piece;

import java.util.SplittableRandom;

/**
 * Zobrist 哈希：每个“棋子类型 + 颜色 + 坐标”预生成一个随机数，
 * 局面哈希由所有棋子对应随机数异或得到；行棋方再异或一个专用随机数。
 * <p>
 * 随机数使用固定种子生成，保证同一进程内与多次运行之间结果稳定（便于测试与置换表复用）。
 */
public final class Zobrist {

    private static final int SQUARES = Board.ROWS * Board.COLS;
    private static final long[][][] PIECE_KEYS =
            new long[PieceType.values().length][2][SQUARES];
    public static final long SIDE_TO_MOVE_KEY;

    static {
        SplittableRandom random = new SplittableRandom(0x5EED_5EED_5EEDL);
        for (int type = 0; type < PieceType.values().length; type++) {
            for (int side = 0; side < 2; side++) {
                for (int square = 0; square < SQUARES; square++) {
                    PIECE_KEYS[type][side][square] = random.nextLong();
                }
            }
        }
        SIDE_TO_MOVE_KEY = random.nextLong();
    }

    private Zobrist() {
    }

    public static long pieceKey(Piece piece, int row, int col) {
        return pieceKey(piece.type(), piece.side(), row, col);
    }

    public static long pieceKey(PieceType type, Side side, int row, int col) {
        return PIECE_KEYS[type.ordinal()][side == Side.RED ? 0 : 1][row * Board.COLS + col];
    }

    /**
     * 按当前棋盘与行棋方计算完整哈希。仅用于校验，正常搜索路径使用 {@link Board#zobristHash(Side)}。
     */
    public static long hash(Board board, Side turn) {
        long key = 0L;
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.pieceAt(row, col);
                if (piece != null) {
                    key ^= pieceKey(piece, row, col);
                }
            }
        }
        return turn == Side.BLACK ? key ^ SIDE_TO_MOVE_KEY : key;
    }
}
