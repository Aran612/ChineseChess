/**
 * 纯净中国象棋 · 前端引擎
 * 移植自后端 Java 引擎（Board/Rules/Evaluator/SearchEngine/Notation），
 * 供离线单机模式使用：本地双人对弈、人机对战、悔棋、认输、记谱。
 * 对外暴露 window.XiangqiEngine。
 */
(function () {
    "use strict";

    const ROWS = 10;
    const COLS = 9;
    const RED = "RED";
    const BLACK = "BLACK";

    const TYPES = {
        GENERAL: "GENERAL",
        ADVISOR: "ADVISOR",
        ELEPHANT: "ELEPHANT",
        HORSE: "HORSE",
        CHARIOT: "CHARIOT",
        CANNON: "CANNON",
        SOLDIER: "SOLDIER"
    };

    const SYMBOLS = {
        RED: {
            GENERAL: "帥", ADVISOR: "仕", ELEPHANT: "相", HORSE: "馬",
            CHARIOT: "車", CANNON: "炮", SOLDIER: "兵"
        },
        BLACK: {
            GENERAL: "將", ADVISOR: "士", ELEPHANT: "象", HORSE: "馬",
            CHARIOT: "車", CANNON: "砲", SOLDIER: "卒"
        }
    };

    const MATERIAL = {
        GENERAL: 100000, ADVISOR: 200, ELEPHANT: 200, HORSE: 400,
        CHARIOT: 900, CANNON: 450, SOLDIER: 100
    };

    // 兵/卒位置分（红方视角，row 0 为黑方底线）
    const SOLDIER_PST = [
        [20, 20, 30, 40, 40, 40, 30, 20, 20],
        [20, 30, 40, 50, 50, 50, 40, 30, 20],
        [20, 30, 40, 50, 50, 50, 40, 30, 20],
        [20, 25, 35, 45, 45, 45, 35, 25, 20],
        [15, 20, 30, 40, 40, 40, 30, 20, 15],
        [0, 0, 0, 0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0, 0, 0, 0]
    ];
    const CHARIOT_PST = [
        [10, 12, 14, 16, 18, 16, 14, 12, 10],
        [12, 14, 18, 22, 24, 22, 18, 14, 12],
        [12, 16, 20, 26, 28, 26, 20, 16, 12],
        [12, 16, 22, 28, 30, 28, 22, 16, 12],
        [12, 16, 22, 28, 30, 28, 22, 16, 12],
        [12, 16, 20, 26, 28, 26, 20, 16, 12],
        [10, 14, 18, 24, 26, 24, 18, 14, 10],
        [8, 12, 16, 20, 22, 20, 16, 12, 8],
        [6, 10, 14, 16, 18, 16, 14, 10, 6],
        [4, 8, 10, 12, 14, 12, 10, 8, 4]
    ];
    const HORSE_PST = [
        [0, 2, 4, 6, 8, 6, 4, 2, 0],
        [2, 6, 10, 14, 16, 14, 10, 6, 2],
        [4, 10, 16, 20, 24, 20, 16, 10, 4],
        [6, 12, 18, 24, 28, 24, 18, 12, 6],
        [6, 12, 18, 24, 28, 24, 18, 12, 6],
        [6, 12, 18, 24, 28, 24, 18, 12, 6],
        [5, 10, 15, 20, 22, 20, 15, 10, 5],
        [4, 8, 12, 16, 18, 16, 12, 8, 4],
        [2, 4, 8, 10, 12, 10, 8, 4, 2],
        [0, 2, 4, 6, 8, 6, 4, 2, 0]
    ];
    const CANNON_PST = [
        [0, 2, 4, 6, 8, 6, 4, 2, 0],
        [2, 4, 8, 12, 14, 12, 8, 4, 2],
        [4, 8, 12, 18, 20, 18, 12, 8, 4],
        [4, 8, 14, 20, 22, 20, 14, 8, 4],
        [4, 8, 14, 20, 22, 20, 14, 8, 4],
        [4, 8, 12, 18, 20, 18, 12, 8, 4],
        [2, 6, 10, 14, 16, 14, 10, 6, 2],
        [2, 4, 8, 12, 14, 12, 8, 4, 2],
        [0, 2, 4, 6, 8, 6, 4, 2, 0],
        [0, 0, 0, 0, 0, 0, 0, 0, 0]
    ];
    const EMPTY_PST = [[0, 0, 0, 0, 0, 0, 0, 0, 0]];

    const MATE_SCORE = 500000;
    const INF = 1000000;

    /* ---------------- Zobrist 哈希（用于重复局面检测） ---------------- */

    const TYPE_INDEX = { GENERAL: 0, ADVISOR: 1, ELEPHANT: 2, HORSE: 3, CHARIOT: 4, CANNON: 5, SOLDIER: 6 };

    let zobristTable = [];
    let zobristTurnHash = 0;
    let currentHash = 0;
    let repetitionCount = new Map();

    function initZobrist() {
        if (zobristTable.length > 0) { return; }
        zobristTable = new Array(2 * 7 * ROWS * COLS);
        let seed = 0x2f6e2b1;
        const rnd = function () {
            seed ^= seed << 13;
            seed ^= seed >>> 17;
            seed ^= seed << 5;
            return seed >>> 0;
        };
        for (let i = 0; i < zobristTable.length; i++) {
            zobristTable[i] = rnd();
        }
        zobristTurnHash = rnd();
    }

    function zobristKey(side, type, row, col) {
        const s = side === RED ? 0 : 1;
        const t = TYPE_INDEX[type];
        return ((s * 7 + t) * ROWS + row) * COLS + col;
    }

    function hashOf(grid) {
        initZobrist();
        let h = 0;
        for (let r = 0; r < ROWS; r++) {
            for (let c = 0; c < COLS; c++) {
                const p = grid[r][c];
                if (p) { h ^= zobristTable[zobristKey(p.side, p.type, r, c)]; }
            }
        }
        return h >>> 0;
    }

    initZobrist();

    function opposite(side) { return side === RED ? BLACK : RED; }

    function inside(row, col) { return row >= 0 && row < ROWS && col >= 0 && col < COLS; }

    function inPalace(side, row, col) {
        if (col < 3 || col > 5) { return false; }
        return side === RED ? (row >= 7 && row <= 9) : (row >= 0 && row <= 2);
    }

    function crossedRiver(side, row) {
        return side === RED ? row <= 4 : row >= 5;
    }

    function pieceSymbol(side, type) { return SYMBOLS[side][type]; }

    /* ---------------- 棋盘 ---------------- */

    function emptyGrid() {
        const grid = new Array(ROWS);
        for (let r = 0; r < ROWS; r++) { grid[r] = new Array(COLS).fill(null); }
        return grid;
    }

    function place(grid, side, type, row, col) {
        grid[row][col] = { side: side, type: type };
    }

    function initialGrid() {
        const grid = emptyGrid();
        const backRank = [TYPES.CHARIOT, TYPES.HORSE, TYPES.ELEPHANT, TYPES.ADVISOR,
            TYPES.GENERAL, TYPES.ADVISOR, TYPES.ELEPHANT, TYPES.HORSE, TYPES.CHARIOT];
        for (let c = 0; c < COLS; c++) {
            place(grid, BLACK, backRank[c], 0, c);
            place(grid, RED, backRank[c], 9, c);
        }
        place(grid, BLACK, TYPES.CANNON, 2, 1);
        place(grid, BLACK, TYPES.CANNON, 2, 7);
        place(grid, RED, TYPES.CANNON, 7, 1);
        place(grid, RED, TYPES.CANNON, 7, 7);
        for (const c of [0, 2, 4, 6, 8]) {
            place(grid, BLACK, TYPES.SOLDIER, 3, c);
            place(grid, RED, TYPES.SOLDIER, 6, c);
        }
        return grid;
    }

    function cloneGrid(grid) {
        const copy = new Array(ROWS);
        for (let r = 0; r < ROWS; r++) { copy[r] = grid[r].slice(); }
        return copy;
    }

    function pieceAt(grid, row, col) {
        return inside(row, col) ? grid[row][col] : null;
    }

    /* ---------------- 伪合法走法 ---------------- */

    function canLand(grid, side, row, col) {
        if (!inside(row, col)) { return false; }
        const occ = grid[row][col];
        return occ == null || occ.side !== side;
    }

    function pseudoTargets(grid, from) {
        const piece = grid[from.row][from.col];
        const side = piece.side;
        const type = piece.type;
        const targets = [];
        const push = (r, c) => { if (canLand(grid, side, r, c)) { targets.push({ row: r, col: c }); } };

        switch (type) {
            case TYPES.GENERAL: {
                const dirs = [[1, 0], [-1, 0], [0, 1], [0, -1]];
                for (const d of dirs) {
                    const r = from.row + d[0], c = from.col + d[1];
                    if (inside(r, c) && inPalace(side, r, c)) { push(r, c); }
                }
                break;
            }
            case TYPES.ADVISOR: {
                const dirs = [[1, 1], [1, -1], [-1, 1], [-1, -1]];
                for (const d of dirs) {
                    const r = from.row + d[0], c = from.col + d[1];
                    if (inside(r, c) && inPalace(side, r, c)) { push(r, c); }
                }
                break;
            }
            case TYPES.ELEPHANT: {
                const dirs = [[2, 2], [2, -2], [-2, 2], [-2, -2]];
                for (const d of dirs) {
                    const r = from.row + d[0], c = from.col + d[1];
                    if (!inside(r, c) || crossedRiver(side, r)) { continue; }
                    const eyeR = from.row + d[0] / 2, eyeC = from.col + d[1] / 2;
                    if (grid[eyeR][eyeC] != null) { continue; }
                    push(r, c);
                }
                break;
            }
            case TYPES.HORSE: {
                const moves = [[-2, -1], [-2, 1], [-1, -2], [-1, 2], [1, -2], [1, 2], [2, -1], [2, 1]];
                for (const m of moves) {
                    const r = from.row + m[0], c = from.col + m[1];
                    if (!inside(r, c)) { continue; }
                    const legR = from.row + (m[0] === 2 ? 1 : m[0] === -2 ? -1 : 0);
                    const legC = from.col + (m[1] === 2 ? 1 : m[1] === -2 ? -1 : 0);
                    if (grid[legR][legC] != null) { continue; }
                    push(r, c);
                }
                break;
            }
            case TYPES.CHARIOT: {
                const dirs = [[1, 0], [-1, 0], [0, 1], [0, -1]];
                for (const d of dirs) {
                    let r = from.row + d[0], c = from.col + d[1];
                    while (inside(r, c)) {
                        if (grid[r][c] == null) {
                            push(r, c);
                        } else {
                            if (grid[r][c].side !== side) { push(r, c); }
                            break;
                        }
                        r += d[0]; c += d[1];
                    }
                }
                break;
            }
            case TYPES.CANNON: {
                const dirs = [[1, 0], [-1, 0], [0, 1], [0, -1]];
                for (const d of dirs) {
                    let hasScreen = false;
                    let r = from.row + d[0], c = from.col + d[1];
                    while (inside(r, c)) {
                        if (!hasScreen) {
                            if (grid[r][c] == null) { push(r, c); } else { hasScreen = true; }
                        } else if (grid[r][c] != null) {
                            if (grid[r][c].side !== side) { push(r, c); }
                            break;
                        }
                        r += d[0]; c += d[1];
                    }
                }
                break;
            }
            case TYPES.SOLDIER: {
                const forward = side === RED ? -1 : 1;
                push(from.row + forward, from.col);
                if (crossedRiver(side, from.row)) {
                    push(from.row, from.col - 1);
                    push(from.row, from.col + 1);
                }
                break;
            }
        }
        return targets;
    }

    /* ---------------- 将军检测 ---------------- */

    function findGeneral(grid, side) {
        for (let r = 0; r < ROWS; r++) {
            for (let c = 0; c < COLS; c++) {
                const p = grid[r][c];
                if (p && p.side === side && p.type === TYPES.GENERAL) { return { row: r, col: c }; }
            }
        }
        return null;
    }

    function generalsFacing(grid) {
        const red = findGeneral(grid, RED);
        const black = findGeneral(grid, BLACK);
        if (!red || !black || red.col !== black.col) { return false; }
        const from = Math.min(red.row, black.row) + 1;
        const to = Math.max(red.row, black.row);
        for (let r = from; r < to; r++) {
            if (grid[r][red.col] != null) { return false; }
        }
        return true;
    }

    function isSoldierAt(grid, row, col, side) {
        const p = pieceAt(grid, row, col);
        return p != null && p.side === side && p.type === TYPES.SOLDIER;
    }

    function isSquareAttacked(grid, target, bySide) {
        // 车与炮：四条直线扫描
        const lines = [[1, 0], [-1, 0], [0, 1], [0, -1]];
        for (const line of lines) {
            let r = target.row + line[0], c = target.col + line[1];
            let screenFound = false;
            while (inside(r, c)) {
                const p = grid[r][c];
                if (p != null) {
                    if (!screenFound) {
                        if (p.side === bySide && p.type === TYPES.CHARIOT) { return true; }
                        screenFound = true;
                    } else {
                        if (p.side === bySide && p.type === TYPES.CANNON) { return true; }
                        break;
                    }
                }
                r += line[0]; c += line[1];
            }
        }

        // 马：从 8 个马位反推马腿
        const horseOffsets = [[-2, -1], [-2, 1], [-1, -2], [-1, 2], [1, -2], [1, 2], [2, -1], [2, 1]];
        for (const o of horseOffsets) {
            const hr = target.row + o[0], hc = target.col + o[1];
            if (!inside(hr, hc)) { continue; }
            const p = grid[hr][hc];
            if (!p || p.side !== bySide || p.type !== TYPES.HORSE) { continue; }
            const dr = target.row - hr, dc = target.col - hc;
            const legR = hr + (dr === 2 ? 1 : dr === -2 ? -1 : 0);
            const legC = hc + (dc === 2 ? 1 : dc === -2 ? -1 : 0);
            if (grid[legR][legC] == null) { return true; }
        }

        // 兵/卒
        if (bySide === RED) {
            const fwd = pieceAt(grid, target.row + 1, target.col);
            if (fwd && fwd.side === RED && fwd.type === TYPES.SOLDIER) { return true; }
            if (target.row <= 4) {
                if (isSoldierAt(grid, target.row, target.col - 1, RED)) { return true; }
                if (isSoldierAt(grid, target.row, target.col + 1, RED)) { return true; }
            }
        } else {
            const fwd = pieceAt(grid, target.row - 1, target.col);
            if (fwd && fwd.side === BLACK && fwd.type === TYPES.SOLDIER) { return true; }
            if (target.row >= 5) {
                if (isSoldierAt(grid, target.row, target.col - 1, BLACK)) { return true; }
                if (isSoldierAt(grid, target.row, target.col + 1, BLACK)) { return true; }
            }
        }
        return false;
    }

    function isInCheck(grid, side) {
        const general = findGeneral(grid, side);
        if (general == null) { return true; }
        return generalsFacing(grid) || isSquareAttacked(grid, general, opposite(side));
    }

    /* ---------------- make / unmake ---------------- */

    function make(grid, move) {
        const mover = grid[move.from.row][move.from.col];
        const captured = grid[move.to.row][move.to.col];
        currentHash ^= zobristTable[zobristKey(mover.side, mover.type, move.from.row, move.from.col)];
        currentHash ^= zobristTable[zobristKey(mover.side, mover.type, move.to.row, move.to.col)];
        if (captured) {
            currentHash ^= zobristTable[zobristKey(captured.side, captured.type, move.to.row, move.to.col)];
        }
        currentHash ^= zobristTurnHash;
        grid[move.to.row][move.to.col] = mover;
        grid[move.from.row][move.from.col] = null;
        return captured;
    }

    function unmake(grid, move, captured) {
        const mover = grid[move.to.row][move.to.col];
        currentHash ^= zobristTurnHash;
        if (captured) {
            currentHash ^= zobristTable[zobristKey(captured.side, captured.type, move.to.row, move.to.col)];
        }
        currentHash ^= zobristTable[zobristKey(mover.side, mover.type, move.to.row, move.to.col)];
        currentHash ^= zobristTable[zobristKey(mover.side, mover.type, move.from.row, move.from.col)];
        grid[move.from.row][move.from.col] = mover;
        grid[move.to.row][move.to.col] = captured;
    }

    /* ---------------- 合法走法 ---------------- */

    function isSafeAfterMove(grid, side, move) {
        const occ = grid[move.to.row][move.to.col];
        if (occ && occ.side === side) { return false; }
        const captured = make(grid, move);
        const safe = !isInCheck(grid, side);
        unmake(grid, move, captured);
        return safe;
    }

    function legalMoves(grid, side) {
        const moves = [];
        for (let r = 0; r < ROWS; r++) {
            for (let c = 0; c < COLS; c++) {
                const p = grid[r][c];
                if (!p || p.side !== side) { continue; }
                const from = { row: r, col: c };
                for (const to of pseudoTargets(grid, from)) {
                    const move = { from: from, to: to };
                    if (isSafeAfterMove(grid, side, move)) { moves.push(move); }
                }
            }
        }
        return moves;
    }

    function legalCaptureMoves(grid, side) {
        const moves = [];
        for (let r = 0; r < ROWS; r++) {
            for (let c = 0; c < COLS; c++) {
                const p = grid[r][c];
                if (!p || p.side !== side) { continue; }
                const from = { row: r, col: c };
                for (const to of pseudoTargets(grid, from)) {
                    if (grid[to.row][to.col] == null) { continue; }
                    const move = { from: from, to: to };
                    if (isSafeAfterMove(grid, side, move)) { moves.push(move); }
                }
            }
        }
        return moves;
    }

    /* ---------------- 评估 ---------------- */

    function pstValue(piece, row, col) {
        const mirrorRow = piece.side === RED ? row : ROWS - 1 - row;
        let table;
        switch (piece.type) {
            case TYPES.SOLDIER: table = SOLDIER_PST; break;
            case TYPES.CHARIOT: table = CHARIOT_PST; break;
            case TYPES.HORSE: table = HORSE_PST; break;
            case TYPES.CANNON: table = CANNON_PST; break;
            default: return 0;
        }
        return table[mirrorRow][col] || 0;
    }

    function evaluate(grid, perspective) {
        let redScore = 0;
        for (let r = 0; r < ROWS; r++) {
            for (let c = 0; c < COLS; c++) {
                const p = grid[r][c];
                if (p == null) { continue; }
                const value = MATERIAL[p.type] + pstValue(p, r, c);
                redScore += p.side === RED ? value : -value;
            }
        }
        return perspective === RED ? redScore : -redScore;
    }

    /* ---------------- 搜索 ---------------- */

    let deadline = 0;
    let nodes = 0;

    function moveScore(grid, move) {
        const attacker = grid[move.from.row][move.from.col];
        const victim = grid[move.to.row][move.to.col];
        if (attacker && victim) {
            return 900000 + MATERIAL[victim.type] * 10 - MATERIAL[attacker.type];
        }
        return 0;
    }

    function orderMoves(grid, moves) {
        return moves.slice().sort((a, b) => moveScore(grid, b) - moveScore(grid, a));
    }

    function negamax(grid, side, depth, alpha, beta, ply) {
        if ((++nodes & 1023) === 0 && Date.now() > deadline) {
            throw new Error("abort");
        }
        // 重复局面检测：同一局面再次出现按和棋处理，规避长将/长捉循环
        const h = currentHash >>> 0;
        const seen = repetitionCount.get(h) || 0;
        if (seen >= 1) {
            return 0;
        }
        repetitionCount.set(h, seen + 1);
        try {
            if (ply >= 126) { return evaluate(grid, side); }
            if (depth <= 0) { return quiescence(grid, side, alpha, beta, ply, 0); }

            const moves = legalMoves(grid, side);
            if (moves.length === 0) { return -MATE_SCORE + ply; }

            let bestScore = -INF;
            for (const move of orderMoves(grid, moves)) {
                const captured = make(grid, move);
                let score;
                try {
                    score = -negamax(grid, opposite(side), depth - 1, -beta, -alpha, ply + 1);
                } finally {
                    unmake(grid, move, captured);
                }
                if (score > bestScore) { bestScore = score; }
                if (score > alpha) { alpha = score; }
                if (alpha >= beta) { break; }
            }
            return bestScore;
        } finally {
            repetitionCount.set(h, seen);
        }
    }

    function quiescence(grid, side, alpha, beta, ply, qDepth) {
        if ((++nodes & 1023) === 0 && Date.now() > deadline) {
            throw new Error("abort");
        }
        const standPat = evaluate(grid, side);
        if (standPat >= beta) { return beta; }
        if (standPat > alpha) { alpha = standPat; }
        if (qDepth >= 32) { return alpha; }

        const captures = legalCaptureMoves(grid, side);
        if (captures.length === 0) { return alpha; }
        for (const move of orderMoves(grid, captures)) {
            const captured = make(grid, move);
            let score;
            try {
                score = -quiescence(grid, opposite(side), -beta, -alpha, ply + 1, qDepth + 1);
            } finally {
                unmake(grid, move, captured);
            }
            if (score >= beta) { return beta; }
            if (score > alpha) { alpha = score; }
        }
        return alpha;
    }

    function search(grid, side, maxDepth, timeLimitMs) {
        deadline = Date.now() + Math.max(100, timeLimitMs);
        nodes = 0;
        currentHash = hashOf(grid);
        repetitionCount = new Map();
        repetitionCount.set(currentHash >>> 0, 1);
        const rootMoves = legalMoves(grid, side);
        if (rootMoves.length === 0) { return null; }

        let bestMove = rootMoves[0];
        try {
            for (let depth = 1; depth <= Math.max(1, maxDepth); depth++) {
                let localBest = null;
                let localBestScore = -INF;
                let alpha = -INF;
                for (const move of orderMoves(grid, rootMoves)) {
                    const captured = make(grid, move);
                    let score;
                    try {
                        score = -negamax(grid, opposite(side), depth - 1, -INF, -alpha, 1);
                    } finally {
                        unmake(grid, move, captured);
                    }
                    if (localBest == null || score > localBestScore) {
                        localBestScore = score;
                        localBest = move;
                    }
                    if (score > alpha) { alpha = score; }
                }
                bestMove = localBest || bestMove;
                if (localBestScore > 490000) { break; }
            }
        } catch (e) {
            // 超时中断，保留上一层结果
        }
        return bestMove;
    }

    /* ---------------- 局面分析 ---------------- */

    function analyze(grid, side, maxDepth, timeLimitMs) {
        deadline = Date.now() + Math.max(100, timeLimitMs);
        nodes = 0;
        currentHash = hashOf(grid);
        repetitionCount = new Map();
        repetitionCount.set(currentHash >>> 0, 1);
        const rootMoves = legalMoves(grid, side);
        if (rootMoves.length === 0) {
            return { move: null, notation: "", score: -MATE_SCORE, depth: 0, nodes: 0, candidates: [], turn: side };
        }

        let bestMove = rootMoves[0];
        let bestScore = 0;
        let completedDepth = 0;
        let candidates = [];

        try {
            for (let depth = 1; depth <= Math.max(1, maxDepth); depth++) {
                let localBest = null;
                let localBestScore = -INF;
                let alpha = -INF;
                const scored = [];
                for (const move of orderMoves(grid, rootMoves)) {
                    const captured = make(grid, move);
                    let score;
                    try {
                        score = -negamax(grid, opposite(side), depth - 1, -INF, -alpha, 1);
                    } finally {
                        unmake(grid, move, captured);
                    }
                    scored.push({ move: move, score: score, notation: notation(grid, side, move) });
                    if (localBest == null || score > localBestScore) {
                        localBestScore = score;
                        localBest = move;
                    }
                    if (score > alpha) { alpha = score; }
                }
                completedDepth = depth;
                bestMove = localBest || bestMove;
                bestScore = localBestScore;
                candidates = scored.slice().sort((a, b) => b.score - a.score);
                if (localBestScore > 490000) { break; }
            }
        } catch (e) {
            // 超时中断，保留上一层结果
        }

        return {
            move: bestMove,
            notation: notation(grid, side, bestMove),
            score: bestScore,
            depth: completedDepth,
            nodes: nodes,
            candidates: candidates,
            turn: side
        };
    }

    /* ---------------- 记谱 ---------------- */

    const CHINESE_DIGITS = ["一", "二", "三", "四", "五", "六", "七", "八", "九"];

    function fileLabel(col, side) {
        return side === RED ? CHINESE_DIGITS[COLS - 1 - col] : String(col + 1);
    }

    function numberLabel(n, side) {
        if (side === BLACK) { return String(n); }
        return (n >= 1 && n <= 9) ? CHINESE_DIGITS[n - 1] : String(n);
    }

    function rankPrefix(grid, from, side, type) {
        const sameFile = [];
        for (let r = 0; r < ROWS; r++) {
            const p = grid[r][from.col];
            if (p && p.side === side && p.type === type) { sameFile.push(r); }
        }
        if (sameFile.length < 2 || sameFile.length > 3) { return null; }
        sameFile.sort((a, b) => side === RED ? a - b : b - a);
        const index = sameFile.indexOf(from.row);
        if (sameFile.length === 2) { return index === 0 ? "前" : "后"; }
        return index === 0 ? "前" : index === 1 ? "中" : "后";
    }

    function notation(grid, side, move) {
        const piece = grid[move.from.row][move.from.col];
        if (!piece || piece.side !== side) { return ""; }
        const type = piece.type;
        const pieceName = SYMBOLS[side][type];
        const prefix = rankPrefix(grid, move.from, side, type);
        const head = prefix != null
            ? prefix + pieceName
            : pieceName + fileLabel(move.from.col, side);

        if (move.from.row === move.to.row) {
            return head + "平" + fileLabel(move.to.col, side);
        }
        const forward = side === RED
            ? move.to.row < move.from.row
            : move.to.row > move.from.row;
        const action = forward ? "进" : "退";
        if (type === TYPES.HORSE || type === TYPES.ELEPHANT || type === TYPES.ADVISOR) {
            return head + action + fileLabel(move.to.col, side);
        }
        const rows = Math.abs(move.to.row - move.from.row);
        return head + action + numberLabel(rows, side);
    }

    /* ---------------- 对局状态机 ---------------- */

    let seq = 0;
    function newGameId() {
        seq += 1;
        return "local-" + Date.now().toString(36) + "-" + seq.toString(36);
    }

    function computeStatus(grid, turn, resultReason) {
        const moves = legalMoves(grid, turn);
        if (moves.length === 0) {
            const status = turn === RED ? "BLACK_WIN" : "RED_WIN";
            return { status: status, resultReason: status === "RED_WIN" ? "红方胜" : "黑方胜" };
        }
        return { status: "PLAYING", resultReason: resultReason || "" };
    }

    function toView(game) {
        const grid = game.grid;
        const pieces = [];
        for (let r = 0; r < ROWS; r++) {
            for (let c = 0; c < COLS; c++) {
                const p = grid[r][c];
                if (p) {
                    pieces.push({ row: r, col: c, side: p.side, type: p.type, symbol: SYMBOLS[p.side][p.type] });
                }
            }
        }
        const history = game.history.map((entry, i) => ({
            index: i + 1,
            side: entry.side,
            move: entry.move,
            notation: entry.notation,
            capturedSymbol: entry.capturedSymbol
        }));
        const lastMove = game.history.length > 0
            ? game.history[game.history.length - 1].move
            : null;
        return {
            gameId: game.gameId,
            turn: game.turn,
            status: game.status,
            resultReason: game.resultReason,
            inCheck: isInCheck(grid, game.turn),
            ply: game.history.length,
            lastMove: lastMove,
            pieces: pieces,
            legalMoves: game.status === "PLAYING" ? legalMoves(grid, game.turn) : [],
            history: history
        };
    }

    function newGame() {
        const grid = initialGrid();
        const game = {
            gameId: newGameId(),
            grid: grid,
            turn: RED,
            history: [],
            status: "PLAYING",
            resultReason: ""
        };
        return toView(game);
    }

    function internalize(view) {
        // 从 GameView 重建内部 grid + history
        const grid = emptyGrid();
        for (const p of view.pieces) {
            grid[p.row][p.col] = { side: p.side, type: p.type };
        }
        const history = view.history.map((h) => ({
            side: h.side,
            move: { from: { row: h.move.from.row, col: h.move.from.col }, to: { row: h.move.to.row, col: h.move.to.col } },
            notation: h.notation,
            capturedSymbol: h.capturedSymbol
        }));
        return {
            gameId: view.gameId,
            grid: grid,
            turn: view.turn,
            history: history,
            status: view.status,
            resultReason: view.resultReason
        };
    }

    function move(game, from, to) {
        if (game.status !== "PLAYING") { return game; }
        const grid = game.grid;
        const move = { from: from, to: to };
        const mover = grid[from.row][from.col];
        if (!mover || mover.side !== game.turn) { throw new Error("还没轮到该方走棋"); }
        const targets = pseudoTargets(grid, from);
        const ok = targets.some((t) => t.row === to.row && t.col === to.col);
        if (!ok) { throw new Error("不合法走法"); }
        if (!isSafeAfterMove(grid, game.turn, move)) { throw new Error("走棋后己方将帅被将军"); }

        const notationText = notation(grid, game.turn, move);
        const captured = make(grid, move);
        game.history.push({
            side: game.turn,
            move: move,
            notation: notationText,
            capturedSymbol: captured ? SYMBOLS[captured.side][captured.type] : null
        });
        game.turn = opposite(game.turn);
        const outcome = computeStatus(grid, game.turn, game.resultReason);
        game.status = outcome.status;
        game.resultReason = outcome.resultReason;
        return toView(game);
    }

    function undo(game) {
        if (game.history.length === 0) { return game; }
        game.history.pop();
        return rebuildFromHistory(game.gameId, game.history, game.resultReason);
    }

    function resign(game, side) {
        if (game.status !== "PLAYING") { return game; }
        const status = side === RED ? "BLACK_WIN" : "RED_WIN";
        const resultReason = side === RED ? "红方认输" : "黑方认输";
        game.status = status;
        game.resultReason = resultReason;
        return toView(game);
    }

    function rebuildFromHistory(gameId, history, resultReason) {
        const grid = initialGrid();
        const rebuilt = [];
        for (const entry of history) {
            const move = entry.move;
            const captured = make(grid, move);
            rebuilt.push({
                side: entry.side,
                move: move,
                notation: entry.notation,
                capturedSymbol: captured ? SYMBOLS[captured.side][captured.type] : null
            });
        }
        const turn = history.length === 0 ? RED : opposite(history[history.length - 1].side);
        const outcome = computeStatus(grid, turn, resultReason);
        return toView({
            gameId: gameId,
            grid: grid,
            turn: turn,
            history: rebuilt,
            status: outcome.status,
            resultReason: outcome.resultReason
        });
    }

    function aiMove(game, depth) {
        if (game.status !== "PLAYING") { return game; }
        const grid = game.grid;
        const move = search(grid, game.turn, depth, 2000 + depth * 200);
        if (!move) { return game; }
        return moveOnInternal(game, move);
    }

    function moveOnInternal(game, move) {
        const grid = game.grid;
        const notationText = notation(grid, game.turn, move);
        const captured = make(grid, move);
        game.history.push({
            side: game.turn,
            move: move,
            notation: notationText,
            capturedSymbol: captured ? SYMBOLS[captured.side][captured.type] : null
        });
        game.turn = opposite(game.turn);
        const outcome = computeStatus(grid, game.turn, game.resultReason);
        game.status = outcome.status;
        game.resultReason = outcome.resultReason;
        return toView(game);
    }

    window.XiangqiEngine = {
        newGame: newGame,
        move: function (view, from, to) {
            const game = internalize(view);
            return move(game, from, to);
        },
        undo: function (view) {
            const game = internalize(view);
            if (game.history.length === 0) { return view; }
            game.history.pop();
            return rebuildFromHistory(game.gameId, game.history, game.resultReason);
        },
        resign: function (view, side) {
            const game = internalize(view);
            return resign(game, side);
        },
        aiMove: function (view, depth) {
            const game = internalize(view);
            return aiMove(game, depth);
        },
        analyze: function (view, depth, timeLimitMs) {
            const game = internalize(view);
            return analyze(game.grid, game.turn, depth, timeLimitMs);
        },
        toView: toView
    };
})();
