package com.tip.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SubscribeMessage(
        String type,
        String symbolId,
        String timeframe
) {
}