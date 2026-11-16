package com.xiangqi.server.error;

import org.springframework.http.HttpStatus;

/**
 * 业务异常：携带 HTTP 状态码与稳定的错误码，不向客户端暴露内部细节。
 */
public class GameException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private GameException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static GameException notFound() {
        return new GameException(HttpStatus.NOT_FOUND, "GAME_NOT_FOUND", "对局不存在或已过期");
    }

    public static GameException unauthorized() {
        return new GameException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "缺少对局令牌");
    }

    public static GameException forbidden() {
        return new GameException(HttpStatus.FORBIDDEN, "FORBIDDEN", "对局令牌无效");
    }

    public static GameException conflict(String message) {
        return new GameException(HttpStatus.CONFLICT, "GAME_CONFLICT", message);
    }

    public static GameException tooManyGames() {
        return new GameException(HttpStatus.SERVICE_UNAVAILABLE, "SERVER_BUSY", "服务繁忙，请稍后再试");
    }

    public static GameException badRequest(String message) {
        return new GameException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
