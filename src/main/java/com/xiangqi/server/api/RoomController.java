package com.xiangqi.server.api;

import com.xiangqi.server.api.dto.MoveRequest;
import com.xiangqi.server.api.dto.RoomSeatResponse;
import com.xiangqi.server.api.dto.RoomView;
import com.xiangqi.server.service.GameService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 联网房间接口。
 * <p>
 * - 创建房间返回红方令牌；
 * - 加入房间时，空余黑方席位会签发黑方令牌，否则以观战者身份返回；
 * - 对局状态公开可读，但落子/认输必须携带本方令牌，且只能在本方回合操作。
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final GameService gameService;

    public RoomController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomSeatResponse create() {
        return gameService.createRoom();
    }

    @PostMapping("/{code}/join")
    public RoomSeatResponse join(
            @PathVariable String code,
            @RequestHeader(value = "X-Game-Token", required = false) String token) {
        return gameService.joinRoom(code, token);
    }

    @GetMapping("/{code}")
    public RoomView room(@PathVariable String code) {
        return gameService.room(code);
    }

    @PostMapping("/{code}/moves")
    public RoomView move(
            @PathVariable String code,
            @RequestHeader(value = "X-Game-Token", required = false) String token,
            @Valid @RequestBody MoveRequest request) {
        return gameService.moveRoom(code, token, request);
    }

    @PostMapping("/{code}/resign")
    public RoomView resign(
            @PathVariable String code,
            @RequestHeader(value = "X-Game-Token", required = false) String token) {
        return gameService.resignRoom(code, token);
    }

    /**
     * SSE 实时同步：观战者与玩家都可通过 EventSource 订阅。
     */
    @GetMapping(value = "/{code}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String code) {
        return gameService.subscribeRoom(code);
    }
}
