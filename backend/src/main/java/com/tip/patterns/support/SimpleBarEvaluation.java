package com.tip.patterns.support;

import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.PatternStageEvent;

import java.util.ArrayList;
import java.util.List;

/** Generic advance+detect evaluation result (same shape as pin-bar / breakout). */
public final class SimpleBarEvaluation {

    private final List<ActivePattern> advanced;
    private final List<ActivePattern> newlyDetected;
    private final List<PatternStageEvent> events;
    private final List<ActivePattern> stillOpen;
    private final List<ActivePattern> closedThisBar;

    public SimpleBarEvaluation(
            List<ActivePattern> advanced,
            List<ActivePattern> newlyDetected,
            List<PatternStageEvent> events,
            List<ActivePattern> stillOpen,
            List<ActivePattern> closedThisBar
    ) {
        this.advanced = List.copyOf(advanced);
        this.newlyDetected = List.copyOf(newlyDetected);
        this.events = List.copyOf(events);
        this.stillOpen = List.copyOf(stillOpen);
        this.closedThisBar = List.copyOf(closedThisBar);
    }

    public List<ActivePattern> advanced() {
        return advanced;
    }

    public List<ActivePattern> newlyDetected() {
        return newlyDetected;
    }

    public List<PatternStageEvent> events() {
        return events;
    }

    public List<ActivePattern> stillOpen() {
        return stillOpen;
    }

    public List<ActivePattern> closedThisBar() {
        return closedThisBar;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<ActivePattern> advanced = new ArrayList<>();
        private final List<ActivePattern> newlyDetected = new ArrayList<>();
        private final List<PatternStageEvent> events = new ArrayList<>();
        private final List<ActivePattern> stillOpen = new ArrayList<>();
        private final List<ActivePattern> closedThisBar = new ArrayList<>();

        public Builder addAdvanced(ActivePattern p) {
            advanced.add(p);
            return this;
        }

        public Builder addNew(ActivePattern p) {
            newlyDetected.add(p);
            return this;
        }

        public Builder addEvents(List<PatternStageEvent> e) {
            events.addAll(e);
            return this;
        }

        public Builder addEvent(PatternStageEvent e) {
            events.add(e);
            return this;
        }

        public Builder trackOpenOrClosed(ActivePattern p) {
            if (p.isTerminal()) {
                closedThisBar.add(p);
            } else {
                stillOpen.add(p);
            }
            return this;
        }

        public SimpleBarEvaluation build() {
            return new SimpleBarEvaluation(advanced, newlyDetected, events, stillOpen, closedThisBar);
        }
    }
}
