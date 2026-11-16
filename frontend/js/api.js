(function () {
    "use strict";

    class ApiError extends Error {
        constructor(message, status, code) {
            super(message);
            this.name = "ApiError";
            this.status = status;
            this.code = code;
        }
    }

    async function request(path, options) {
        const config = options || {};
        const headers = {};
        if (config.body !== undefined) {
            headers["Content-Type"] = "application/json";
        }
        if (config.token) {
            headers["X-Game-Token"] = config.token;
        }

        let response;
        try {
            response = await fetch(path, {
                method: config.method || "GET",
                headers,
                body: config.body === undefined ? undefined : JSON.stringify(config.body)
            });
        } catch (error) {
            throw new ApiError("无法连接服务器，请确认后端已启动", 0, "NETWORK_ERROR");
        }

        const text = await response.text();
        let data = null;
        if (text) {
            try {
                data = JSON.parse(text);
            } catch (parseError) {
                data = { message: text };
            }
        }

        if (!response.ok) {
            const message = (data && data.message) || ("请求失败（" + response.status + "）");
            throw new ApiError(message, response.status, data && data.code);
        }
        return data;
    }

    function gamePath(gameId, suffix) {
        return "/api/games/" + encodeURIComponent(gameId) + (suffix || "");
    }

    function roomPath(code, suffix) {
        return "/api/rooms/" + encodeURIComponent(code) + (suffix || "");
    }

    window.ApiError = ApiError;
    window.XiangqiApi = {
        createGame: function () {
            return request("/api/games", { method: "POST" });
        },
        getGame: function (gameId, token) {
            return request(gamePath(gameId), { token: token });
        },
        move: function (gameId, token, from, to) {
            return request(gamePath(gameId, "/moves"), {
                method: "POST",
                token: token,
                body: {
                    from: { row: from.row, col: from.col },
                    to: { row: to.row, col: to.col }
                }
            });
        },
        undo: function (gameId, token) {
            return request(gamePath(gameId, "/undo"), { method: "POST", token: token });
        },
        resign: function (gameId, token, side) {
            return request(gamePath(gameId, "/resign"), {
                method: "POST",
                token: token,
                body: { side: side }
            });
        },
        aiMove: function (gameId, token, depth) {
            return request(gamePath(gameId, "/ai-move?depth=" + encodeURIComponent(depth)), {
                method: "POST",
                token: token
            });
        },

        createRoom: function () {
            return request("/api/rooms", { method: "POST" });
        },
        joinRoom: function (code, token) {
            return request(roomPath(code, "/join"), {
                method: "POST",
                token: token || null
            });
        },
        getRoom: function (code) {
            return request(roomPath(code), {});
        },
        roomMove: function (code, token, from, to) {
            return request(roomPath(code, "/moves"), {
                method: "POST",
                token: token,
                body: {
                    from: { row: from.row, col: from.col },
                    to: { row: to.row, col: to.col }
                }
            });
        },
        roomResign: function (code, token) {
            return request(roomPath(code, "/resign"), { method: "POST", token: token });
        },
        roomEventsUrl: function (code) {
            return roomPath(code, "/events");
        },

        records: function (limit) {
            return request("/api/records?limit=" + encodeURIComponent(limit || 12), {});
        },
        replay: function (gameId) {
            return request("/api/records/" + encodeURIComponent(gameId), {});
        }
    };
})();
