package com.pathlocked;

import com.pathlocked.points.ThresholdCurve;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ThresholdCurveTest
{
	@Test
	public void firstChoiceCostsTheDocumentedBase()
	{
		// 1,000 pts/h expected on a fresh account × 0.35 h target = 350.
		assertEquals(350, ThresholdCurve.cost(0));
	}

	@Test
	public void costsStrictlyIncreaseUntilThePlateau()
	{
		for (int i = 0; i < ThresholdCurve.PACING_SPAN; i++)
		{
			assertTrue("cost(" + (i + 1) + ") should exceed cost(" + i + ")",
				ThresholdCurve.cost(i + 1) > ThresholdCurve.cost(i));
		}
	}

	@Test
	public void costPlateausAtMaxCostBeyondThePacingSpan()
	{
		// The cap that keeps added content from reintroducing a late-game wall:
		// the final in-run draft hits it exactly, and it never grows past it.
		assertEquals(ThresholdCurve.maxCost(), ThresholdCurve.cost(ThresholdCurve.PACING_SPAN));
		assertEquals(ThresholdCurve.maxCost(), ThresholdCurve.cost(ThresholdCurve.PACING_SPAN + 1));
		assertEquals(ThresholdCurve.maxCost(), ThresholdCurve.cost(500));
		assertTrue("plateau boundary must be reachable in-run",
			ThresholdCurve.cost(ThresholdCurve.PACING_SPAN - 1) <= ThresholdCurve.maxCost());
	}

	@Test
	public void costsAreRoundNumbers()
	{
		for (int i = 0; i <= ThresholdCurve.PACING_SPAN + 10; i++)
		{
			assertEquals(0, ThresholdCurve.cost(i) % 25);
		}
	}
}
