package com.xiangqi.server.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 走子请求。坐标由服务端再次校验范围与合法性，绝不信任前端。
 */
public record MoveRequest(
        @NotNull @Valid Square from,
        @NotNull @Valid Square to
) {

    public record Square(
            @Min(0) @Max(9) int row,
            @Min(0) @Max(8) int col
    ) {
    }
}
