package com.xiangqi.core;

/**
 * 七种棋子类型及其显示字符。红黑双方的写法沿用传统棋谱字形。
 */
public enum PieceType {
    GENERAL('帥', '將', "将帅"),
    ADVISOR('仕', '士', "士"),
    ELEPHANT('相', '象', "象"),
    HORSE('馬', '馬', "马"),
    CHARIOT('車', '車', "车"),
    CANNON('炮', '砲', "炮"),
    SOLDIER('兵', '卒', "兵");

    private final char redSymbol;
    private final char blackSymbol;
    private final String label;

    PieceType(char redSymbol, char blackSymbol, String label) {
        this.redSymbol = redSymbol;
        this.blackSymbol = blackSymbol;
        this.label = label;
    }

    public char symbol(Side side) {
        return side == Side.RED ? redSymbol : blackSymbol;
    }

    public String label() {
        return label;
    }
}
