package com.xiangqi.server.api;

import com.xiangqi.server.api.dto.CreateGameResponse;
import com.xiangqi.server.api.dto.GameView;
import com.xiangqi.server.api.dto.MoveRequest;
import com.xiangqi.server.api.dto.ResignRequest;
import com.xiangqi.server.service.GameService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对局 HTTP 接口。除新建对局外，所有请求都必须携带 X-Game-Token。
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateGameResponse create() {
        return gameService.create();
    }

    @GetMapping("/{gameId}")
    public GameView get(
            @PathVariable String gameId,
            @RequestHeader(value = "X-Game-Token", required = false) String token) {
        return gameService.view(gameId, token);
    }

    @PostMapping("/{gameId}/moves")
    public GameView move(
            @PathVariable String gameId,
            @RequestHeader(value = "X-Game-Token", required = false) String token,
            @Valid @RequestBody MoveRequest request) {
        return gameService.move(gameId, token, request);
    }

    @PostMapping("/{gameId}/undo")
    public GameView undo(
            @PathVariable String gameId,
            @RequestHeader(value = "X-Game-Token", required = false) String token) {
        return gameService.undo(gameId, token);
    }

    @PostMapping("/{gameId}/resign")
    public GameView resign(
            @PathVariable String gameId,
            @RequestHeader(value = "X-Game-Token", required = false) String token,
            @Valid @RequestBody ResignRequest request) {
        return gameService.resign(gameId, token, request.side());
    }

    @PostMapping("/{gameId}/ai-move")
    public GameView aiMove(
            @PathVariable String gameId,
            @RequestHeader(value = "X-Game-Token", required = false) String token,
            @RequestParam(defaultValue = "3") int depth) {
        return gameService.aiMove(gameId, token, depth);
    }
}
