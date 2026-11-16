package com.xiangqi.server.api.dto;

import com.xiangqi.core.GameStatus;

/**
 * 历史对局摘要，用于“历史对局”列表。
 */
public record RecordSummary(
        String gameId,
        String mode,
        GameStatus status,
        String resultReason,
        int ply,
        long createdAt,
        long updatedAt
) {
}
