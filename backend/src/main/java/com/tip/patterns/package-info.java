/**
 * Pattern intelligence.
 *
 * <p><b>Pure domain (no Spring/DB):</b> {@code com.tip.patterns.breakout.*}, {@code com.tip.patterns.model.*}.
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
 */
package com.tip.patterns;
