(function () {
    "use strict";

    const SVG_NS = "http://www.w3.org/2000/svg";
    const VIEW_WIDTH = 900;
    const VIEW_HEIGHT = 1000;
    const ORIGIN_X = 50;
    const ORIGIN_Y = 50;
    const STEP = 100;
    const COLS = 9;
    const ROWS = 10;

    const STAR_POINTS = [
        [2, 1], [2, 7], [7, 1], [7, 7],
        [3, 0], [3, 2], [3, 4], [3, 6], [3, 8],
        [6, 0], [6, 2], [6, 4], [6, 6], [6, 8]
    ];

    function svgNode(tag, attributes) {
        const node = document.createElementNS(SVG_NS, tag);
        Object.keys(attributes || {}).forEach(function (name) {
            node.setAttribute(name, attributes[name]);
        });
        return node;
    }

    function svgLine(group, x1, y1, x2, y2) {
        group.appendChild(svgNode("line", { x1: x1, y1: y1, x2: x2, y2: y2 }));
    }

    class XiangqiBoard {
        constructor(boardElement) {
            this.boardEl = boardElement;
            this.svgEl = boardElement.querySelector(".board-svg");
            this.piecesEl = boardElement.querySelector(".layer-pieces");
            this.markersEl = boardElement.querySelector(".layer-markers");
            this.onCell = null;
            this.prevState = null;
            this._buildSvg();
            this.boardEl.addEventListener("click", (event) => this._handlePointer(event));
        }

        _buildSvg() {
            const svg = this.svgEl;
            while (svg.firstChild) {
                svg.removeChild(svg.firstChild);
            }

            const grid = svgNode("g", {
                stroke: "#8a5a2b",
                "stroke-width": "2.1",
                "stroke-linecap": "round",
                fill: "none"
            });

            for (let row = 0; row < ROWS; row++) {
                const y = ORIGIN_Y + row * STEP;
                svgLine(grid, ORIGIN_X, y, ORIGIN_X + (COLS - 1) * STEP, y);
            }

            for (let col = 0; col < COLS; col++) {
                const x = ORIGIN_X + col * STEP;
                if (col === 0 || col === COLS - 1) {
                    svgLine(grid, x, ORIGIN_Y, x, ORIGIN_Y + (ROWS - 1) * STEP);
                } else {
                    svgLine(grid, x, ORIGIN_Y, x, ORIGIN_Y + 4 * STEP);
                    svgLine(grid, x, ORIGIN_Y + 5 * STEP, x, ORIGIN_Y + (ROWS - 1) * STEP);
                }
            }

            // 九宫斜线
            const palaceTopLeft = ORIGIN_X + 3 * STEP;
            const palaceTopRight = ORIGIN_X + 5 * STEP;
            svgLine(grid, palaceTopLeft, ORIGIN_Y, palaceTopRight, ORIGIN_Y + 2 * STEP);
            svgLine(grid, palaceTopRight, ORIGIN_Y, palaceTopLeft, ORIGIN_Y + 2 * STEP);
            svgLine(grid, palaceTopLeft, ORIGIN_Y + 7 * STEP, palaceTopRight, ORIGIN_Y + 9 * STEP);
            svgLine(grid, palaceTopRight, ORIGIN_Y + 7 * STEP, palaceTopLeft, ORIGIN_Y + 9 * STEP);

            svg.appendChild(grid);

            // 兵/炮位标记
            const marks = svgNode("g", {
                stroke: "#7a4a22",
                "stroke-width": "1.7",
                "stroke-linecap": "round",
                "stroke-opacity": "0.72",
                fill: "none"
            });
            const corners = [[-1, -1], [-1, 1], [1, -1], [1, 1]];
            STAR_POINTS.forEach(function (point) {
                const row = point[0];
                const col = point[1];
                const cx = ORIGIN_X + col * STEP;
                const cy = ORIGIN_Y + row * STEP;
                corners.forEach(function (corner) {
                    const dx = corner[0];
                    const dy = corner[1];
                    if (col === 0 && dx < 0 || col === COLS - 1 && dx > 0) {
                        return;
                    }
                    if (row === 0 && dy < 0 || row === ROWS - 1 && dy > 0) {
                        return;
                    }
                    const x = cx + dx * 11;
                    const y = cy + dy * 11;
                    svgLine(marks, x - dx * 9, y, x, y);
                    svgLine(marks, x, y - dy * 9, x, y);
                });
            });
            svg.appendChild(marks);

            // 外框
            svg.appendChild(svgNode("rect", {
                x: 22, y: 22, width: 856, height: 956, rx: 6,
                fill: "none", stroke: "#7a4a22", "stroke-width": "4"
            }));
            svg.appendChild(svgNode("rect", {
                x: 34, y: 34, width: 832, height: 932, rx: 3,
                fill: "none", stroke: "#7a4a22", "stroke-width": "1.4", "stroke-opacity": "0.55"
            }));

            // 楚河汉界
            const riverStyle = {
                x: 250, y: 524,
                "text-anchor": "middle",
                "font-size": "46",
                "font-family": "STKaiti, KaiTi, Songti SC, serif",
                fill: "#8a5a2b",
                "fill-opacity": "0.88",
                "letter-spacing": "10"
            };
            const leftText = svgNode("text", riverStyle);
            leftText.textContent = "楚 河";
            svg.appendChild(leftText);

            const rightText = svgNode("text", Object.assign({}, riverStyle, { x: 650 }));
            rightText.textContent = "漢 界";
            svg.appendChild(rightText);
        }

        render(state, selected) {
            const previous = this.prevState;
            this.piecesEl.textContent = "";
            this.markersEl.textContent = "";

            if (!state) {
                this.prevState = null;
                return;
            }

            const lastMove = state.lastMove || null;
            if (lastMove) {
                this._addMarker("last-from", lastMove.from.row, lastMove.from.col);
                this._addMarker("last-to", lastMove.to.row, lastMove.to.col);
            }

            if (selected) {
                const targetKeys = new Set();
                (state.legalMoves || []).forEach((move) => {
                    if (move.from.row === selected.row && move.from.col === selected.col) {
                        targetKeys.add(move.to.row + "," + move.to.col);
                    }
                });
                const occupied = new Set(state.pieces.map((piece) => piece.row + "," + piece.col));
                targetKeys.forEach((key) => {
                    const parts = key.split(",");
                    const row = Number(parts[0]);
                    const col = Number(parts[1]);
                    this._addMarker(occupied.has(key) ? "capture" : "dot", row, col);
                });
            }

            // 只有“上一步局面 → 当前局面”且步数恰好 +1 时才播放走子动画，避免复盘跳转时错位
            const animate = previous && previous !== state && previous.ply !== undefined
                && previous.gameId === state.gameId
                && state.ply === previous.ply + 1 && lastMove;
            let movedKey = null;
            if (animate) {
                const origin = previous.pieces.find((piece) =>
                    piece.row === lastMove.from.row && piece.col === lastMove.from.col);
                const target = state.pieces.find((piece) =>
                    piece.row === lastMove.to.row && piece.col === lastMove.to.col);
                if (origin && target && origin.side === target.side && origin.type === target.type) {
                    movedKey = lastMove.to.row + "," + lastMove.to.col;
                    const captured = previous.pieces.find((piece) =>
                        piece.row === lastMove.to.row && piece.col === lastMove.to.col);
                    if (captured) {
                        this._addGhost(captured);
                    }
                }
            }

            state.pieces.forEach((piece) => {
                const element = document.createElement("div");
                element.className = "piece " + (piece.side === "RED" ? "red" : "black");
                element.textContent = piece.symbol;
                const key = piece.row + "," + piece.col;

                if (movedKey === key) {
                    this._position(element, lastMove.from.row, lastMove.from.col);
                } else {
                    this._position(element, piece.row, piece.col);
                }

                if (selected && selected.row === piece.row && selected.col === piece.col) {
                    element.classList.add("selected");
                }
                if (state.inCheck && piece.type === "GENERAL" && piece.side === state.turn) {
                    element.classList.add("in-check");
                }
                if (lastMove && lastMove.to.row === piece.row && lastMove.to.col === piece.col) {
                    element.classList.add("just-moved");
                }
                this.piecesEl.appendChild(element);

                if (movedKey === key) {
                    // 强制一次布局，确保 left/top 从起点开始过渡
                    void element.offsetWidth;
                    window.requestAnimationFrame(() => {
                        this._position(element, lastMove.to.row, lastMove.to.col);
                    });
                }
            });

            this.prevState = state;
        }

        _addGhost(piece) {
            const ghost = document.createElement("div");
            ghost.className = "piece ghost " + (piece.side === "RED" ? "red" : "black");
            ghost.textContent = piece.symbol;
            this._position(ghost, piece.row, piece.col);
            this.piecesEl.appendChild(ghost);
            window.setTimeout(() => {
                if (ghost.parentNode) {
                    ghost.parentNode.removeChild(ghost);
                }
            }, 340);
        }

        _addMarker(kind, row, col) {
            const marker = document.createElement("span");
            marker.className = "marker " + kind;
            this._position(marker, row, col);
            this.markersEl.appendChild(marker);
        }

        _position(element, row, col) {
            const x = ORIGIN_X + col * STEP;
            const y = ORIGIN_Y + row * STEP;
            element.style.left = (x / VIEW_WIDTH * 100) + "%";
            element.style.top = (y / VIEW_HEIGHT * 100) + "%";
        }

        _handlePointer(event) {
            const rect = this.boardEl.getBoundingClientRect();
            const x = (event.clientX - rect.left) / rect.width * VIEW_WIDTH;
            const y = (event.clientY - rect.top) / rect.height * VIEW_HEIGHT;
            const col = Math.round((x - ORIGIN_X) / STEP);
            const row = Math.round((y - ORIGIN_Y) / STEP);
            if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
                return;
            }
            if (Math.abs(x - (ORIGIN_X + col * STEP)) > STEP * 0.46
                || Math.abs(y - (ORIGIN_Y + row * STEP)) > STEP * 0.46) {
                return;
            }
            if (typeof this.onCell === "function") {
                this.onCell({ row: row, col: col });
            }
        }
    }

    window.XiangqiBoard = XiangqiBoard;
})();
