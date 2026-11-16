package com.xiangqi.core;

/**
 * 一步棋：起点与终点。是否吃子、是否将军由规则层推导，客户端不可信。
 */
public record Move(Position from, Position to) {

    public Move {
        if (from == null || to == null) {
            throw new IllegalArgumentException("起点与终点不能为空");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("起点与终点不能相同");
        }
    }

    public static Move of(Position from, Position to) {
        return new Move(from, to);
    }
}
