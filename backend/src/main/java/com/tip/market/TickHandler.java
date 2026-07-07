package com.tip.market;

import com.tip.market.model.Tick;

@FunctionalInterface
public interface TickHandler {
    void onTick(Tick tick);
}