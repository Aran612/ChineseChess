package com.xiangqi.core.piece;

import com.xiangqi.core.Board;
import com.xiangqi.core.PieceType;
import com.xiangqi.core.Position;
import com.xiangqi.core.Side;

import java.util.ArrayList;
import java.util.List;

/**
 * 相/象：斜走“田”字，象眼被堵则不可走，且不得过河。
 */
public final class Elephant extends Piece {

    private static final int[][] DIRECTIONS = {{2, 2}, {2, -2}, {-2, 2}, {-2, -2}};

    public Elephant(Side side) {
        super(side, PieceType.ELEPHANT);
    }

    @Override
    public List<Position> pseudoTargets(Board board, Position from) {
        List<Position> targets = new ArrayList<>(4);
        for (int[] direction : DIRECTIONS) {
            int row = from.row() + direction[0];
            int col = from.col() + direction[1];
            if (!Board.isInside(row, col) || Board.crossedRiver(side(), row)) {
                continue;
            }
            int eyeRow = from.row() + direction[0] / 2;
            int eyeCol = from.col() + direction[1] / 2;
            if (!board.isEmpty(new Position(eyeRow, eyeCol))) {
                continue;
            }
            Position target = new Position(row, col);
            if (canLand(board, target)) {
                targets.add(target);
            }
        }
        return targets;
    }
}
