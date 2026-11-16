package com.xiangqi.core.piece;

import com.xiangqi.core.Board;
import com.xiangqi.core.PieceType;
import com.xiangqi.core.Position;
import com.xiangqi.core.Side;

import java.util.ArrayList;
import java.util.List;

/**
 * 兵/卒：只进不退；过河后可左右横走。
 */
public final class Soldier extends Piece {

    public Soldier(Side side) {
        super(side, PieceType.SOLDIER);
    }

    @Override
    public List<Position> pseudoTargets(Board board, Position from) {
        List<Position> targets = new ArrayList<>(3);
        int forward = side() == Side.RED ? -1 : 1;
        addIfValid(board, targets, from.row() + forward, from.col());
        if (Board.crossedRiver(side(), from.row())) {
            addIfValid(board, targets, from.row(), from.col() - 1);
            addIfValid(board, targets, from.row(), from.col() + 1);
        }
        return targets;
    }

    private void addIfValid(Board board, List<Position> targets, int row, int col) {
        if (!Board.isInside(row, col)) {
            return;
        }
        Position target = new Position(row, col);
        if (canLand(board, target)) {
            targets.add(target);
        }
    }
}
