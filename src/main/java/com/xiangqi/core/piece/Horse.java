package com.xiangqi.core.piece;

import com.xiangqi.core.Board;
import com.xiangqi.core.PieceType;
import com.xiangqi.core.Position;
import com.xiangqi.core.Side;

import java.util.ArrayList;
import java.util.List;

/**
 * 马：走“日”字，马腿被堵则不可走。
 */
public final class Horse extends Piece {

    private static final int[][] MOVES = {
            {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
            {1, -2}, {1, 2}, {2, -1}, {2, 1}
    };

    public Horse(Side side) {
        super(side, PieceType.HORSE);
    }

    @Override
    public List<Position> pseudoTargets(Board board, Position from) {
        List<Position> targets = new ArrayList<>(8);
        for (int[] move : MOVES) {
            int row = from.row() + move[0];
            int col = from.col() + move[1];
            if (!Board.isInside(row, col)) {
                continue;
            }
            int legRow = from.row() + (move[0] == 2 ? 1 : move[0] == -2 ? -1 : 0);
            int legCol = from.col() + (move[1] == 2 ? 1 : move[1] == -2 ? -1 : 0);
            if (!board.isEmpty(new Position(legRow, legCol))) {
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
