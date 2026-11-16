package com.xiangqi.core.engine;

import com.xiangqi.core.Move;

/**
 * 搜索结果。bestMove 为 null 表示当前一方已无合法着法。
 */
public record SearchResult(
        Move bestMove,
        int score,
        int nodes,
        long elapsedMs,
        int completedDepth
) {
}
