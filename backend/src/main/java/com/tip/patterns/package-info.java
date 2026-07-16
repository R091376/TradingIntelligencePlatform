/**
 * Pattern intelligence.
 *
 * <p><b>Pure domain (no Spring/DB):</b> {@code breakout.*}, {@code breakdown.*},
 * {@code pinbar.*}, {@code model.*}.
 * <p><b>App orchestration (Spring):</b> {@link com.tip.patterns.PatternEvaluator}, reconcilers, store, WS publisher.
 *
 * <p>Docs: {@code docs/patterns/}. Pure domain depends on {@code com.tip.indicators} only.
 * Persistence goes through {@code com.tip.journal} from orchestrators only.
 *
 * <p>Breakout / Breakdown: Donchian reference lifecycle (full stages).
 * <p>Pin-bar (Hammer / Shooting Star): Nison geometry + short lifecycle
 * ({@link com.tip.patterns.pinbar.PinBarBarEvaluator}).
 *
 * <p>Shared terminal expire: {@link com.tip.patterns.PatternLifecycleSupport#expire}.
 */
package com.tip.patterns;
