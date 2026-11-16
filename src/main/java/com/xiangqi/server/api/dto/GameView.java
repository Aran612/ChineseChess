package com.xiangqi.server.api.dto;

import com.xiangqi.core.GameStatus;
import com.xiangqi.core.PieceType;
import com.xiangqi.core.Side;

import java.util.List;

/**
 * 对局视图。服务端一次性返回完整可渲染状态，客户端不持有任何权威数据。
 */
public record GameView(
        String gameId,
        Side turn,
        GameStatus status,
        String resultReason,
        boolean inCheck,
        int ply,
        MoveView lastMove,
        List<PieceView> pieces,
        List<MoveView> legalMoves,
        List<HistoryView> history
) {

    public record PositionView(int row, int col) {
    }

    public record PieceView(int row, int col, Side side, PieceType type, String symbol) {
    }

    public record MoveView(PositionView from, PositionView to) {
    }

    public record HistoryView(int index, Side side, MoveView move, String notation, String capturedSymbol) {
    }
}
