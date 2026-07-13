package com.tip.patterns;

import com.tip.api.dto.PatternEventMessage;
import com.tip.api.websocket.LiveWebSocketHandler;
import com.tip.config.PatternProperties;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.PatternStageEvent;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Filters stages and broadcasts {@code pattern_event} over /ws/live.
 */
@Component
public class PatternEventPublisher {

    private final LiveWebSocketHandler webSocketHandler;
    private final PatternProperties patternProperties;

    public PatternEventPublisher(
            LiveWebSocketHandler webSocketHandler,
            PatternProperties patternProperties
    ) {
        this.webSocketHandler = webSocketHandler;
        this.patternProperties = patternProperties;
    }

    public void publish(ActivePattern pattern, List<PatternStageEvent> events) {
        if (pattern == null || events == null || events.isEmpty()) {
            return;
        }
        for (PatternStageEvent ev : events) {
            String stage = ev.stage().wireValue();
            if (!patternProperties.getWs().shouldBroadcast(stage)) {
                continue;
            }
            PatternEventMessage msg = new PatternEventMessage(
                    "pattern_event",
                    pattern.symbolId(),
                    pattern.timeframe(),
                    pattern.patternType().wireValue(),
                    stage,
                    pattern.referenceLevel(),
                    ev.priceAtEvent(),
                    ev.candleTime(),
                    pattern.id().toString(),
                    pattern.direction().wireValue(),
                    pattern.status().wireValue(),
                    pattern.entryPrice(),
                    pattern.stopLevel(),
                    pattern.targetLevel(),
                    pattern.confirmationModeUsed().wireValue()
            );
            webSocketHandler.broadcastAll(msg);
        }
    }
}
