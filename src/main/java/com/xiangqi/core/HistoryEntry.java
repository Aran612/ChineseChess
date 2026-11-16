package com.xiangqi.core;

import com.xiangqi.core.piece.Piece;

/**
 * 一步棋的完整记录。captured 仅用于悔棋与棋谱展示。
 */
public record HistoryEntry(
        int index,
        Side side,
        Move move,
        Piece captured,
        String notation,
        int noCaptureBefore
) {

    public String capturedSymbol() {
        return captured == null ? null : String.valueOf(captured.symbol());
    }
}
