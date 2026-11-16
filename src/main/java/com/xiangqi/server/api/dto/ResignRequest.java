package com.xiangqi.server.api.dto;

import com.xiangqi.core.Side;
import jakarta.validation.constraints.NotNull;

public record ResignRequest(@NotNull Side side) {
}
