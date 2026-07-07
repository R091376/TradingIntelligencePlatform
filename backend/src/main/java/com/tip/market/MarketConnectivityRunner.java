package com.tip.market;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MarketConnectivityRunner implements ApplicationRunner {

    private final MarketBootstrapService marketBootstrapService;

    public MarketConnectivityRunner(MarketBootstrapService marketBootstrapService) {
        this.marketBootstrapService = marketBootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        marketBootstrapService.recoverSession();
    }
}