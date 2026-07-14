/**
 * Pattern intelligence.
 *
 * <p><b>Pure domain (no Spring/DB):</b> {@code com.tip.patterns.breakout.*},
 * {@code com.tip.patterns.breakdown.*}, {@code com.tip.patterns.model.*}.
 * <p><b>App orchestration (Spring):</b> {@link com.tip.patterns.PatternEvaluator}, reconcilers, store, WS publisher.
 *
 * <p>Docs: {@code docs/patterns/}. Pure domain depends on {@code com.tip.indicators} only.
 * Persistence goes through {@code com.tip.journal} from orchestrators only.
 *
 * <p>Breakout entry points:
 * <ul>
 *   <li>{@link com.tip.patterns.breakout.BreakoutDetector#tryDetect}</li>
 *   <li>{@link com.tip.patterns.breakout.BreakoutLifecycle#onCandle}</li>
 *   <li>{@link com.tip.patterns.breakout.BreakoutBarEvaluator#evaluate} — one closed bar</li>
 * </ul>
 *
 * <p>Breakdown entry points (short mirror):
 * <ul>
 *   <li>{@link com.tip.patterns.breakdown.BreakdownDetector#tryDetect}</li>
 *   <li>{@link com.tip.patterns.breakdown.BreakdownLifecycle#onCandle}</li>
 *   <li>{@link com.tip.patterns.breakdown.BreakdownBarEvaluator#evaluate} — one closed bar</li>
 * </ul>
 *
 * <p>Shared terminal expire: {@link com.tip.patterns.PatternLifecycleSupport#expire}.
 */
package com.tip.patterns;
