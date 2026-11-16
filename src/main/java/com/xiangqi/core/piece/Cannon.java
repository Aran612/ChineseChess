package com.xiangqi.core.piece;

import com.xiangqi.core.Board;
import com.xiangqi.core.PieceType;
import com.xiangqi.core.Position;
import com.xiangqi.core.Side;

import java.util.ArrayList;
import java.util.List;

/**
 * 炮：直线行走；吃子必须隔一个“炮架”翻山。
 */
public final class Cannon extends Piece {

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public Cannon(Side side) {
        super(side, PieceType.CANNON);
    }

    @Override
    public List<Position> pseudoTargets(Board board, Position from) {
        List<Position> targets = new ArrayList<>(16);
        for (int[] direction : DIRECTIONS) {
            boolean hasScreen = false;
            int row = from.row() + direction[0];
            int col = from.col() + direction[1];
            while (Board.isInside(row, col)) {
                Position target = new Position(row, col);
                if (!hasScreen) {
                    if (board.isEmpty(target)) {
                        targets.add(target);
                    } else {
                        hasScreen = true;
                    }
                } else if (!board.isEmpty(target)) {
                    if (board.pieceAt(target).side() != side()) {
                        targets.add(target);
                    }
                    break;
                }
                row += direction[0];
                col += direction[1];
            }
        }
        return targets;
    }
}
