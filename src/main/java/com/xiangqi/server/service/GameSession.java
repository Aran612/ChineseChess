package com.xiangqi.server.service;

import com.xiangqi.core.ChessGame;
import com.xiangqi.core.Side;
import com.xiangqi.server.security.Tokens;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 对局会话：棋局 + 令牌摘要 + 单局互斥锁 + 版本号。
 * <p>
 * 本地对弈使用一枚“热座令牌”（可操作双方）；联网房间为红/黑双方各签发一枚令牌。
 */
final class GameSession {

    private final String id;
    private final String code;
    private final boolean room;
    private final ChessGame game;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile byte[] sharedTokenHash;
    private volatile byte[] redTokenHash;
    private volatile byte[] blackTokenHash;

    /** 当前待处理的悔棋请求发起方（RED/BLACK），无请求时为 null。 */
    private volatile Side undoRequester;

    private final long createdAt;
    private volatile long updatedAt;
    private volatile long lastAccess;
    private volatile long version;

    private GameSession(String id, String code, boolean room, ChessGame game,
                        byte[] sharedTokenHash, byte[] redTokenHash, byte[] blackTokenHash,
                        long createdAt, long updatedAt) {
        this.id = id;
        this.code = code;
        this.room = room;
        this.game = game;
        this.sharedTokenHash = sharedTokenHash == null ? null : sharedTokenHash.clone();
        this.redTokenHash = redTokenHash == null ? null : redTokenHash.clone();
        this.blackTokenHash = blackTokenHash == null ? null : blackTokenHash.clone();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastAccess = System.currentTimeMillis();
    }

    static GameSession local(String id, byte[] tokenHash) {
        long now = System.currentTimeMillis();
        return new GameSession(id, null, false, ChessGame.start(),
                tokenHash, null, null, now, now);
    }

    static GameSession room(String id, String code, byte[] redTokenHash) {
        long now = System.currentTimeMillis();
        return new GameSession(id, code, true, ChessGame.start(),
                null, redTokenHash, null, now, now);
    }

    static GameSession restored(String id, String code, boolean room, ChessGame game,
                                byte[] sharedTokenHash, byte[] redTokenHash, byte[] blackTokenHash,
                                long createdAt, long updatedAt) {
        return new GameSession(id, code, room, game, sharedTokenHash, redTokenHash, blackTokenHash,
                createdAt, updatedAt);
    }

    String id() {
        return id;
    }

    String code() {
        return code;
    }

    boolean isRoom() {
        return room;
    }

    ChessGame game() {
        return game;
    }

    ReentrantLock lock() {
        return lock;
    }

    void touch() {
        lastAccess = System.currentTimeMillis();
    }

    long lastAccess() {
        return lastAccess;
    }

    long createdAt() {
        return createdAt;
    }

    long updatedAt() {
        return updatedAt;
    }

    long version() {
        return version;
    }

    /**
     * 状态或席位发生变化后递增版本号，SSE 客户端据此忽略过期事件。
     */
    long bumpVersion() {
        updatedAt = System.currentTimeMillis();
        return ++version;
    }

    /**
     * 本地热座令牌：允许操作双方。
     */
    boolean allowsHotseat(String token) {
        return sharedTokenHash != null && Tokens.matches(token, sharedTokenHash);
    }

    /**
     * 联网房间：根据令牌解析席位；无效令牌返回 null。
     */
    Side sideForToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (redTokenHash != null && Tokens.matches(token, redTokenHash)) {
            return Side.RED;
        }
        if (blackTokenHash != null && Tokens.matches(token, blackTokenHash)) {
            return Side.BLACK;
        }
        return null;
    }

    boolean canRead(String token) {
        return allowsHotseat(token) || sideForToken(token) != null;
    }

    boolean hasBlackSeat() {
        return blackTokenHash != null;
    }

    void assignBlackToken(byte[] hash) {
        if (blackTokenHash == null) {
            blackTokenHash = hash.clone();
        }
    }

    byte[] sharedTokenHash() {
        return sharedTokenHash;
    }

    byte[] redTokenHash() {
        return redTokenHash;
    }

    byte[] blackTokenHash() {
        return blackTokenHash;
    }

    Side undoRequester() {
        return undoRequester;
    }

    void setUndoRequester(Side requester) {
        undoRequester = requester;
    }
}
