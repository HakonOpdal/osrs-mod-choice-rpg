package com.pathlocked.points;

/**
 * Escalating cost of each choice event. Constants are the initial tune from
 * the design doc (first draft ~20 minutes in at early-game earn rates) and
 * are expected to be adjusted after playtesting.
 */
public final class ThresholdCurve
{
	private static final double BASE_COST = 250d;
	private static final double GROWTH_EXPONENT = 1.35d;
	private static final long ROUNDING_STEP = 25L;

	private ThresholdCurve()
	{
	}

	public static long cost(int choiceIndex)
	{
		double raw = BASE_COST * Math.pow(choiceIndex + 1, GROWTH_EXPONENT);
		long rounded = Math.round(raw / ROUNDING_STEP) * ROUNDING_STEP;
		return Math.max(ROUNDING_STEP, rounded);
	}
}
