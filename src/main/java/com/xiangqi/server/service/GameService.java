package com.xiangqi.server.service;

import com.xiangqi.core.Board;
import com.xiangqi.core.ChessGame;
import com.xiangqi.core.GameStatus;
import com.xiangqi.core.HistoryEntry;
import com.xiangqi.core.IllegalMoveException;
import com.xiangqi.core.Move;
import com.xiangqi.core.Position;
import com.xiangqi.core.Side;
import com.xiangqi.core.engine.SearchEngine;
import com.xiangqi.core.engine.SearchResult;
import com.xiangqi.core.piece.Piece;
import com.xiangqi.server.api.dto.CreateGameResponse;
import com.xiangqi.server.api.dto.GameView;
import com.xiangqi.server.api.dto.GameView.HistoryView;
import com.xiangqi.server.api.dto.GameView.MoveView;
import com.xiangqi.server.api.dto.GameView.PieceView;
import com.xiangqi.server.api.dto.GameView.PositionView;
import com.xiangqi.server.api.dto.MoveRequest;
import com.xiangqi.server.api.dto.RecordSummary;
import com.xiangqi.server.api.dto.ReplayView;
import com.xiangqi.server.api.dto.RoomSeatResponse;
import com.xiangqi.server.api.dto.RoomView;
import com.xiangqi.server.error.GameException;
import com.xiangqi.server.security.Tokens;
import com.xiangqi.server.store.GameArchiveRepository;
import com.xiangqi.server.store.StoredGame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 对局服务：
 * - 服务端持有全部权威状态，客户端只能提交“起终点”；
 * - 每个对局一把互斥锁，保证同局并发请求串行执行；
 * - 会话有容量上限与空闲回收，避免内存被无限占用；
 * - 每次状态变化写入本地 JSON 存档，并可通过 SSE 实时推送给房间成员。
 */
@Service
public class GameService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameService.class);
    private static final int MAX_ID_LENGTH = 64;
    private static final int AI_MAX_DEPTH = 20;
    /** 每层深度分配的思考时间（毫秒），并设置总上限，避免单次请求长期占用线程。 */
    private static final long AI_MS_PER_DEPTH = 500;
    private static final long AI_MAX_THINK_MS = 15_000;
    private static final long SSE_TIMEOUT_MS = 300_000L;
    private static final Pattern ROOM_CODE_PATTERN = Pattern.compile("[A-Z0-9]{4,8}");
    private static final String ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int ROOM_CODE_LENGTH = 6;

    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, GameSession> rooms = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<RoomClient>> subscribers = new ConcurrentHashMap<>();
    private final SecureRandom codeRandom = new SecureRandom();

    private final GameArchiveRepository archive;
    private final ObjectMapper mapper;
    private final int maxGames;
    private final long localIdleTtlMs;
    private final long roomIdleTtlMs;

    public GameService(
            GameArchiveRepository archive,
            ObjectMapper mapper,
            @Value("${app.game.max-games:1000}") int maxGames,
            @Value("${app.game.idle-ttl-minutes:60}") long idleTtlMinutes,
            @Value("${app.game.room-idle-ttl-minutes:720}") long roomIdleTtlMinutes) {
        this.archive = archive;
        this.mapper = mapper;
        this.maxGames = Math.max(1, maxGames);
        this.localIdleTtlMs = Math.max(1, idleTtlMinutes) * 60_000L;
        this.roomIdleTtlMs = Math.max(1, roomIdleTtlMinutes) * 60_000L;
        restoreArchives();
    }

    /* ==================== 本地对弈 ==================== */

    public CreateGameResponse create() {
        ensureCapacity();
        String id = UUID.randomUUID().toString();
        String token = Tokens.issue();
        GameSession session = GameSession.local(id, Tokens.hash(token));
        sessions.put(id, session);
        persist(session);
        return new CreateGameResponse(id, token, viewOf(id, session.game()));
    }

    public GameView view(String gameId, String token) {
        GameSession session = requireSession(gameId);
        if (!session.canRead(token)) {
            throw GameException.forbidden();
        }
        session.lock().lock();
        try {
            session.touch();
            return viewOf(gameId, session.game());
        } finally {
            session.lock().unlock();
        }
    }

    public GameView move(String gameId, String token, MoveRequest request) {
        return withLocalGame(gameId, token, game -> {
            game.play(new Move(toPosition(request.from()), toPosition(request.to())));
            return viewOf(gameId, game);
        });
    }

    public GameView undo(String gameId, String token) {
        return withLocalGame(gameId, token, game -> {
            game.undo();
            return viewOf(gameId, game);
        });
    }

    public GameView resign(String gameId, String token, Side side) {
        return withLocalGame(gameId, token, game -> {
            game.resign(side);
            return viewOf(gameId, game);
        });
    }

    /**
     * 本地对弈中的“AI 代走”：仅用于演示搜索引擎，不参与联网对战逻辑。
     */
    public GameView aiMove(String gameId, String token, int requestedDepth) {
        return withLocalGame(gameId, token, game -> {
            if (game.isFinished()) {
                throw GameException.conflict("对局已结束，无法继续走棋");
            }
            int depth = Math.max(1, Math.min(AI_MAX_DEPTH, requestedDepth));
            SearchEngine engine = new SearchEngine();
            // 搜索在副本上进行，任何搜索异常都不会影响权威对局状态
            SearchResult result = engine.search(game.board().copy(), game.turn(), depth, thinkTimeMs(depth));
            if (result.bestMove() == null) {
                throw GameException.conflict("当前局面没有合法着法");
            }
            game.play(result.bestMove());
            return viewOf(gameId, game);
        });
    }

    /**
     * 本地对局：必须持有热座令牌，操作后落盘。
     */
    private GameView withLocalGame(String gameId, String token, Function<ChessGame, GameView> action) {
        GameSession session = requireSession(gameId);
        if (session.isRoom()) {
            throw GameException.conflict("该对局为联网房间，请通过房间接口操作");
        }
        if (token == null || token.isBlank()) {
            throw GameException.unauthorized();
        }
        if (!session.allowsHotseat(token)) {
            throw GameException.forbidden();
        }

        session.lock().lock();
        try {
            session.touch();
            GameView view = action.apply(session.game());
            session.bumpVersion();
            persist(session);
            return view;
        } finally {
            session.lock().unlock();
        }
    }

    /* ==================== 联网房间 ==================== */

    public RoomSeatResponse createRoom() {
        ensureCapacity();
        String id = UUID.randomUUID().toString();
        String code = generateRoomCode();
        String token = Tokens.issue();
        GameSession session = GameSession.room(id, code, Tokens.hash(token));
        sessions.put(id, session);
        rooms.put(code, session);
        persist(session);
        return new RoomSeatResponse(code, id, token, Side.RED.name(), roomView(session));
    }

    /**
     * 加入房间：已有席位令牌则直接复用；空余黑方席位则签发新令牌；已满则以观战者身份进入。
     */
    public RoomSeatResponse joinRoom(String rawCode, String token) {
        GameSession session = requireRoom(rawCode);
        if (token != null && !token.isBlank()) {
            Side existing = session.sideForToken(token);
            if (existing != null) {
                session.touch();
                return new RoomSeatResponse(session.code(), session.id(), token, existing.name(),
                        roomView(session));
            }
        }

        String blackToken = null;
        RoomView view;
        session.lock().lock();
        try {
            if (!session.hasBlackSeat()) {
                blackToken = Tokens.issue();
                session.assignBlackToken(Tokens.hash(blackToken));
                session.bumpVersion();
                session.touch();
                persist(session);
            }
            view = roomView(session);
        } finally {
            session.lock().unlock();
        }

        if (blackToken != null) {
            broadcast(session);
            return new RoomSeatResponse(session.code(), session.id(), blackToken, Side.BLACK.name(), view);
        }
        return new RoomSeatResponse(session.code(), session.id(), null, "SPECTATOR", view);
    }

    /**
     * 观战与公共视图：无需令牌。
     */
    public RoomView room(String rawCode) {
        GameSession session = requireRoom(rawCode);
        session.touch();
        return roomView(session);
    }

    public RoomView moveRoom(String rawCode, String token, MoveRequest request) {
        GameSession session = requireRoom(rawCode);
        RoomView view;
        session.lock().lock();
        try {
            session.touch();
            Side side = session.sideForToken(token);
            if (side == null) {
                throw GameException.unauthorized();
            }
            ChessGame game = session.game();
            if (game.isFinished()) {
                throw GameException.conflict("对局已结束，无法继续走棋");
            }
            if (game.turn() != side) {
                throw GameException.conflict("还没轮到你走棋");
            }
            game.play(new Move(toPosition(request.from()), toPosition(request.to())));
            session.bumpVersion();
            persist(session);
            view = roomView(session);
        } finally {
            session.lock().unlock();
        }
        broadcast(session);
        return view;
    }

    public RoomView resignRoom(String rawCode, String token) {
        GameSession session = requireRoom(rawCode);
        RoomView view;
        session.lock().lock();
        try {
            session.touch();
            Side side = session.sideForToken(token);
            if (side == null) {
                throw GameException.unauthorized();
            }
            session.game().resign(side);
            session.bumpVersion();
            persist(session);
            view = roomView(session);
        } finally {
            session.lock().unlock();
        }
        broadcast(session);
        return view;
    }

    /**
     * SSE 订阅：立即推送当前局面，之后每次状态变化推送新版本。
     */
    public SseEmitter subscribeRoom(String rawCode) {
        GameSession session = requireRoom(rawCode);
        session.touch();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        RoomClient client = new RoomClient(emitter);
        CopyOnWriteArrayList<RoomClient> clients =
                subscribers.computeIfAbsent(session.code(), key -> new CopyOnWriteArrayList<>());
        clients.add(client);

        emitter.onCompletion(() -> removeClient(session.code(), client));
        emitter.onTimeout(() -> {
            removeClient(session.code(), client);
            emitter.complete();
        });
        emitter.onError(error -> removeClient(session.code(), client));

        // 首次推送放在注册完成之后，保证不会漏掉紧随其后的广播
        sendState(client, session);
        return emitter;
    }

    /* ==================== 历史与复盘 ==================== */

    public List<RecordSummary> records(int limit) {
        int effectiveLimit = Math.max(1, Math.min(100, limit));
        return archive.loadAll().stream()
                .filter(record -> record.moves() != null && !record.moves().isEmpty())
                .sorted(Comparator.comparingLong(StoredGame::updatedAt).reversed())
                .limit(effectiveLimit)
                .map(record -> new RecordSummary(record.id(), record.mode(), record.status(),
                        record.resultReason(), record.moves().size(), record.createdAt(),
                        record.updatedAt()))
                .toList();
    }

    /**
     * 逐步复盘：服务端重放存档棋谱，返回每一帧的完整局面。
     */
    public ReplayView replay(String gameId) {
        validateGameId(gameId);
        StoredGame record = archive.load(gameId);
        if (record == null) {
            throw GameException.notFound();
        }
        ChessGame game = ChessGame.start();
        List<ReplayView.FrameView> frames = new ArrayList<>();
        frames.add(frameOf(game, null));

        for (Move move : record.moves()) {
            try {
                game.play(move);
            } catch (IllegalMoveException exception) {
                LOGGER.warn("存档 {} 棋谱在 {} 步后不完整，停止复盘", gameId, game.plyCount());
                break;
            }
            frames.add(frameOf(game, game.history().get(game.plyCount() - 1)));
        }

        // 认输等“棋谱之外”的结束原因需要按存档补回到最后一帧
        if (record.status() != null && record.status() != GameStatus.PLAYING && !frames.isEmpty()
                && game.status() == GameStatus.PLAYING) {
            ReplayView.FrameView last = frames.remove(frames.size() - 1);
            frames.add(new ReplayView.FrameView(last.ply(), last.mover(), last.move(), last.notation(),
                    last.capturedSymbol(), last.turn(), last.inCheck(), record.status(),
                    record.resultReason(), last.lastMove(), last.pieces()));
        }

        return new ReplayView(record.id(), record.mode(),
                record.status() == null ? game.status() : record.status(),
                record.resultReason() == null ? game.resultReason() : record.resultReason(),
                record.createdAt(), record.updatedAt(), frames);
    }

    /* ==================== 公共辅助 ==================== */

    private GameSession requireSession(String gameId) {
        validateGameId(gameId);
        GameSession session = sessions.get(gameId);
        if (session == null) {
            throw GameException.notFound();
        }
        return session;
    }

    private GameSession requireRoom(String rawCode) {
        String code = normalizeCode(rawCode);
        GameSession session = rooms.get(code);
        if (session == null || !session.isRoom()) {
            throw GameException.notFound();
        }
        return session;
    }

    private String normalizeCode(String rawCode) {
        if (rawCode == null) {
            throw GameException.badRequest("房间号不合法");
        }
        String code = rawCode.trim().toUpperCase();
        if (!ROOM_CODE_PATTERN.matcher(code).matches()) {
            throw GameException.badRequest("房间号不合法");
        }
        return code;
    }

    private String generateRoomCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder builder = new StringBuilder(ROOM_CODE_LENGTH);
            for (int index = 0; index < ROOM_CODE_LENGTH; index++) {
                builder.append(ROOM_CODE_ALPHABET.charAt(codeRandom.nextInt(ROOM_CODE_ALPHABET.length())));
            }
            String code = builder.toString();
            if (!rooms.containsKey(code)) {
                return code;
            }
        }
        throw GameException.tooManyGames();
    }

    private void ensureCapacity() {
        evictIdleSessions();
        if (sessions.size() >= maxGames) {
            LOGGER.warn("对局数量达到上限 {}，拒绝新建", maxGames);
            throw GameException.tooManyGames();
        }
    }

    private void validateGameId(String gameId) {
        if (gameId == null || gameId.isBlank() || gameId.length() > MAX_ID_LENGTH) {
            throw GameException.badRequest("对局标识不合法");
        }
    }

    private long thinkTimeMs(int depth) {
        return Math.min(AI_MAX_THINK_MS, AI_MS_PER_DEPTH * depth);
    }

    private Position toPosition(MoveRequest.Square square) {
        return new Position(square.row(), square.col());
    }

    private GameView viewOf(String gameId, ChessGame game) {
        return new GameView(
                gameId,
                game.turn(),
                game.status(),
                game.resultReason(),
                game.isInCheck(),
                game.plyCount(),
                game.lastMove() == null ? null : toMoveView(game.lastMove()),
                pieceViews(game.board()),
                game.legalMoves().stream().map(this::toMoveView).toList(),
                historyViews(game.history())
        );
    }

    private RoomView roomView(GameSession session) {
        session.lock().lock();
        try {
            return new RoomView(
                    session.code(),
                    session.id(),
                    true,
                    session.hasBlackSeat(),
                    session.version(),
                    viewOf(session.id(), session.game())
            );
        } finally {
            session.lock().unlock();
        }
    }

    private List<PieceView> pieceViews(Board board) {
        List<PieceView> pieces = new ArrayList<>(32);
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.pieceAt(row, col);
                if (piece != null) {
                    pieces.add(new PieceView(row, col, piece.side(), piece.type(),
                            String.valueOf(piece.symbol())));
                }
            }
        }
        return pieces;
    }

    private List<HistoryView> historyViews(List<HistoryEntry> entries) {
        List<HistoryView> views = new ArrayList<>(entries.size());
        for (HistoryEntry entry : entries) {
            views.add(new HistoryView(entry.index(), entry.side(), toMoveView(entry.move()),
                    entry.notation(), entry.capturedSymbol()));
        }
        return views;
    }

    private MoveView toMoveView(Move move) {
        return new MoveView(new PositionView(move.from().row(), move.from().col()),
                new PositionView(move.to().row(), move.to().col()));
    }

    private ReplayView.FrameView frameOf(ChessGame game, HistoryEntry entry) {
        return new ReplayView.FrameView(
                game.plyCount(),
                entry == null ? null : entry.side(),
                entry == null ? null : toMoveView(entry.move()),
                entry == null ? null : entry.notation(),
                entry == null ? null : entry.capturedSymbol(),
                game.turn(),
                game.isInCheck(),
                game.status(),
                game.resultReason(),
                game.lastMove() == null ? null : toMoveView(game.lastMove()),
                pieceViews(game.board())
        );
    }

    /* ==================== 持久化 ==================== */

    private void persist(GameSession session) {
        List<Move> moves = session.game().history().stream().map(HistoryEntry::move).toList();
        StoredGame record = new StoredGame(
                session.id(),
                session.code(),
                session.isRoom() ? StoredGame.MODE_ROOM : StoredGame.MODE_LOCAL,
                hexOrNull(session.redTokenHash()),
                hexOrNull(session.blackTokenHash()),
                hexOrNull(session.sharedTokenHash()),
                session.createdAt(),
                session.updatedAt(),
                moves,
                session.game().status(),
                session.game().resultReason()
        );
        archive.save(record);
        // 内存中的记录无法与磁盘同步时，历史列表仍可从存档读取
    }

    private void restoreArchives() {
        for (StoredGame record : archive.loadAll()) {
            if (sessions.size() >= maxGames) {
                LOGGER.info("达到会话上限，跳过剩余存档恢复");
                break;
            }
            if (sessions.containsKey(record.id())) {
                continue;
            }
            ChessGame game = rebuild(record);
            if (game == null) {
                continue;
            }
            GameSession session = GameSession.restored(
                    record.id(),
                    record.code(),
                    record.isRoom(),
                    game,
                    Tokens.unhex(record.sharedTokenHash()),
                    Tokens.unhex(record.redTokenHash()),
                    Tokens.unhex(record.blackTokenHash()),
                    record.createdAt(),
                    record.updatedAt()
            );
            sessions.put(record.id(), session);
            if (session.isRoom() && session.code() != null) {
                rooms.putIfAbsent(session.code(), session);
            }
        }
        if (!sessions.isEmpty()) {
            LOGGER.info("已从存档恢复 {} 局，其中房间 {} 个", sessions.size(), rooms.size());
        }
    }

    private ChessGame rebuild(StoredGame record) {
        ChessGame game = ChessGame.start();
        for (Move move : record.moves()) {
            try {
                game.play(move);
            } catch (IllegalMoveException | IllegalArgumentException exception) {
                LOGGER.warn("存档 {} 棋谱损坏，跳过恢复", record.id());
                return null;
            }
        }
        // 认输不会写入棋谱，需要根据存档结果补回终局状态
        if (record.status() != null && record.status() != GameStatus.PLAYING
                && game.status() == GameStatus.PLAYING && record.resultReason() != null
                && record.resultReason().endsWith("认输")) {
            Side resigner = record.resultReason().startsWith(Side.RED.label()) ? Side.RED : Side.BLACK;
            game.resign(resigner);
        }
        return game;
    }

    private String hexOrNull(byte[] bytes) {
        return bytes == null ? null : Tokens.hex(bytes);
    }

    /* ==================== SSE 推送 ==================== */

    private void broadcast(GameSession session) {
        CopyOnWriteArrayList<RoomClient> clients = subscribers.get(session.code());
        if (clients == null || clients.isEmpty()) {
            return;
        }
        for (RoomClient client : clients) {
            sendState(client, session);
        }
    }

    private void sendState(RoomClient client, GameSession session) {
        try {
            String json = mapper.writeValueAsString(roomView(session));
            synchronized (client) {
                client.emitter().send(SseEmitter.event().name("state").data(json));
            }
        } catch (Exception exception) {
            removeClient(session.code(), client);
            try {
                client.emitter().complete();
            } catch (Exception ignored) {
                // 连接已断开
            }
        }
    }

    private void removeClient(String code, RoomClient client) {
        CopyOnWriteArrayList<RoomClient> clients = subscribers.get(code);
        if (clients == null) {
            return;
        }
        clients.remove(client);
        if (clients.isEmpty()) {
            subscribers.remove(code, clients);
        }
    }

    private void closeSubscribers(String code) {
        CopyOnWriteArrayList<RoomClient> clients = subscribers.remove(code);
        if (clients == null) {
            return;
        }
        for (RoomClient client : clients) {
            try {
                client.emitter().complete();
            } catch (Exception ignored) {
                // 连接已断开
            }
        }
    }

    /* ==================== 定时任务 ==================== */

    @Scheduled(fixedDelay = 300_000)
    public void evictIdleSessions() {
        long now = System.currentTimeMillis();
        int before = sessions.size();
        for (Map.Entry<String, GameSession> entry : sessions.entrySet()) {
            GameSession session = entry.getValue();
            long ttl = session.isRoom() ? roomIdleTtlMs : localIdleTtlMs;
            if (now - session.lastAccess() <= ttl) {
                continue;
            }
            if (sessions.remove(entry.getKey(), session)) {
                if (session.isRoom() && session.code() != null) {
                    rooms.remove(session.code(), session);
                    closeSubscribers(session.code());
                }
            }
        }
        int removed = before - sessions.size();
        if (removed > 0) {
            LOGGER.info("回收空闲对局 {} 个，当前 {} 个", removed, sessions.size());
        }
    }

    /**
     * 心跳：定期发送 SSE 注释行，避免代理层因长时间无数据而断开连接。
     */
    @Scheduled(fixedDelay = 30_000)
    public void heartbeat() {
        for (CopyOnWriteArrayList<RoomClient> clients : subscribers.values()) {
            for (RoomClient client : clients) {
                try {
                    synchronized (client) {
                        client.emitter().send(SseEmitter.event().comment("ping"));
                    }
                } catch (Exception exception) {
                    removeClientByEmitter(client);
                }
            }
        }
    }

    private void removeClientByEmitter(RoomClient client) {
        for (Map.Entry<String, CopyOnWriteArrayList<RoomClient>> entry : subscribers.entrySet()) {
            if (entry.getValue().remove(client)) {
                if (entry.getValue().isEmpty()) {
                    subscribers.remove(entry.getKey(), entry.getValue());
                }
                return;
            }
        }
    }

    private static final class RoomClient {

        private final SseEmitter emitter;

        private RoomClient(SseEmitter emitter) {
            this.emitter = emitter;
        }

        private SseEmitter emitter() {
            return emitter;
        }
    }
}
