package com.xiangqi.core.engine;

import com.xiangqi.core.Move;

/**
 * 置换表：以 Zobrist 哈希为索引，缓存搜索结果。
 * <p>
 * 采用固定大小的直接映射表：同一个槽位只保留一条记录，冲突时覆盖。
 * 数组元素为不可变记录，引用赋值在 JVM 中原子，因此无需加锁即可安全读取。
 */
public final class TranspositionTable {

    public enum Bound {
        /** 精确值。 */
        EXACT,
        /** 只保证“不小于该值”（搜索中发生 beta 截断）。 */
        LOWER,
        /** 只保证“不大于该值”（搜索中未能超过 alpha）。 */
        UPPER
    }

    public record Entry(long key, int depth, int score, Bound bound, Move bestMove) {
    }

    private final Entry[] slots;
    private final int mask;

    public TranspositionTable(int bits) {
        int size = 1 << Math.max(8, Math.min(22, bits));
        this.slots = new Entry[size];
        this.mask = size - 1;
    }

    public Entry probe(long key) {
        Entry entry = slots[(int) (key & mask)];
        return entry != null && entry.key() == key ? entry : null;
    }

    public void store(long key, int depth, int score, Bound bound, Move bestMove) {
        int index = (int) (key & mask);
        Entry previous = slots[index];
        // 同一局面的浅层结果不应覆盖更深的搜索结果
        if (previous != null && previous.key() == key && previous.depth() > depth) {
            return;
        }
        slots[index] = new Entry(key, depth, score, bound, bestMove);
    }

    public void clear() {
        java.util.Arrays.fill(slots, null);
    }
}
