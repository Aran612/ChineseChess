package com.xiangqi.core;

/**
 * 对局状态。困毙（无子可动）在中国象棋中判负，因此红方/黑方胜均可能是“将死”或“困毙”。
 */
public enum GameStatus {
    PLAYING("进行中"),
    RED_WIN("红方胜"),
    BLACK_WIN("黑方胜"),
    DRAW("和棋");

    private final String label;

    GameStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
