package com.xiangqi.core;

import java.util.Objects;

/**
 * 棋盘坐标：row 0 为黑方底线，row 9 为红方底线；col 0 为最左列。
 */
public record Position(int row, int col) {

    public Position {
        if (!Board.isInside(row, col)) {
            throw new IllegalArgumentException("坐标超出棋盘范围: (" + row + ", " + col + ")");
        }
    }

    public static Position of(int row, int col) {
        return new Position(row, col);
    }

    public boolean sameAs(Position other) {
        return other != null && row == other.row && col == other.col;
    }

    @Override
    public String toString() {
        return "(" + row + "," + col + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Position other)) {
            return false;
        }
        return row == other.row && col == other.col;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col);
    }
}
