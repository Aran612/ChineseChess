package com.xiangqi.server.api;

import com.xiangqi.server.api.dto.RecordSummary;
import com.xiangqi.server.api.dto.ReplayView;
import com.xiangqi.server.service.GameService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 历史对局与复盘接口。只暴露棋谱与局面数据，不包含任何令牌信息。
 */
@RestController
@RequestMapping("/api/records")
public class RecordsController {

    private final GameService gameService;

    public RecordsController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping
    public List<RecordSummary> list(@RequestParam(defaultValue = "20") int limit) {
        return gameService.records(limit);
    }

    @GetMapping("/{gameId}")
    public ReplayView replay(@PathVariable String gameId) {
        return gameService.replay(gameId);
    }
}
