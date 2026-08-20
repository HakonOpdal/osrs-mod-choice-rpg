package com.pathlocked.points;

/**
 * Escalating cost of each choice event — the "paced curve" (v2, replaces the
 * v0.1 power law after the 2026-08 balancing research;
 * see docs/balancing-research.html).
 *
 * <p>Cost is the product of two explicit models, so the constants below are
 * directly the game-feel knobs:
 * <ul>
 * <li>{@link #EARLY_POINTS_PER_HOUR}/{@link #LATE_POINTS_PER_HOUR} — how fast
 * a player realistically earns points (researched F2P rates: ~1,000/h on a
 * fresh account rising to ~8,000/h late game), ramped by
 * {@link #RATE_RAMP_EXPONENT};</li>
 * <li>{@link #FIRST_DRAFT_HOURS}/{@link #LAST_DRAFT_HOURS} — how long a draft
 * SHOULD take at that stage: 21 minutes for the first, 2.5 hours for the
 * last.</li>
 * </ul>
 * Progress along the run is normalized by {@link #PACING_SPAN} — the LAST
 * draft index at the v0.2 content scale (160 unlockables − 18 starter grants
 * = 142 drafts, indices 0..141); {@code ContentDataTest} pins the constant to
 * the content so a v0.3 content change forces a conscious retune. Past the
 * span the cost plateaus at {@link #maxCost()}, so added content never
 * reintroduces a late-game wall. Full-run total = 1,161,000 points ≈ 11.6M
 * XP-equivalent ≈ 200 hours to 100% completion.
 */
public final class ThresholdCurve
{
	/**
	 * The last draft index of a full run at the v0.2 content scale; the
	 * pacing ramp is normalized against it.
	 */
	public static final int PACING_SPAN = 141;

	/** Expected points/hour on a fresh account vs. deep into a run. */
	private static final double EARLY_POINTS_PER_HOUR = 1_000d;
	private static final double LATE_POINTS_PER_HOUR = 8_000d;
	/** Sub-linear ramp: earn rate grows slower than draft count early on. */
	private static final double RATE_RAMP_EXPONENT = 0.8d;

	/** Target wall-clock hours between drafts, first draft vs. last. */
	private static final double FIRST_DRAFT_HOURS = 0.35d;
	private static final double LAST_DRAFT_HOURS = 2.5d;

	private static final long ROUNDING_STEP = 25L;

	private ThresholdCurve()
	{
	}

	public static long cost(int choiceIndex)
	{
		double progress = Math.min(1d, Math.max(0, choiceIndex) / (double) PACING_SPAN);
		double pointsPerHour = EARLY_POINTS_PER_HOUR
			+ (LATE_POINTS_PER_HOUR - EARLY_POINTS_PER_HOUR) * Math.pow(progress, RATE_RAMP_EXPONENT);
		double targetHours = FIRST_DRAFT_HOURS + (LAST_DRAFT_HOURS - FIRST_DRAFT_HOURS) * progress;
		long rounded = Math.round(pointsPerHour * targetHours / ROUNDING_STEP) * ROUNDING_STEP;
		return Math.max(ROUNDING_STEP, rounded);
	}

	/**
	 * The plateau cost every draft at or past {@link #PACING_SPAN} settles on
	 * (late rate × last interval).
	 */
	public static long maxCost()
	{
		return cost(PACING_SPAN);
	}
}
