package com.tip.patterns;

import com.tip.patterns.model.ActivePattern;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-process open pattern instances keyed by {@code symbolId|timeframe}.
 */
@Component
public class ActiveInstanceStore {

    private final Map<String, List<ActivePattern>> byKey = new ConcurrentHashMap<>();

    private static String key(String symbolId, String timeframe) {
        return symbolId + "|" + timeframe;
    }

    public List<ActivePattern> getOpen(String symbolId, String timeframe) {
        List<ActivePattern> list = byKey.get(key(symbolId, timeframe));
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        synchronized (list) {
            return list.stream().filter(p -> !p.isTerminal()).map(p -> p).collect(Collectors.toCollection(ArrayList::new));
        }
    }

    public void replaceOpen(String symbolId, String timeframe, List<ActivePattern> open) {
        String k = key(symbolId, timeframe);
        List<ActivePattern> copy = new ArrayList<>();
        if (open != null) {
            for (ActivePattern p : open) {
                if (p != null && !p.isTerminal()) {
                    copy.add(p);
                }
            }
        }
        byKey.put(k, copy);
    }

    public void put(ActivePattern pattern) {
        if (pattern == null || pattern.isTerminal()) {
            return;
        }
        String k = key(pattern.symbolId(), pattern.timeframe());
        byKey.compute(k, (ignored, list) -> {
            List<ActivePattern> next = list == null ? new ArrayList<>() : new ArrayList<>(list);
            next.removeIf(p -> p.id().equals(pattern.id()));
            next.add(pattern);
            return next;
        });
    }

    public void remove(ActivePattern pattern) {
        if (pattern == null) {
            return;
        }
        String k = key(pattern.symbolId(), pattern.timeframe());
        byKey.computeIfPresent(k, (ignored, list) -> {
            List<ActivePattern> next = new ArrayList<>(list);
            next.removeIf(p -> p.id().equals(pattern.id()));
            return next;
        });
    }

    public void removeAllForSymbol(String symbolId) {
        if (symbolId == null) {
            return;
        }
        String prefix = symbolId + "|";
        byKey.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public List<ActivePattern> snapshotAllOpen() {
        List<ActivePattern> all = new ArrayList<>();
        for (List<ActivePattern> list : byKey.values()) {
            synchronized (list) {
                for (ActivePattern p : list) {
                    if (!p.isTerminal()) {
                        all.add(p);
                    }
                }
            }
        }
        return all;
    }

    /** Open instances for one symbol across all timeframes (live MFE/MAE intact). */
    public List<ActivePattern> getOpenForSymbol(String symbolId) {
        if (symbolId == null) {
            return List.of();
        }
        List<ActivePattern> out = new ArrayList<>();
        for (ActivePattern p : snapshotAllOpen()) {
            if (symbolId.equals(p.symbolId())) {
                out.add(p);
            }
        }
        return out;
    }

    public void clear() {
        byKey.clear();
    }
}
