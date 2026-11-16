package com.xiangqi.server.store;

import com.xiangqi.core.GameStatus;
import com.xiangqi.core.Move;

import java.util.List;

/**
 * 落盘的对局记录。
 * <p>
 * 令牌只保存 SHA-256 摘要（十六进制），用于服务重启后恢复会话；摘要不会出现在任何公开接口中。
 */
public record StoredGame(
        String id,
        String code,
        String mode,
        String redTokenHash,
        String blackTokenHash,
        String sharedTokenHash,
        long createdAt,
        long updatedAt,
        List<Move> moves,
        GameStatus status,
        String resultReason
) {

    public static final String MODE_LOCAL = "LOCAL";
    public static final String MODE_ROOM = "ROOM";

    public boolean isRoom() {
        return MODE_ROOM.equals(mode);
    }
}
