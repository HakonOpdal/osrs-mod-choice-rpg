package com.pathlocked;

import com.pathlocked.points.PointsService;
import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PointsServiceTest
{
	@Test
	public void firstSightingPrimesAndAwardsNothing()
	{
		PointsService service = new PointsService();
		assertEquals(0, service.xpDelta(Skill.FISHING, 5000));
		assertEquals(150, service.xpDelta(Skill.FISHING, 5150));
	}

	@Test
	public void regressionOrRepeatAwardsNothing()
	{
		PointsService service = new PointsService();
		service.prime(Skill.MINING, 1000);
		assertEquals(0, service.xpDelta(Skill.MINING, 1000));
		assertEquals(0, service.xpDelta(Skill.MINING, 900));
	}

	@Test
	public void combatSkillsReportRawDeltaButAreFlaggedCombat()
	{
		// The caller (plugin) decides what a delta is worth; the service must
		// still report raw combat deltas so locked combat skills can accrue
		// void XP.
		PointsService service = new PointsService();
		service.prime(Skill.DEFENCE, 0);
		assertEquals(40, service.xpDelta(Skill.DEFENCE, 40));
		assertTrue(PointsService.isCombatSkill(Skill.DEFENCE));
		assertTrue(PointsService.isCombatSkill(Skill.MAGIC));
		assertFalse(PointsService.isCombatSkill(Skill.FISHING));
	}

	@Test
	public void resetForcesRepriming()
	{
		PointsService service = new PointsService();
		service.prime(Skill.COOKING, 2000);
		service.reset();
		assertEquals("First delta after reset must prime, not award",
			0, service.xpDelta(Skill.COOKING, 2500));
	}
}
