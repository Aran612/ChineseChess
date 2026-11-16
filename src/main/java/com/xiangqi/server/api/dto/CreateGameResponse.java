package com.xiangqi.server.api.dto;

/**
 * 新建对局的响应：token 只在此处返回一次，后续请求需通过 X-Game-Token 携带。
 */
public record CreateGameResponse(String gameId, String token, GameView game) {
}
