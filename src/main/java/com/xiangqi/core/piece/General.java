package com.xiangqi.core.piece;

import com.xiangqi.core.Board;
import com.xiangqi.core.PieceType;
import com.xiangqi.core.Position;
import com.xiangqi.core.Side;

import java.util.ArrayList;
import java.util.List;

/**
 * 帅/将：九宫内沿直线走一步。
 * 将帅照面由 {@link com.xiangqi.core.Rules} 统一判定。
 */
public final class General extends Piece {

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public General(Side side) {
        super(side, PieceType.GENERAL);
    }

    @Override
    public List<Position> pseudoTargets(Board board, Position from) {
        List<Position> targets = new ArrayList<>(4);
        for (int[] direction : DIRECTIONS) {
            int row = from.row() + direction[0];
            int col = from.col() + direction[1];
            if (!Board.isInside(row, col) || !Board.inPalace(side(), row, col)) {
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
