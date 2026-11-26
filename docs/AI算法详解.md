# 中国象棋 AI 计算算法详解

> 本文档说明「纯净中国象棋」内置引擎（`frontend/js/engine.js`）的完整计算流程，覆盖棋盘表示、走法生成、局面评估、搜索算法与结果判定，以及分析系统如何复用这套算法。

---

## 1. 总览

引擎是一个纯 JavaScript 实现、无依赖的本地象棋引擎，对外暴露 `window.XiangqiEngine`。整体采用经典的「生成-评估-搜索」三层结构：

```
局面状态 → 走法生成 → 局面评估 → 搜索（Negamax + Alpha-Beta + 迭代加深 + 静态搜索）
                                                        ↓
                                              最佳走法 / 评估分 / 候选走法
```

| 能力 | 说明 |
| --- | --- |
| 走法生成 | 全量合法走法 + 将军检测 + 自将过滤 |
| 局面评估 | 子力价值 + 位置价值表（PST） |
| 搜索 | Negamax（负极大值）+ Alpha-Beta 剪枝 + 迭代加深 + 静态搜索 |
| 结果判定 | 将死 / 困毙判负 |
| 分析接口 | 返回评估分、最佳走法、候选走法、搜索深度、节点数 |

---

## 2. 棋盘与棋子表示

### 2.1 坐标系统

棋盘为 **10 行 × 9 列** 的二维数组：

```js
const ROWS = 10;   // 行 0~9，红方在底部（行 7~9），黑方在顶部（行 0~2）
const COLS = 9;    // 列 0~8
```

每个交叉点用一个对象表示，空点用 `null`：

```js
grid[row][col] = { side: "RED" | "BLACK", type: "GENERAL" | ... } | null
```

### 2.2 棋子类型

| 类型 | 红方符号 | 黑方符号 | 子力价值 |
| --- | --- | --- | --- |
| GENERAL（将/帅） | 帥 | 將 | 100000 |
| ADVISOR（士/仕） | 仕 | 士 | 200 |
| ELEPHANT（相/象） | 相 | 象 | 200 |
| HORSE（马） | 馬 | 馬 | 400 |
| CHARIOT（车） | 車 | 車 | 900 |
| CANNON（炮） | 炮 | 砲 | 450 |
| SOLDIER（兵/卒） | 兵 | 卒 | 100 |

---

## 3. 走法生成

走法生成分三步，从「伪合法」逐步过滤到「完全合法」。

### 3.1 伪合法目标点 `pseudoTargets(grid, from)`

按每种棋子的走法规则，生成某棋子的所有可达点，**不检查是否会导致己方被将军**。

- **将/帅**：九宫格内走一格，且不能与对方将帅「照面」。
- **士/仕**：九宫格内斜走一格。
- **相/象**：田字斜走，塞象眼则不能走，不能过河。
- **马**：日字走，蹩马腿则不能走。
- **车**：沿直线，直到被棋子阻挡（可吃挡住的敌子）。
- **炮**：沿直线移动时不可吃子；吃子时必须隔一个「炮架」。
- **兵/卒**：过河前只能前进；过河后可前进或左右横移，不能后退。

### 3.2 自将过滤 `isSafeAfterMove`

用 make/unmake 试探性地走一步，再检测己方将帅是否处于被将军状态：

```js
function isSafeAfterMove(grid, side, move) {
    const captured = make(grid, move);   // 走子
    const safe = !isInCheck(grid, side); // 走子后己方是否被将军
    unmake(grid, move, captured);        // 回退
    return safe;
}
```

### 3.3 合法走法 `legalMoves(grid, side)`

遍历己方所有棋子，对每个伪合法目标点执行 `isSafeAfterMove`，保留不会导致被将军的走法：

```js
for (每个己方棋子) {
    for (每个伪合法目标点) {
        if (isSafeAfterMove(grid, side, move)) moves.push(move);
    }
}
```

`legalCaptureMoves` 是它的子集，只保留吃子走法，供静态搜索使用。

---

## 4. 局面评估

### 4.1 静态评估函数 `evaluate(grid, perspective)`

评估值 = **子力价值 + 位置价值**，以「红方得分」为基准累加，红方棋子为正、黑方棋子为负：

```js
function evaluate(grid, perspective) {
    let redScore = 0;
    for (每个交叉点) {
        const value = MATERIAL[p.type] + pstValue(p, r, c);
        redScore += p.side === RED ? value : -value;
    }
    return perspective === RED ? redScore : -redScore;  // 返回指定视角
}
```

- 正值：红方占优；负值：黑方占优。
- 返回值始终为「当前走子方」视角（调用方传入 `perspective`），便于 Negamax 统一符号。

### 4.2 位置价值表（Piece-Square Table）

为兵、车、马、炮各定义一张 10×9 的加分表，奖励棋子占据有利位置（如兵过河、马在中央、车占肋道）。红方直接查表，黑方按行镜像：

```js
function pstValue(piece, row, col) {
    switch (piece.type) {
        case SOLDIER: table = SOLDIER_PST; break;
        case CHARIOT: table = CHARIOT_PST; break;
        case HORSE:   table = HORSE_PST;   break;
        case CANNON:  table = CANNON_PST;  break;
        default: return 0;
    }
    const mirrorRow = piece.side === RED ? row : (ROWS - 1 - row);
    return table[mirrorRow][col] || 0;
}
```

---

## 5. 搜索算法

### 5.1 Negamax（负极大值搜索）`negamax(grid, side, depth, alpha, beta, ply)`

Negamax 利用「双方都最大化自身得分」的对称性，把 max/min 两套逻辑合并为单一边界：

```js
if (depth <= 0) return quiescence(...);              // 叶子节点进入静态搜索
const moves = legalMoves(grid, side);
if (moves.length === 0) return -MATE_SCORE + ply;     // 无子可走：被将死/困毙

let bestScore = -INF;
for (const move of orderMoves(grid, moves)) {
    make(grid, move);
    score = -negamax(grid, opposite(side), depth - 1, -beta, -alpha, ply + 1);
    unmake(grid, move);
    bestScore = max(bestScore, score);
    alpha = max(alpha, score);
    if (alpha >= beta) break;                          // Beta 剪枝
}
return bestScore;
```

关键点：

- **符号取负**：递归返回对手视角的得分，取负后即本方的得分。
- **杀棋分值带 `ply`**：`-MATE_SCORE + ply` 保证「更快将杀」比「更慢将杀」得分更高，让引擎优先选择最短杀棋路线。

### 5.2 Alpha-Beta 剪枝

维护 `alpha`（已找到的最好下界）与 `beta`（对手能接受的上界）。当 `alpha >= beta` 时，后续分支不可能影响结果，直接剪掉，大幅减少搜索节点数。

### 5.3 走法排序 `orderMoves`

在搜索前对走法排序，让「更可能好的」走法先被搜索，提高剪枝效率。当前采用 **MVV-LVA**（最有价值被吃子 - 最不有价值攻击子）：

```js
function moveScore(grid, move) {
    if (有吃子) return 900000 + MATERIAL[victim] * 10 - MATERIAL[attacker];
    return 0;
}
```

吃子走法（尤其是吃大子）排在最前，能显著提升剪枝命中率。

### 5.4 静态搜索 `quiescence(grid, side, alpha, beta, ply, qDepth)`

普通搜索到叶子层直接返回静态评估会带来「地平线效应」（下一步就要被吃子却看不到）。静态搜索在叶子节点继续**只展开吃子走法**，直到局面「安静」：

```js
const standPat = evaluate(grid, side);   // 当前静止评估
if (standPat >= beta) return beta;
if (standPat > alpha) alpha = standPat;
for (const move of 吃子走法) { ... }       // 只搜索吃子，且同样带 Alpha-Beta 剪枝
```

### 5.5 迭代加深 `search(grid, side, maxDepth, timeLimitMs)`

从深度 1 逐层加深到 `maxDepth`，每层都做一次完整的根节点搜索：

```js
for (let depth = 1; depth <= maxDepth; depth++) {
    for (每个根走法) {
        score = -negamax(grid, opposite(side), depth - 1, ...);
        // 记录本层最佳
    }
    // 找到杀棋提前终止；超时则中断，保留上一层结果
}
```

好处：随时可被时限打断并返回「上一完整层」的结果，兼顾响应速度与棋力。

### 5.6 时限控制

```js
deadline = Date.now() + timeLimitMs;   // 设定截止时间
// 每搜索 1024 个节点检查一次，超时抛异常中断
if ((++nodes & 1023) === 0 && Date.now() > deadline) throw new Error("abort");
```

AI 思考时间随难度递增：`2000 + depth * 200` 毫秒（depth = 4/6/10/20）。

---

## 6. 结果判定 `computeStatus(grid, turn)`

轮到方无合法走法时判负，**将死与困毙均判负**（符合中国象棋规则，困毙不是和棋）：

```js
const moves = legalMoves(grid, turn);
if (moves.length === 0) {
    return turn === RED ? "BLACK_WIN" : "RED_WIN";
}
return "PLAYING";
```

---

## 7. 分析系统如何复用这套算法

分析系统（「分析」按钮）调用引擎新增的 `analyze` 接口：

```js
window.XiangqiEngine.analyze(view, depth, timeLimitMs)
```

它复用同一套 `evaluate` / `negamax` / `quiescence`，并在 `search` 的迭代加深基础上，额外记录：

| 返回字段 | 含义 |
| --- | --- |
| `notation` | 最佳走法的中文记谱（如「炮二平五」） |
| `score` | 最佳走法的评估分（当前走子方视角，正=占优） |
| `depth` | 实际完成的最大搜索深度 |
| `nodes` | 搜索过的节点总数 |
| `candidates` | 所有根走法按得分排序（前端取前 5 展示） |

前端分析弹窗据此渲染：

- **评估条**：将 `score` 转换为红/黑优势比例（`50 + 50 * score/(|score|+1000)`），视觉化双方形势。
- **评估文本**：`|score| ≥ 490000` 显示「绝杀」，否则显示「红方/黑方优势 +X」或「均势」。
- **最佳走法**：显示 `notation`。
- **候选走法**：列出前 5 个走法及各自得分。

---

## 8. 难度档位与参数

| 档位 | 最大搜索深度 | 时间上限 | 适用 |
| --- | --- | --- | --- |
| 入门 | 4 层 | ~2.8s | 新手 |
| 进阶 | 6 层 | ~3.2s | 默认 |
| 高级 | 10 层 | ~4.0s | 有经验 |
| 顶级 | 20 层 | ~6.0s | 挑战 |

由于迭代加深 + 时限中断，实际完成深度受时间限制，通常顶级档在较复杂局面下也只能完成 8~12 层，但配合静态搜索与走法排序，棋力已足够覆盖绝大多数业余对局。

---

## 9. 算法复杂度

- **分支因子**：象棋平均约 30~40 个合法走法。
- **理论复杂度**：`O(b^d)`（b 为分支因子，d 为深度）。
- **Alpha-Beta 剪枝**：理想情况下可将有效分支因子降到约 `√b`，即同样时间可多搜近一倍深度。
- **走法排序 + 静态搜索**：进一步提升剪枝效率并消除地平线效应。
