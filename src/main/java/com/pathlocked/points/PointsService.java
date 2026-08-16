package com.pathlocked.points;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;

/**
 * Converts XP gains and kills into points. Mirrors the OSRS TCG economy:
 * non-combat XP only (kills are credited separately by combat level, so
 * combat XP would double-dip), at 1 point per 10 XP.
 */
public class PointsService
{
	public static final int XP_PER_POINT = 10;

	private static final Set<Skill> COMBAT_SKILLS = EnumSet.of(
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS,
		Skill.RANGED, Skill.MAGIC, Skill.PRAYER);

	private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);

	/**
	 * @param totalXp the new total XP in the skill
	 * @return the raw XP delta (0 for the first sighting of a skill, which
	 * primes the baseline). The caller decides what the delta is worth: combat
	 * skills earn no points ({@link #isCombatSkill}), locked skills route to
	 * void XP, and fractional-point carry-over is persisted with the profile.
	 */
	public int xpDelta(Skill skill, int totalXp)
	{
		Integer previous = lastXp.put(skill, totalXp);
		if (previous == null || totalXp <= previous)
		{
			return 0;
		}
		return totalXp - previous;
	}

	/**
	 * Combat skills never earn XP points — kills are credited separately by
	 * combat level, so combat XP would double-dip.
	 */
	public static boolean isCombatSkill(Skill skill)
	{
		return COMBAT_SKILLS.contains(skill);
	}

	/**
	 * Sets a skill's XP baseline without awarding anything — used when the
	 * plugin starts mid-session, after the login StatChanged burst is gone.
	 */
	public void prime(Skill skill, int totalXp)
	{
		lastXp.put(skill, totalXp);
	}

	public long pointsForKill(int combatLevel)
	{
		return Math.max(1, combatLevel);
	}

	/**
	 * Clears the XP cache so the next StatChanged per skill primes instead of awarding.
	 */
	public void reset()
	{
		lastXp.clear();
	}
}
