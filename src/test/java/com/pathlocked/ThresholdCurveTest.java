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
		assertEquals(250, ThresholdCurve.cost(0));
	}

	@Test
	public void costsStrictlyIncrease()
	{
		for (int i = 0; i < 50; i++)
		{
			assertTrue("cost(" + (i + 1) + ") should exceed cost(" + i + ")",
				ThresholdCurve.cost(i + 1) > ThresholdCurve.cost(i));
		}
	}

	@Test
	public void costsAreRoundNumbers()
	{
		for (int i = 0; i < 50; i++)
		{
			assertEquals(0, ThresholdCurve.cost(i) % 25);
		}
	}
}
