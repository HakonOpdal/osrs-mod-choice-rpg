package com.pathlocked.unlocks;

import com.pathlocked.draft.DraftCategory;
import com.pathlocked.draft.DraftOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
		/**
		 * Frozen at roll time so a reroll swaps cards within the same kind of
		 * draft even if the rotation/override state has since changed. Null on
		 * profiles saved before this field existed; normalize() backfills it.
		 */
		public DraftCategory category;
		public List<DraftOption> offers = new ArrayList<>();
		public int rerollsUsed;
		/**
		 * True when this draft's category came from
		 * {@link #nextCategoryOverride}, so the override is cleared exactly when
		 * the draft it produced is picked — not when an older pending draft
		 * (rolled before the override existed) resolves.
		 */
		public boolean consumedOverride;
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
	/**
	 * Lowercase item-tag names.
	 */
	public Set<String> unlockedTags = new LinkedHashSet<>();
	/**
	 * Lowercase skill names (RuneLite Skill.getName() form).
	 */
	public Set<String> unlockedSkills = new LinkedHashSet<>();
	/**
	 * Lowercase skill name -> XP gained while that skill was locked. Void XP
	 * earns no points; it exists so the honor-mode violation is visible.
	 */
	public Map<String, Long> voidXpBySkill = new LinkedHashMap<>();
	/**
	 * When set, the next NEW draft uses this category instead of the rotation —
	 * how new and migrated profiles get their instant skill keystone. Cleared
	 * when the draft it produced is picked.
	 */
	public DraftCategory nextCategoryOverride;
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

	public boolean isTagUnlocked(String name)
	{
		return name != null && unlockedTags.contains(name.toLowerCase());
	}

	public boolean isSkillUnlocked(String name)
	{
		return name != null && unlockedSkills.contains(name.toLowerCase());
	}

	public long voidXpTotal()
	{
		long total = 0;
		for (long xp : voidXpBySkill.values())
		{
			total += xp;
		}
		return total;
	}
}
