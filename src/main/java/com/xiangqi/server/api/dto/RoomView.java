package com.xiangqi.server.api.dto;

/**
 * 联网房间视图：对局状态 + 席位占用情况 + 版本号。
 * 观战者无需令牌即可读取。
 */
public record RoomView(
        String code,
        String gameId,
        boolean redJoined,
        boolean blackJoined,
        long version,
        String undoRequest,
        GameView game
) {
}
