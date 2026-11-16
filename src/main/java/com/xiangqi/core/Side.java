package com.xiangqi.core;

/**
 * 对弈方。红方先行。
 */
public enum Side {
    RED("红方"),
    BLACK("黑方");

    private final String label;

    Side(String label) {
        this.label = label;
    }

    public Side opposite() {
        return this == RED ? BLACK : RED;
    }

    public String label() {
        return label;
    }
}
