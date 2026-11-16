package com.xiangqi.core.piece;

import com.xiangqi.core.Board;
import com.xiangqi.core.PieceType;
import com.xiangqi.core.Position;
import com.xiangqi.core.Side;

import java.util.List;

/**
 * 棋子抽象基类。所有子类只负责“几何走法”（伪合法），
 * 是否会导致己方将帅被将军由规则层统一过滤。
 */
public abstract class Piece {

    private final Side side;
    private final PieceType type;

    protected Piece(Side side, PieceType type) {
        this.side = side;
        this.type = type;
    }

    public Side side() {
        return side;
    }

    public PieceType type() {
        return type;
    }

    public char symbol() {
        return type.symbol(side);
    }

    public String displayName() {
        return String.valueOf(symbol());
    }

    /**
     * 生成伪合法目标点（不判断己方将帅是否被将军、不判断将帅照面）。
     */
    public abstract List<Position> pseudoTargets(Board board, Position from);

    protected final boolean canLand(Board board, Position target) {
        return board.canLand(target, side);
    }

    protected final boolean isInside(int row, int col) {
        return Board.isInside(row, col);
    }
}
