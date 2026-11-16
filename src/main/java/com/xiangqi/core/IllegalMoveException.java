package com.xiangqi.core;

/**
 * 非法走法或非法操作（对局已结束、无棋可悔等）。
 */
public class IllegalMoveException extends RuntimeException {

    public IllegalMoveException(String message) {
        super(message);
    }
}
