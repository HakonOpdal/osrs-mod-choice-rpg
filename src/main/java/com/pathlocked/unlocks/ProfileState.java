package com.pathlocked.unlocks;

import com.pathlocked.draft.DraftOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The full persisted state of one account's run. Serialized to JSON by
 * {@link ProfileManager}; keep fields Gson-friendly.
 */
public class ProfileState
{
	public static class PendingDraft
	{
		public int choiceIndex;
		public List<DraftOption> offers = new ArrayList<>();
		public int rerollsUsed;
	}

	public static class ChoiceRecord
	{
		public int choiceIndex;
		public List<DraftOption> offers = new ArrayList<>();
		public DraftOption picked;
		public int rerollsUsed;
	}

	public long seed;
	public long totalPoints;
	public long spentPoints;
	public int choiceIndex;
	public Set<Integer> unlockedRegions = new LinkedHashSet<>();
	/**
	 * Lowercase monster names.
	 */
	public Set<String> unlockedMonsters = new LinkedHashSet<>();
	public PendingDraft pendingDraft;
	public List<ChoiceRecord> history = new ArrayList<>();
	public long violationTicks;
	public long illegalKills;
	/**
	 * Non-combat XP not yet converted to a point (1 point per 10 XP);
	 * persisted so short sessions never lose fractional credit.
	 */
	public int xpRemainder;

	public long availablePoints()
	{
		return totalPoints - spentPoints;
	}

	public boolean isRegionUnlocked(int regionId)
	{
		return unlockedRegions.contains(regionId);
	}

	public boolean isMonsterUnlocked(String name)
	{
		return name != null && unlockedMonsters.contains(name.toLowerCase());
	}
}
