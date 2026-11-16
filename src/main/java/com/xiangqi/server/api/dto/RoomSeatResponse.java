package com.xiangqi.server.api.dto;

/**
 * 进入房间的响应。role 为 RED / BLACK / SPECTATOR；观战者 token 为空。
 */
public record RoomSeatResponse(
        String code,
        String gameId,
        String token,
        String role,
        RoomView room
) {
}
