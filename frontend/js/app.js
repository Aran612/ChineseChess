(function () {
    "use strict";

    const SESSION_KEY = "xiangqi.session.v1";
    const ROOM_STORE_KEY = "xiangqi.rooms.v1";
    const SIDE_LABEL = { RED: "红方", BLACK: "黑方" };

    const elements = {
        homeScreen: document.getElementById("homeScreen"),
        gameScreen: document.getElementById("gameScreen"),
        homeAiBtn: document.getElementById("homeAiBtn"),
        homePvpBtn: document.getElementById("homePvpBtn"),
        homeRoomBtn: document.getElementById("homeRoomBtn"),
        backHomeBtn: document.getElementById("backHomeBtn"),
        gameTitle: document.getElementById("gameTitle"),
        gameSubtitle: document.getElementById("gameSubtitle"),
        board: document.getElementById("board"),
        turnStrip: document.getElementById("turnStrip"),
        turnText: document.getElementById("turnText"),
        statusLine: document.getElementById("statusLine"),
        resultLine: document.getElementById("resultLine"),
        plyText: document.getElementById("plyText"),
        gameIdText: document.getElementById("gameIdText"),
        modeText: document.getElementById("modeText"),
        history: document.getElementById("history"),
        records: document.getElementById("records"),
        redClock: document.getElementById("redClock"),
        blackClock: document.getElementById("blackClock"),
        newGameBtn: document.getElementById("newGameBtn"),
        undoBtn: document.getElementById("undoBtn"),
        pvpBtn: document.getElementById("pvpBtn"),
        aiModeBtn: document.getElementById("aiModeBtn"),
        aiSettings: document.getElementById("aiSettings"),
        playerSide: document.getElementById("playerSide"),
        aiResignBtn: document.getElementById("aiResignBtn"),
        pvpResignGroup: document.getElementById("pvpResignGroup"),
        aiResignGroup: document.getElementById("aiResignGroup"),
        resignRedBtn: document.getElementById("resignRedBtn"),
        resignBlackBtn: document.getElementById("resignBlackBtn"),
        aiDepth: document.getElementById("aiDepth"),
        exportBtn: document.getElementById("exportBtn"),
        modeLocalBtn: document.getElementById("modeLocalBtn"),
        modeRoomBtn: document.getElementById("modeRoomBtn"),
        localControls: document.getElementById("localControls"),
        roomControls: document.getElementById("roomControls"),
        roomEntry: document.getElementById("roomEntry"),
        createRoomBtn: document.getElementById("createRoomBtn"),
        joinRoomBtn: document.getElementById("joinRoomBtn"),
        roomCodeInput: document.getElementById("roomCodeInput"),
        serverUrlInput: document.getElementById("serverUrlInput"),
        saveServerBtn: document.getElementById("saveServerBtn"),
        roomStatus: document.getElementById("roomStatus"),
        roomBadges: document.getElementById("roomBadges"),
        copyRoomBtn: document.getElementById("copyRoomBtn"),
        roomUndoBtn: document.getElementById("roomUndoBtn"),
        roomResignBtn: document.getElementById("roomResignBtn"),
        leaveRoomBtn: document.getElementById("leaveRoomBtn"),
        replayDialog: document.getElementById("replayDialog"),
        replayBoard: document.getElementById("replayBoard"),
        replayInfo: document.getElementById("replayInfo"),
        replaySlider: document.getElementById("replaySlider"),
        replayMoves: document.getElementById("replayMoves"),
        replayFirstBtn: document.getElementById("replayFirstBtn"),
        replayPrevBtn: document.getElementById("replayPrevBtn"),
        replayNextBtn: document.getElementById("replayNextBtn"),
        replayLastBtn: document.getElementById("replayLastBtn"),
        replayCloseBtn: document.getElementById("replayCloseBtn"),
        toast: document.getElementById("toast"),
        loading: document.getElementById("loading"),
        loadingText: document.getElementById("loadingText"),
        menuBtn: document.getElementById("menuBtn"),
        drawerScrim: document.getElementById("drawerScrim"),
        drawerCloseBtn: document.getElementById("drawerCloseBtn"),
        sideDrawer: document.getElementById("sideDrawer"),
        stepCounter: document.getElementById("stepCounter"),
        toolbarUndoBtn: document.getElementById("toolbarUndoBtn"),
        analyzeBtn: document.getElementById("analyzeBtn"),
        hintBtn: document.getElementById("hintBtn")
    };

    let boardView = null;
    let replayBoardView = null;
    let room = null;
    let roomSource = null;
    let roomConnected = false;
    let roomPollTimer = 0;
    let lastSseAt = 0;
    let lastStateVersion = -1;
    let undoRequest = null;
    let mode = "LOCAL";
    let localMode = "PVP";
    let playerSide = "RED";
    let aiDepth = 6;
    let state = null;
    let selected = null;
    let busy = false;
    let toastTimer = 0;
    let replayData = null;
    let replayIndex = 0;

    const clockState = { RED: 0, BLACK: 0, since: 0, active: null, gameId: null };

    function boot() {
        boardView = new window.XiangqiBoard(elements.board);
        boardView.onCell = handleCell;
        replayBoardView = new window.XiangqiBoard(elements.replayBoard);
        bindActions();
        try {
            elements.serverUrlInput.value = window.localStorage.getItem("xiangqi.server") || "";
        } catch (error) {
            // ignore
        }
        render();
        document.addEventListener("pointerdown", () => window.XiangqiSound.unlock(), { once: true });
        window.setInterval(tickClocks, 500);
        // 手机切回前台时补拉一次，避免后台期间错过的推送
        document.addEventListener("visibilitychange", () => {
            if (document.visibilityState === "visible" && mode === "ROOM" && room) {
                refreshRoomState(true);
            }
        });
        refreshRecords();

        const queryRoom = new URLSearchParams(window.location.search).get("room");
        if (queryRoom) {
            enterGame("ROOM");
            enterRoom(queryRoom);
        } else {
            showHome();
        }
    }

    function bindActions() {
        elements.homeAiBtn.addEventListener("click", () => enterGame("AI"));
        elements.homePvpBtn.addEventListener("click", () => enterGame("PVP"));
        elements.homeRoomBtn.addEventListener("click", () => enterGame("ROOM"));
        elements.backHomeBtn.addEventListener("click", showHome);
        elements.menuBtn.addEventListener("click", toggleDrawer);
        elements.drawerCloseBtn.addEventListener("click", closeDrawer);
        elements.drawerScrim.addEventListener("click", closeDrawer);
        elements.toolbarUndoBtn.addEventListener("click", () => {
            if (mode === "ROOM") {
                handleRoomUndo();
            } else {
                undoMove();
            }
        });
        elements.analyzeBtn.addEventListener("click", () => showToast("分析功能即将上线"));
        elements.hintBtn.addEventListener("click", showHint);
        elements.newGameBtn.addEventListener("click", () => createNewGame(false));
        elements.undoBtn.addEventListener("click", undoMove);
        elements.pvpBtn.addEventListener("click", () => switchLocalMode("PVP"));
        elements.aiModeBtn.addEventListener("click", () => switchLocalMode("AI"));
        elements.aiResignBtn.addEventListener("click", () => resignLocal(playerSide));
        elements.resignRedBtn.addEventListener("click", () => resignLocal("RED"));
        elements.resignBlackBtn.addEventListener("click", () => resignLocal("BLACK"));
        elements.playerSide.addEventListener("click", (e) => {
            const btn = e.target.closest(".seg-btn");
            if (!btn) return;
            elements.playerSide.querySelectorAll(".seg-btn").forEach((b) => b.classList.toggle("is-active", b === btn));
            playerSide = btn.dataset.value;
            if (localMode === "AI") {
                createNewGame(false);
            }
        });
        elements.aiDepth.addEventListener("click", (e) => {
            const btn = e.target.closest(".seg-btn");
            if (!btn) return;
            elements.aiDepth.querySelectorAll(".seg-btn").forEach((b) => b.classList.toggle("is-active", b === btn));
            aiDepth = Number(btn.dataset.value) || 6;
        });
        elements.exportBtn.addEventListener("click", exportNotation);
        elements.modeLocalBtn.addEventListener("click", () => switchMode("LOCAL"));
        elements.modeRoomBtn.addEventListener("click", () => switchMode("ROOM"));
        elements.createRoomBtn.addEventListener("click", createRoom);
        elements.joinRoomBtn.addEventListener("click", () => {
            const code = elements.roomCodeInput.value.trim().toUpperCase();
            if (!code) {
                showToast("请输入房间号");
                return;
            }
            enterRoom(code);
        });
        elements.roomCodeInput.addEventListener("keydown", (event) => {
            if (event.key === "Enter") {
                elements.joinRoomBtn.click();
            }
        });
        elements.saveServerBtn.addEventListener("click", saveServer);
        elements.copyRoomBtn.addEventListener("click", copyInviteLink);
        elements.roomUndoBtn.addEventListener("click", handleRoomUndo);
        elements.roomResignBtn.addEventListener("click", resignRoom);
        elements.leaveRoomBtn.addEventListener("click", leaveRoom);

        elements.replayCloseBtn.addEventListener("click", () => elements.replayDialog.close());
        elements.replayFirstBtn.addEventListener("click", () => jumpReplay(0));
        elements.replayPrevBtn.addEventListener("click", () => jumpReplay(replayIndex - 1));
        elements.replayNextBtn.addEventListener("click", () => jumpReplay(replayIndex + 1));
        elements.replayLastBtn.addEventListener("click", () => jumpReplay(Number(elements.replaySlider.max)));
        elements.replaySlider.addEventListener("input", () => jumpReplay(Number(elements.replaySlider.value)));
    }

    /* ==================== 模式切换 ==================== */

    function showHome() {
        closeRoomStream();
        elements.gameScreen.classList.add("hidden");
        elements.homeScreen.classList.remove("hidden");
    }

    function showGame() {
        elements.homeScreen.classList.add("hidden");
        elements.gameScreen.classList.remove("hidden");
        closeDrawer();
    }

    function toggleDrawer() {
        if (elements.sideDrawer.classList.contains("open")) {
            closeDrawer();
        } else {
            openDrawer();
        }
    }

    function openDrawer() {
        elements.sideDrawer.classList.add("open");
        elements.drawerScrim.classList.remove("hidden");
    }

    function closeDrawer() {
        elements.sideDrawer.classList.remove("open");
        elements.drawerScrim.classList.add("hidden");
    }

    function showHint() {
        if (busy || !state || state.status !== "PLAYING") {
            showToast("当前无法提示");
            return;
        }
        if (mode === "LOCAL" && localMode === "AI" && state.turn !== playerSide) {
            showToast("轮到 AI 行棋");
            return;
        }
        try {
            const next = window.XiangqiEngine.aiMove(state, 3);
            const entry = next && next.history && next.history[next.history.length - 1];
            showToast(entry && entry.notation ? "建议走法：" + entry.notation : "暂无提示");
        } catch (error) {
            showToast("暂无提示");
        }
    }

    function enterGame(target) {
        if (target === "ROOM") {
            switchMode("ROOM");
            showGame();
            return;
        }
        closeRoomStream();
        mode = "LOCAL";
        localMode = target === "AI" ? "AI" : "PVP";
        selected = null;
        updateModeUi();
        updateLocalModeUi();
        showGame();
        loadLocalGame();
    }

    function updateGameTitle() {
        if (mode === "ROOM") {
            elements.gameTitle.textContent = "远程对弈";
            elements.gameSubtitle.textContent = "房间码联机 · 实时对战";
        } else if (localMode === "AI") {
            elements.gameTitle.textContent = "人机对战";
            elements.gameSubtitle.textContent = "你执" + (playerSide === "RED" ? "红" : "黑") + " · AI 对手";
        } else {
            elements.gameTitle.textContent = "双人对弈";
            elements.gameSubtitle.textContent = "同屏热座 · 轮流落子";
        }
    }

    function switchMode(next) {
        if (busy) {
            return;
        }
        if (next === "LOCAL") {
            closeRoomStream();
            mode = "LOCAL";
            selected = null;
            updateModeUi();
            loadLocalGame();
            return;
        }
        mode = "ROOM";
        selected = null;
        updateModeUi();
        if (room) {
            refreshRoomState();
            openRoomStream();
        } else {
            state = null;
            render();
        }
    }

    function updateModeUi() {
        elements.modeLocalBtn.classList.toggle("is-active", mode === "LOCAL");
        elements.modeRoomBtn.classList.toggle("is-active", mode === "ROOM");
        elements.localControls.classList.toggle("hidden", mode !== "LOCAL");
        elements.roomControls.classList.toggle("hidden", mode !== "ROOM");
        updateLocalModeUi();
        updateRoomPanel();
        renderButtons();
    }

    function switchLocalMode(next) {
        if (busy || mode !== "LOCAL" || localMode === next) {
            return;
        }
        localMode = next;
        selected = null;
        updateLocalModeUi();
        createNewGame(true);
    }

    function updateLocalModeUi() {
        const aiMode = localMode === "AI";
        elements.pvpBtn.classList.toggle("is-active", !aiMode);
        elements.aiModeBtn.classList.toggle("is-active", aiMode);
        elements.aiSettings.classList.toggle("hidden", !aiMode);
        elements.pvpResignGroup.classList.toggle("hidden", aiMode);
        elements.aiResignGroup.classList.toggle("hidden", !aiMode);
    }

    /* ==================== 本地对弈 ==================== */

    function readLocalSession() {
        try {
            const raw = window.localStorage.getItem(SESSION_KEY);
            if (!raw) {
                return null;
            }
            const parsed = JSON.parse(raw);
            if (typeof parsed.gameId === "string" && Array.isArray(parsed.pieces)) {
                return parsed;
            }
        } catch (error) {
            return null;
        }
        return null;
    }

    function writeLocalSession() {
        try {
            window.localStorage.setItem(SESSION_KEY, JSON.stringify(state));
        } catch (error) {
            // localStorage 不可用时不影响当前对局
        }
    }

    function clearLocalSession() {
        try {
            window.localStorage.removeItem(SESSION_KEY);
        } catch (error) {
            // ignore
        }
    }

    async function restoreLocalSession() {
        const saved = readLocalSession();
        if (saved) {
            applyGameView(saved);
            return;
        }
        await createNewGame(true);
    }

    async function loadLocalGame() {
        const saved = readLocalSession();
        if (saved) {
            applyGameView(saved);
            return;
        }
        await createNewGame(true);
    }

    async function createNewGame(initial) {
        if (busy) {
            return;
        }
        if (!initial && state && state.status === "PLAYING"
            && !window.confirm("当前对局尚未结束，确定开始新的一局？")) {
            return;
        }
        setBusy(true);
        try {
            applyGameView(window.XiangqiEngine.newGame());
        } catch (error) {
            window.XiangqiSound.error();
            showToast(error.message);
        } finally {
            setBusy(false);
        }
    }

    async function playMove(from, to) {
        setBusy(true);
        try {
            if (mode === "ROOM") {
                applyRoomView(await window.XiangqiApi.roomMove(room.code, room.token, from, to));
            } else {
                applyGameView(window.XiangqiEngine.move(state, from, to));
            }
        } catch (error) {
            window.XiangqiSound.error();
            showToast(error.message);
        } finally {
            setBusy(false);
        }
    }

    async function undoMove() {
        if (busy || mode !== "LOCAL" || !state || state.history.length === 0) {
            return;
        }
        setBusy(true);
        try {
            let next = window.XiangqiEngine.undo(state);
            if (localMode === "AI") {
                // 人机模式：回退到轮到玩家为止
                while (next.status === "PLAYING" && next.turn !== playerSide && next.history.length > 0) {
                    next = window.XiangqiEngine.undo(next);
                }
            }
            applyGameView(next);
        } catch (error) {
            window.XiangqiSound.error();
            showToast(error.message);
        } finally {
            setBusy(false);
        }
    }

    async function resignLocal(side) {
        if (busy || mode !== "LOCAL" || !state || state.status !== "PLAYING") {
            return;
        }
        if (!window.confirm(SIDE_LABEL[side] + "确认认输？")) {
            return;
        }
        setBusy(true);
        try {
            applyGameView(window.XiangqiEngine.resign(state, side));
        } catch (error) {
            window.XiangqiSound.error();
            showToast(error.message);
        } finally {
            setBusy(false);
        }
    }

    async function runAiMove() {
        if (mode !== "LOCAL" || localMode !== "AI" || busy || !state || state.status !== "PLAYING") {
            return;
        }
        if (state.turn === playerSide) {
            return;
        }
        const depth = aiDepth;
        setBusy(true);
        showLoading(true, "AI 正在思考…");
        try {
            // 先让 loading 渲染出来，再执行同步搜索
            await new Promise((resolve) => window.setTimeout(resolve, 30));
            applyGameView(window.XiangqiEngine.aiMove(state, depth));
        } catch (error) {
            window.XiangqiSound.error();
            showToast(error.message);
        } finally {
            showLoading(false);
            setBusy(false);
        }
    }

    /* ==================== 联网房间 ==================== */

    function readRoomStore() {
        try {
            return JSON.parse(window.localStorage.getItem(ROOM_STORE_KEY) || "{}") || {};
        } catch (error) {
            return {};
        }
    }

    function saveRoomEntry(entry) {
        try {
            const store = readRoomStore();
            store[entry.code] = { code: entry.code, token: entry.token, role: entry.role };
            window.localStorage.setItem(ROOM_STORE_KEY, JSON.stringify(store));
        } catch (error) {
            // ignore
        }
    }

    function removeRoomEntry(code) {
        try {
            const store = readRoomStore();
            delete store[code];
            window.localStorage.setItem(ROOM_STORE_KEY, JSON.stringify(store));
        } catch (error) {
            // ignore
        }
    }

    function saveServer() {
        const value = elements.serverUrlInput.value.trim().replace(/\/+$/, "");
        try {
            if (value) {
                window.localStorage.setItem("xiangqi.server", value);
            } else {
                window.localStorage.removeItem("xiangqi.server");
            }
        } catch (error) {
            // ignore
        }
        showToast(value ? "服务器地址已保存：" + value : "已恢复为本机服务器");
    }

    async function createRoom() {
        if (busy || mode !== "ROOM") {
            return;
        }
        setBusy(true);
        showLoading(true, "正在创建房间…");
        try {
            const data = await window.XiangqiApi.createRoom();
            room = { code: data.code, token: data.token, role: data.role };
            saveRoomEntry(room);
            lastStateVersion = -1;
            applyRoomView(data.room);
            openRoomStream();
            updateUrlRoom(data.code);
            elements.roomCodeInput.value = data.code;
            showToast("房间已创建，房间号 " + data.code);
        } catch (error) {
            window.XiangqiSound.error();
            showToast(error.message);
        } finally {
            showLoading(false);
            setBusy(false);
        }
    }

    async function enterRoom(code) {
        if (busy) {
            return;
        }
        setBusy(true);
        showLoading(true, "正在进入房间…");
        try {
            const stored = readRoomStore()[code] || {};
            const data = await window.XiangqiApi.joinRoom(code, stored.token);
            room = { code: data.code || code, token: data.token || null, role: data.role };
            if (data.token) {
                saveRoomEntry(room);
            }
            lastStateVersion = -1;
            roomConnected = false;
            applyRoomView(data.room);
            openRoomStream();
            updateUrlRoom(room.code);
            elements.roomCodeInput.value = room.code;
        } catch (error) {
            window.XiangqiSound.error();
            showToast(error.message);
            room = null;
        } finally {
            showLoading(false);
            setBusy(false);
        }
    }

    async function refreshRoomState(silent) {
        if (!room) {
            return;
        }
        try {
            const view = await window.XiangqiApi.getRoom(room.code);
            applyRoomView(view);
        } catch (error) {
            if (!silent) {
                showToast(error.message);
            }
        }
    }

    function openRoomStream() {
        closeRoomStream();
        if (!room) {
            return;
        }
        const source = new EventSource(window.XiangqiApi.roomEventsUrl(room.code));
        roomSource = source;
        source.onopen = () => {
            roomConnected = true;
            updateRoomPanel();
        };
        source.onerror = () => {
            roomConnected = false;
            updateRoomPanel();
        };
        source.addEventListener("state", (event) => {
            let payload = null;
            try {
                payload = JSON.parse(event.data);
            } catch (error) {
                return;
            }
            lastSseAt = Date.now();
            if (mode === "ROOM" && room && payload.code === room.code) {
                applyRoomView(payload);
            }
        });
        // 部分网络/代理会缓冲 SSE（表现为连接成功但收不到事件），
        // 因此串行轮询兜底：收不到事件时快轮询，SSE 正常时只做低频校准
        lastSseAt = 0;
        roomPollTimer = window.setTimeout(pollRoomLoop, 1200);
    }

    async function pollRoomLoop() {
        roomPollTimer = 0;
        if (!room || mode !== "ROOM") {
            return;
        }
        await refreshRoomState(true);
        if (!room || mode !== "ROOM") {
            return;
        }
        const quiet = Date.now() - lastSseAt > 8000;
        roomPollTimer = window.setTimeout(pollRoomLoop, quiet ? 1200 : 4000);
    }

    function closeRoomStream() {
        if (roomSource) {
            roomSource.close();
            roomSource = null;
        }
        if (roomPollTimer) {
            window.clearTimeout(roomPollTimer);
            roomPollTimer = 0;
        }
        roomConnected = false;
        lastSseAt = 0;
    }

    function updateUrlRoom(code) {
        try {
            const url = new URL(window.location.href);
            if (code) {
                url.searchParams.set("room", code);
            } else {
                url.searchParams.delete("room");
            }
            window.history.replaceState(null, "", url.toString());
        } catch (error) {
            // ignore
        }
    }

    async function resignRoom() {
        if (busy || !room || !room.token || !state || state.status !== "PLAYING") {
            return;
        }
        if (!window.confirm("确认认输？")) {
            return;
        }
        setBusy(true);
        try {
            applyRoomView(await window.XiangqiApi.roomResign(room.code, room.token));
        } catch (error) {
            window.XiangqiSound.error();
            showToast(error.message);
        } finally {
            setBusy(false);
        }
    }

    async function handleRoomUndo() {
        if (busy || !room || !room.token || !state || state.status !== "PLAYING") {
            return;
        }
        // 对方已发起悔棋请求：本按钮变成“同意悔棋”，确认后同意，取消则拒绝
        if (undoRequest && undoRequest !== room.role) {
            if (!window.confirm("对方请求悔棋，是否同意？")) {
                setBusy(true);
                try {
                    applyRoomView(await window.XiangqiApi.roomRejectUndo(room.code, room.token));
                } catch (error) {
                    window.XiangqiSound.error();
                    showToast(error.message);
                } finally {
                    setBusy(false);
                }
                return;
            }
            setBusy(true);
            try {
                applyRoomView(await window.XiangqiApi.roomAcceptUndo(room.code, room.token));
            } catch (error) {
                window.XiangqiSound.error();
                showToast(error.message);
            } finally {
                setBusy(false);
            }
            return;
        }
        // 无请求：发起悔棋
        if (!window.confirm("请求悔棋？需对方同意后生效")) {
            return;
        }
        setBusy(true);
        try {
            applyRoomView(await window.XiangqiApi.roomRequestUndo(room.code, room.token));
        } catch (error) {
            window.XiangqiSound.error();
            showToast(error.message);
        } finally {
            setBusy(false);
        }
    }

    function leaveRoom() {
        if (!room) {
            return;
        }
        if (!window.confirm("离开当前房间？")) {
            return;
        }
        const code = room.code;
        closeRoomStream();
        room = null;
        undoRequest = null;
        lastStateVersion = -1;
        removeRoomEntry(code);
        updateUrlRoom(null);
        elements.roomCodeInput.value = "";
        switchMode("LOCAL");
        showToast("已离开房间");
    }

    function copyInviteLink() {
        if (!room) {
            return;
        }
        const url = new URL(window.location.href);
        url.searchParams.set("room", room.code);
        const link = url.toString();
        // 手机通过局域网 http 访问时没有 clipboard API，退回选中复制
        if (window.isSecureContext && navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(link)
                .then(() => showToast("邀请链接已复制"))
                .catch(() => copyBySelection(link));
        } else {
            copyBySelection(link);
        }
    }

    function copyBySelection(text) {
        const area = document.createElement("textarea");
        area.value = text;
        area.setAttribute("readonly", "");
        area.style.position = "fixed";
        area.style.left = "-9999px";
        area.style.opacity = "0";
        document.body.appendChild(area);
        area.select();
        area.setSelectionRange(0, text.length);
        let copied = false;
        try {
            copied = document.execCommand("copy");
        } catch (error) {
            copied = false;
        }
        document.body.removeChild(area);
        showToast(copied ? "邀请链接已复制" : "邀请链接：" + text);
    }

    /* ==================== 状态应用与渲染 ==================== */

    function applyGameView(view) {
        if (!view) {
            return;
        }
        const previous = state;
        state = view;
        selected = null;
        handleEffects(previous, view);
        updateClock(view);
        render();
        if (mode === "LOCAL") {
            writeLocalSession();
            if (localMode === "AI" && state.status === "PLAYING" && state.turn !== playerSide) {
                window.setTimeout(runAiMove, 250);
            }
        }
    }

    function applyRoomView(view) {
        if (!view || !view.game) {
            return;
        }
        if (room && view.code && view.code !== room.code) {
            return;
        }
        if (!room) {
            room = { code: view.code, token: null, role: "SPECTATOR" };
        }
        if (typeof view.version === "number") {
            if (view.version < lastStateVersion) {
                return;
            }
            lastStateVersion = view.version;
        }
        updateRoomBadges(view);
        undoRequest = view.undoRequest || null;
        applyGameView(view.game);
        updateRoomPanel();
        if (typeof view.version === "number") {
            lastStateVersion = Math.max(lastStateVersion, view.version);
        }
    }

    function handleEffects(previous, next) {
        if (!previous || !next || previous.gameId !== next.gameId) {
            return;
        }
        if (next.ply > previous.ply) {
            const lastEntry = next.history[next.history.length - 1];
            if (lastEntry && lastEntry.capturedSymbol) {
                window.XiangqiSound.capture();
            } else {
                window.XiangqiSound.move();
            }
        }
        if (next.status !== "PLAYING" && previous.status === "PLAYING") {
            window.setTimeout(() => {
                if (next.status === "DRAW") {
                    window.XiangqiSound.draw();
                } else {
                    window.XiangqiSound.win();
                }
            }, 320);
            refreshRecords();
        }
        if (next.status === "PLAYING" && next.inCheck && !previous.inCheck) {
            window.XiangqiSound.check();
        }
    }

    function handleCell(position) {
        if (busy || !state || state.status !== "PLAYING") {
            return;
        }
        if (mode === "ROOM") {
            if (!room || !room.token) {
                return;
            }
            if (room.role !== state.turn) {
                const own = state.pieces.find((piece) =>
                    piece.row === position.row && piece.col === position.col
                    && piece.side === room.role);
                if (own) {
                    showToast("还没轮到你走棋");
                }
                return;
            }
        }
        if (mode === "LOCAL" && localMode === "AI" && state.turn !== playerSide) {
            showToast("轮到 AI 行棋");
            return;
        }

        if (selected && isLegalTarget(position)) {
            window.XiangqiSound.select();
            playMove(selected, position);
            return;
        }

        const piece = pieceAt(position);
        if (piece && piece.side === state.turn) {
            selected = position;
            window.XiangqiSound.select();
            boardView.render(state, selected);
            return;
        }
        if (selected) {
            selected = null;
            boardView.render(state, null);
        }
    }

    function isLegalTarget(position) {
        if (!selected) {
            return false;
        }
        return state.legalMoves.some((move) =>
            move.from.row === selected.row && move.from.col === selected.col
            && move.to.row === position.row && move.to.col === position.col);
    }

    function pieceAt(position) {
        return state.pieces.find((piece) =>
            piece.row === position.row && piece.col === position.col);
    }

    function render() {
        boardView.render(state, selected);
        renderTurnStrip();
        renderPanels();
        updateGameTitle();
        renderHistory();
        renderButtons();
        updateModeUi();
    }

    function renderTurnStrip() {
        elements.turnStrip.classList.remove("red", "black", "is-check");
        if (!state) {
            elements.turnText.textContent = mode === "ROOM" ? "等待进入房间" : "正在连接…";
            return;
        }
        elements.turnStrip.classList.add(state.turn === "RED" ? "red" : "black");
        if (state.status === "PLAYING" && state.inCheck) {
            elements.turnStrip.classList.add("is-check");
        }
        elements.turnText.textContent = state.status === "PLAYING"
            ? SIDE_LABEL[state.turn] + "行棋"
            : "对局结束";
    }

    function renderPanels() {
        if (!state) {
            elements.statusLine.textContent = mode === "ROOM" ? "请创建或加入房间" : "正在连接服务器…";
            elements.statusLine.className = "status-line";
            elements.resultLine.textContent = "";
        } else if (state.status === "PLAYING") {
            elements.statusLine.className = "status-line "
                + (state.turn === "RED" ? "is-red" : "is-black");
            elements.statusLine.textContent = state.inCheck
                ? SIDE_LABEL[state.turn] + "被将军"
                : SIDE_LABEL[state.turn] + "行棋";
            elements.resultLine.textContent = roomHint();
        } else {
            elements.statusLine.className = "status-line is-over";
            if (state.status === "RED_WIN") {
                elements.statusLine.textContent = "红方胜";
            } else if (state.status === "BLACK_WIN") {
                elements.statusLine.textContent = "黑方胜";
            } else {
                elements.statusLine.textContent = "和棋";
            }
            elements.resultLine.textContent = state.resultReason || "";
        }
        elements.plyText.textContent = state ? String(state.ply) : "0";
        elements.gameIdText.textContent = state ? shortId(state.gameId) : "—";
        elements.stepCounter.textContent = (state ? state.ply : 0) + "步";
        if (mode === "ROOM") {
            elements.modeText.textContent = room ? "联网 · " + roleLabel(room.role) : "联网待加入";
        } else if (localMode === "AI") {
            elements.modeText.textContent = "人机 · 你执" + (playerSide === "RED" ? "红" : "黑");
        } else {
            elements.modeText.textContent = "双人对弈";
        }
    }

    function roomHint() {
        if (mode !== "ROOM" || !room) {
            return "";
        }
        if (room.role === "SPECTATOR") {
            return "观战中";
        }
        return room.role === state.turn ? "轮到你走棋" : "等待对方走棋";
    }

    function renderHistory() {
        elements.history.textContent = "";
        if (!state || state.history.length === 0) {
            const empty = document.createElement("div");
            empty.className = "history-empty";
            empty.textContent = "尚未落子";
            elements.history.appendChild(empty);
            return;
        }

        const rounds = [];
        state.history.forEach((entry) => {
            const round = Math.floor((entry.index - 1) / 2);
            if (!rounds[round]) {
                rounds[round] = { RED: "", BLACK: "" };
            }
            rounds[round][entry.side] = entry.notation;
        });

        rounds.forEach((round, index) => {
            const row = document.createElement("div");
            row.className = "history-row";

            const number = document.createElement("span");
            number.className = "history-no";
            number.textContent = (index + 1) + ".";

            const red = document.createElement("span");
            red.className = "history-red";
            red.textContent = round.RED || "";

            const black = document.createElement("span");
            black.className = "history-black";
            black.textContent = round.BLACK || "";

            row.appendChild(number);
            row.appendChild(red);
            row.appendChild(black);
            elements.history.appendChild(row);
        });

        elements.history.scrollTop = elements.history.scrollHeight;
    }

    function renderButtons() {
        const playing = Boolean(state) && state.status === "PLAYING";
        const local = mode === "LOCAL";
        const pvp = local && localMode === "PVP";
        const aiMode = local && localMode === "AI";
        elements.newGameBtn.disabled = busy || !local;
        elements.undoBtn.disabled = busy || !local || !state || state.history.length === 0;
        elements.resignRedBtn.disabled = busy || !pvp || !playing;
        elements.resignBlackBtn.disabled = busy || !pvp || !playing;
        elements.aiResignBtn.disabled = busy || !aiMode || !playing;
        elements.exportBtn.disabled = !state || state.history.length === 0;
        elements.createRoomBtn.disabled = busy || mode !== "ROOM" || Boolean(room);
        elements.joinRoomBtn.disabled = busy || mode !== "ROOM";
        elements.copyRoomBtn.disabled = !room || busy;
        elements.leaveRoomBtn.disabled = !room || busy;
        elements.roomResignBtn.disabled = busy || !room || !room.token || !playing;
        elements.roomUndoBtn.disabled = busy || !room || !room.token || !playing
            || (Boolean(undoRequest) && undoRequest === room.role);
        elements.toolbarUndoBtn.disabled = busy || !state
            || (mode === "LOCAL" ? state.history.length === 0
                : (!room || !room.token || !playing));
    }

    function updateRoomPanel() {
        const inRoom = Boolean(room);
        if (elements.roomEntry) {
            // 已在房间内时收起创建/加入入口，把空间让给棋盘（手机端尤其明显）
            elements.roomEntry.classList.toggle("hidden", inRoom);
        }
        if (!inRoom) {
            elements.roomStatus.textContent = "尚未进入房间";
        } else {
            elements.roomStatus.textContent = "房间号 " + room.code + " · " + roleLabel(room.role)
                + (roomConnected ? " · 已连接" : " · 连接中…");
        }
        if (inRoom && mode === "ROOM") {
            elements.roomCodeInput.value = room.code;
        }
        if (elements.roomUndoBtn) {
            if (undoRequest && undoRequest === room.role) {
                elements.roomUndoBtn.textContent = "已请求悔棋";
            } else if (undoRequest && undoRequest !== room.role && room.role !== "SPECTATOR") {
                elements.roomUndoBtn.textContent = "同意悔棋";
            } else {
                elements.roomUndoBtn.textContent = "悔棋";
            }
        }
    }

    function updateRoomBadges(view) {
        elements.roomBadges.textContent = "";
        if (!view) {
            return;
        }
        const badges = [
            { text: "红方已就位", on: view.redJoined },
            { text: view.blackJoined ? "黑方已就位" : "等待黑方加入", on: view.blackJoined }
        ];
        badges.forEach((badge) => {
            const span = document.createElement("span");
            span.className = "room-badge" + (badge.on ? " is-on" : "");
            span.textContent = badge.text;
            elements.roomBadges.appendChild(span);
        });
    }

    function roleLabel(role) {
        if (role === "RED") {
            return "执红（先手）";
        }
        if (role === "BLACK") {
            return "执黑（后手）";
        }
        return "观战中";
    }

    function setBusy(value) {
        busy = value;
        renderButtons();
        updateRoomPanel();
    }

    function shortId(gameId) {
        if (!gameId) {
            return "—";
        }
        return gameId.slice(0, 8) + "…";
    }

    /* ==================== 计时 ==================== */

    function updateClock(next) {
        const now = performance.now();
        if (clockState.active) {
            clockState[clockState.active] += now - clockState.since;
        }
        clockState.since = now;
        if (!next) {
            clockState.active = null;
            return;
        }
        if (clockState.gameId !== next.gameId) {
            clockState.gameId = next.gameId;
            clockState.RED = 0;
            clockState.BLACK = 0;
        }
        clockState.active = next.status === "PLAYING" ? next.turn : null;
        renderClocks();
    }

    function tickClocks() {
        if (clockState.active) {
            renderClocks();
        }
    }

    function renderClocks() {
        let red = clockState.RED;
        let black = clockState.BLACK;
        if (clockState.active) {
            const delta = performance.now() - clockState.since;
            if (clockState.active === "RED") {
                red += delta;
            } else {
                black += delta;
            }
        }
        elements.redClock.textContent = formatTime(red);
        elements.blackClock.textContent = formatTime(black);
    }

    function formatTime(milliseconds) {
        const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
        const minutes = Math.floor(totalSeconds / 60);
        const seconds = totalSeconds % 60;
        return (minutes < 10 ? "0" : "") + minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    /* ==================== 棋谱导出 ==================== */

    function exportNotation() {
        if (!state || state.history.length === 0) {
            return;
        }
        const rounds = [];
        state.history.forEach((entry) => {
            const round = Math.floor((entry.index - 1) / 2);
            if (!rounds[round]) {
                rounds[round] = { RED: "", BLACK: "" };
            }
            rounds[round][entry.side] = entry.notation;
        });
        const header = [
            "中国象棋棋谱",
            "对局号：" + state.gameId,
            "模式：" + (mode === "ROOM" ? "联网对战" : "本地对弈"),
            "结果：" + resultText(state),
            "步数：" + state.history.length,
            ""
        ];
        const body = rounds.map((round, index) =>
            (index + 1) + ". " + (round.RED || "") + (round.BLACK ? "  " + round.BLACK : ""));
        const content = header.concat(body).join("\r\n");
        const blob = new Blob([content], { type: "text/plain;charset=utf-8" });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = "xiangqi-" + String(state.gameId).slice(0, 8) + ".txt";
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 600);
        showToast("棋谱已导出");
    }

    function resultText(gameView) {
        if (gameView.status === "RED_WIN") {
            return "红方胜" + (gameView.resultReason ? "（" + gameView.resultReason + "）" : "");
        }
        if (gameView.status === "BLACK_WIN") {
            return "黑方胜" + (gameView.resultReason ? "（" + gameView.resultReason + "）" : "");
        }
        if (gameView.status === "DRAW") {
            return "和棋" + (gameView.resultReason ? "（" + gameView.resultReason + "）" : "");
        }
        return "进行中";
    }

    /* ==================== 历史对局与复盘 ==================== */

    async function refreshRecords() {
        try {
            const records = await window.XiangqiApi.records(12);
            renderRecords(records);
        } catch (error) {
            elements.records.textContent = "";
            const empty = document.createElement("div");
            empty.className = "history-empty";
            empty.textContent = "暂无历史对局";
            elements.records.appendChild(empty);
        }
    }

    function renderRecords(records) {
        elements.records.textContent = "";
        if (!records || records.length === 0) {
            const empty = document.createElement("div");
            empty.className = "history-empty";
            empty.textContent = "暂无历史对局";
            elements.records.appendChild(empty);
            return;
        }
        records.forEach((record) => {
            const item = document.createElement("button");
            item.type = "button";
            item.className = "record-item";

            const title = document.createElement("span");
            title.className = "record-title";
            title.textContent = (record.mode === "ROOM" ? "联网 · " : "本地 · ") + statusLabel(record.status);

            const meta = document.createElement("span");
            meta.className = "record-meta";
            meta.textContent = record.ply + " 步 · " + formatDate(record.updatedAt)
                + (record.resultReason ? " · " + record.resultReason : "");

            item.appendChild(title);
            item.appendChild(meta);
            item.addEventListener("click", () => openReplay(record.gameId));
            elements.records.appendChild(item);
        });
    }

    async function openReplay(gameId) {
        showLoading(true, "正在载入复盘…");
        try {
            const data = await window.XiangqiApi.replay(gameId);
            replayData = data;
            replayIndex = 0;
            const maxIndex = Math.max(0, data.frames.length - 1);
            elements.replaySlider.max = String(maxIndex);
            if (elements.replayDialog.open) {
                elements.replayDialog.close();
            }
            renderReplay();
            elements.replayDialog.showModal();
        } catch (error) {
            window.XiangqiSound.error();
            showToast(error.message);
        } finally {
            showLoading(false);
        }
    }

    function jumpReplay(index) {
        if (!replayData) {
            return;
        }
        const maxIndex = replayData.frames.length - 1;
        replayIndex = Math.max(0, Math.min(maxIndex, index));
        renderReplay();
    }

    function renderReplay() {
        if (!replayData || replayData.frames.length === 0) {
            return;
        }
        const frame = replayData.frames[replayIndex];
        elements.replaySlider.value = String(replayIndex);

        replayBoardView.render({
            gameId: replayData.gameId,
            ply: frame.ply,
            pieces: frame.pieces,
            legalMoves: [],
            lastMove: frame.lastMove,
            inCheck: frame.inCheck,
            turn: frame.turn
        }, null);

        const moverLabel = frame.mover === "RED" ? "红方" : frame.mover === "BLACK" ? "黑方" : "开局";
        const notation = frame.notation || "初始局面";
        const statusText = statusLabel(frame.status)
            + (frame.resultReason ? "（" + frame.resultReason + "）" : "");
        elements.replayInfo.textContent = "第 " + frame.ply + " 步 · " + moverLabel + " " + notation
            + " · " + statusText + " · " + SIDE_LABEL[frame.turn] + "行棋";

        elements.replayFirstBtn.disabled = replayIndex === 0;
        elements.replayPrevBtn.disabled = replayIndex === 0;
        elements.replayNextBtn.disabled = replayIndex >= replayData.frames.length - 1;
        elements.replayLastBtn.disabled = replayIndex >= replayData.frames.length - 1;
    }

    function statusLabel(status) {
        if (status === "RED_WIN") {
            return "红方胜";
        }
        if (status === "BLACK_WIN") {
            return "黑方胜";
        }
        if (status === "DRAW") {
            return "和棋";
        }
        return "进行中";
    }

    function formatDate(epochMillis) {
        if (!epochMillis) {
            return "";
        }
        const date = new Date(epochMillis);
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");
        const hours = String(date.getHours()).padStart(2, "0");
        const minutes = String(date.getMinutes()).padStart(2, "0");
        return date.getFullYear() + "-" + month + "-" + day + " " + hours + ":" + minutes;
    }

    /* ==================== 提示与加载 ==================== */

    function showToast(message) {
        elements.toast.textContent = message;
        elements.toast.classList.add("show");
        window.clearTimeout(toastTimer);
        toastTimer = window.setTimeout(() => elements.toast.classList.remove("show"), 3200);
    }

    function showLoading(visible, text) {
        elements.loading.classList.toggle("hidden", !visible);
        if (text) {
            elements.loadingText.textContent = text;
        }
    }

    boot();
})();
