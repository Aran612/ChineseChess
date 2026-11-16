package com.xiangqi.server.api.dto;

import com.xiangqi.core.GameStatus;
import com.xiangqi.core.Side;

import java.util.List;

/**
 * 复盘数据：初始局面 + 每步落子后的完整局面快照，客户端可离线逐步浏览。
 */
public record ReplayView(
        String gameId,
        String mode,
        GameStatus status,
        String resultReason,
        long createdAt,
        long updatedAt,
        List<FrameView> frames
) {

    public record FrameView(
            int ply,
            Side mover,
            GameView.MoveView move,
            String notation,
            String capturedSymbol,
            Side turn,
            boolean inCheck,
            GameStatus status,
            String resultReason,
            GameView.MoveView lastMove,
            List<GameView.PieceView> pieces
    ) {
    }
}
