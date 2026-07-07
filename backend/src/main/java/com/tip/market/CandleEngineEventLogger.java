package com.tip.market;

import com.tip.market.event.CandleClosedEvent;
import com.tip.market.event.CandleUpdatedEvent;
import com.tip.market.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Component
public class CandleEngineEventLogger {

    private static final Logger log = LoggerFactory.getLogger(CandleEngineEventLogger.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(CandleBoundaryUtils.NSE_ZONE);

    private long updateLogCount;

    @EventListener
    public void onCandleClosed(CandleClosedEvent event) {
        Candle candle = event.candle();
        log.info("Candle CLOSED [{} {}] {} O={} H={} L={} C={} V={}",
                event.instrumentKey(),
                event.timeframe(),
                formatTime(candle.time()),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume());
    }

    @EventListener
    public void onCandleUpdated(CandleUpdatedEvent event) {
        if (event.isFinal()) {
            return;
        }

        updateLogCount++;
        if (updateLogCount <= 3 || updateLogCount % 100 == 0) {
            Candle candle = event.candle();
            log.debug("Candle UPDATE [{} {}] {} C={} V={}",
                    event.instrumentKey(),
                    event.timeframe(),
                    formatTime(candle.time()),
                    candle.close(),
                    candle.volume());
        }
    }

    private String formatTime(long epochSecond) {
        return TIME_FORMAT.format(Instant.ofEpochSecond(epochSecond));
    }
}