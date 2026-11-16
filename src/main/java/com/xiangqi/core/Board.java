package com.xiangqi.core;

import com.xiangqi.core.piece.Advisor;
import com.xiangqi.core.piece.Cannon;
import com.xiangqi.core.piece.Chariot;
import com.xiangqi.core.piece.Elephant;
import com.xiangqi.core.piece.General;
import com.xiangqi.core.piece.Horse;
import com.xiangqi.core.piece.Piece;
import com.xiangqi.core.piece.Soldier;

/**
 * 棋盘：10 行 x 9 列的交叉点棋盘。
 * <p>
 * 棋盘只负责“空间与落子”，不负责规则裁决；规则由 {@link Rules} 负责。
 * 提供 make/unmake 以便搜索引擎做无对象分配的回溯搜索。
 */
public final class Board {

    public static final int ROWS = 10;
    public static final int COLS = 9;

    private final Piece[][] grid = new Piece[ROWS][COLS];
    /** Zobrist 增量哈希，随 make/unmake 同步维护，避免每个搜索节点重扫棋盘。 */
    private long hash;

    private Board() {
    }

    /**
     * 空棋盘，便于测试与残局构造。
     */
    public static Board empty() {
        return new Board();
    }

    public static Board initial() {
        Board board = new Board();
        board.setupBackRank(0, Side.BLACK);
        board.setupBackRank(9, Side.RED);
        board.place(Side.BLACK, PieceType.CANNON, 2, 1);
        board.place(Side.BLACK, PieceType.CANNON, 2, 7);
        board.place(Side.BLACK, PieceType.SOLDIER, 3, 0);
        board.place(Side.BLACK, PieceType.SOLDIER, 3, 2);
        board.place(Side.BLACK, PieceType.SOLDIER, 3, 4);
        board.place(Side.BLACK, PieceType.SOLDIER, 3, 6);
        board.place(Side.BLACK, PieceType.SOLDIER, 3, 8);
        board.place(Side.RED, PieceType.CANNON, 7, 1);
        board.place(Side.RED, PieceType.CANNON, 7, 7);
        board.place(Side.RED, PieceType.SOLDIER, 6, 0);
        board.place(Side.RED, PieceType.SOLDIER, 6, 2);
        board.place(Side.RED, PieceType.SOLDIER, 6, 4);
        board.place(Side.RED, PieceType.SOLDIER, 6, 6);
        board.place(Side.RED, PieceType.SOLDIER, 6, 8);
        return board;
    }

    private void setupBackRank(int row, Side side) {
        place(side, PieceType.CHARIOT, row, 0);
        place(side, PieceType.HORSE, row, 1);
        place(side, PieceType.ELEPHANT, row, 2);
        place(side, PieceType.ADVISOR, row, 3);
        place(side, PieceType.GENERAL, row, 4);
        place(side, PieceType.ADVISOR, row, 5);
        place(side, PieceType.ELEPHANT, row, 6);
        place(side, PieceType.HORSE, row, 7);
        place(side, PieceType.CHARIOT, row, 8);
    }

    public static boolean isInside(int row, int col) {
        return row >= 0 && row < ROWS && col >= 0 && col < COLS;
    }

    public static boolean inPalace(Side side, int row, int col) {
        if (col < 3 || col > 5) {
            return false;
        }
        return side == Side.RED ? row >= 7 && row <= 9 : row >= 0 && row <= 2;
    }

    public static boolean crossedRiver(Side side, int row) {
        return side == Side.RED ? row <= 4 : row >= 5;
    }

    public Piece pieceAt(Position position) {
        return grid[position.row()][position.col()];
    }

    public Piece pieceAt(int row, int col) {
        return isInside(row, col) ? grid[row][col] : null;
    }

    public boolean isEmpty(Position position) {
        return pieceAt(position) == null;
    }

    /**
     * 目标点是否可以落子：空点，或对方棋子（可吃）。
     */
    public boolean canLand(Position target, Side mover) {
        if (!isInside(target.row(), target.col())) {
            return false;
        }
        Piece occupant = pieceAt(target);
        return occupant == null || occupant.side() != mover;
    }

    public Piece set(Position position, Piece piece) {
        int row = position.row();
        int col = position.col();
        Piece previous = grid[row][col];
        if (previous != null) {
            hash ^= Zobrist.pieceKey(previous, row, col);
        }
        if (piece != null) {
            hash ^= Zobrist.pieceKey(piece, row, col);
        }
        grid[row][col] = piece;
        return previous;
    }

    /**
     * 按类型放置一枚新棋子；空置传 null。
     */
    public void place(Side side, PieceType type, int row, int col) {
        if (!isInside(row, col)) {
            throw new IllegalArgumentException("坐标超出棋盘范围: (" + row + "," + col + ")");
        }
        set(new Position(row, col), type == null ? null : newPiece(side, type));
    }

    public void remove(int row, int col) {
        if (!isInside(row, col)) {
            throw new IllegalArgumentException("坐标超出棋盘范围: (" + row + "," + col + ")");
        }
        set(new Position(row, col), null);
    }

    private static Piece newPiece(Side side, PieceType type) {
        return switch (type) {
            case GENERAL -> new General(side);
            case ADVISOR -> new Advisor(side);
            case ELEPHANT -> new Elephant(side);
            case HORSE -> new Horse(side);
            case CHARIOT -> new Chariot(side);
            case CANNON -> new Cannon(side);
            case SOLDIER -> new Soldier(side);
        };
    }

    /**
     * 执行一步棋，返回被吃掉的棋子（可能为 null）。不做任何合法性校验。
     */
    public Piece make(Move move) {
        Position from = move.from();
        Position to = move.to();
        Piece mover = grid[from.row()][from.col()];
        if (mover == null) {
            throw new IllegalArgumentException("起点没有棋子: " + from);
        }
        Piece captured = grid[to.row()][to.col()];
        hash ^= Zobrist.pieceKey(mover, from.row(), from.col());
        hash ^= Zobrist.pieceKey(mover, to.row(), to.col());
        if (captured != null) {
            hash ^= Zobrist.pieceKey(captured, to.row(), to.col());
        }
        grid[to.row()][to.col()] = mover;
        grid[from.row()][from.col()] = null;
        return captured;
    }

    /**
     * 撤销 make。
     */
    public void unmake(Move move, Piece captured) {
        Position from = move.from();
        Position to = move.to();
        Piece mover = grid[to.row()][to.col()];
        hash ^= Zobrist.pieceKey(mover, to.row(), to.col());
        hash ^= Zobrist.pieceKey(mover, from.row(), from.col());
        if (captured != null) {
            hash ^= Zobrist.pieceKey(captured, to.row(), to.col());
        }
        grid[from.row()][from.col()] = mover;
        grid[to.row()][to.col()] = captured;
    }

    /**
     * 浅拷贝。棋子本身无状态，搜索与悔棋不需要深拷贝。
     */
    public Board copy() {
        Board copy = new Board();
        for (int row = 0; row < ROWS; row++) {
            System.arraycopy(grid[row], 0, copy.grid[row], 0, COLS);
        }
        copy.hash = hash;
        return copy;
    }

    /**
     * 当前棋盘与行棋方的 Zobrist 哈希。
     */
    public long zobristHash(Side turn) {
        return turn == Side.BLACK ? hash ^ Zobrist.SIDE_TO_MOVE_KEY : hash;
    }

    /**
     * 局面指纹（棋盘 + 行棋方），用于重复局面判定。
     */
    public String positionKey(Side turn) {
        StringBuilder builder = new StringBuilder(ROWS * COLS + 5);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                Piece piece = grid[row][col];
                builder.append(piece == null ? '.' : piece.symbol());
            }
        }
        builder.append('|').append(turn.name());
        return builder.toString();
    }
}
