package com.pathlocked.draft;

import com.pathlocked.content.ContentRepository;
import com.pathlocked.content.ItemTagDef;
import com.pathlocked.content.MonsterDef;
import com.pathlocked.content.RegionDef;
import com.pathlocked.points.ThresholdCurve;
import com.pathlocked.unlocks.ProfileState;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.LongSupplier;

/**
 * Rolls and applies draft choices. Fully deterministic from
 * (seed, choiceIndex, rerollCount) so offers survive relogs and seeded runs
 * are shareable. Candidates come only from the "frontier" — regions adjacent
 * to an unlocked region, monsters with an unlocked home region — which is
 * what prevents softlocks.
 */
public class DraftService
{
	public static final int MAX_REROLLS = 1;
	public static final int OFFERS_PER_CATEGORY = 3;
	public static final int FREE_PICK_OFFERS = 6;
	private static final int FREE_PICK_INTERVAL = 5;
	private static final int KEYSTONE_INTERVAL = 10;

	private final ContentRepository content;
	private final LongSupplier clock;

	public DraftService(ContentRepository content)
	{
		this(content, System::currentTimeMillis);
	}

	/**
	 * @param clock epoch-millis source for pick timestamps, injectable so
	 * tests can freeze time; offers themselves stay purely seed-derived
	 */
	public DraftService(ContentRepository content, LongSupplier clock)
	{
		this.content = content;
		this.clock = clock;
	}

	/**
	 * Starts a draft if the player can afford the next threshold and there is
	 * anything left to offer.
	 *
	 * @return true if a new pending draft was created
	 */
	public boolean maybeStartDraft(ProfileState state)
	{
		if (state.pendingDraft != null)
		{
			return false;
		}
		if (state.availablePoints() < ThresholdCurve.cost(state.choiceIndex))
		{
			return false;
		}
		DraftCategory category = categoryFor(state);
		List<DraftOption> offers = rollOffers(state, category, 0);
		if (offers.isEmpty())
		{
			return false;
		}
		ProfileState.PendingDraft draft = new ProfileState.PendingDraft();
		draft.choiceIndex = state.choiceIndex;
		draft.category = category;
		draft.offers = offers;
		draft.rerollsUsed = 0;
		draft.consumedOverride = state.nextCategoryOverride != null;
		state.pendingDraft = draft;
		return true;
	}

	public boolean reroll(ProfileState state)
	{
		ProfileState.PendingDraft draft = state.pendingDraft;
		if (draft == null || draft.rerollsUsed >= MAX_REROLLS)
		{
			return false;
		}
		draft.rerollsUsed++;
		// The category was frozen when the draft was rolled: a reroll swaps the
		// cards, never the kind of draft — even if an override appeared since
		// (migration) or the rotation would now say otherwise.
		DraftCategory category = draft.category != null ? draft.category : categoryFor(state);
		draft.offers = rollOffers(state, category, draft.rerollsUsed);
		return true;
	}

	/**
	 * Applies the chosen option: unlocks it, spends the threshold cost,
	 * records history, and advances the choice index.
	 */
	public DraftOption pick(ProfileState state, int optionIndex)
	{
		ProfileState.PendingDraft draft = state.pendingDraft;
		if (draft == null || optionIndex < 0 || optionIndex >= draft.offers.size())
		{
			return null;
		}
		DraftOption picked = draft.offers.get(optionIndex);
		if (picked.getCategory() == null)
		{
			// Malformed persisted offer; normalize() drops such drafts at load,
			// this is the in-session backstop against an NPE on the switch.
			return null;
		}
		switch (picked.getCategory())
		{
			case REGION:
				state.unlockedRegions.add(picked.getRegionId());
				break;
			case MONSTER:
				state.unlockedMonsters.add(picked.getName().toLowerCase());
				break;
			case ITEM:
				state.unlockedTags.add(picked.getName().toLowerCase());
				break;
			case SKILL:
				state.unlockedSkills.add(picked.getName().toLowerCase());
				break;
			default:
				return null;
		}
		if (draft.consumedOverride)
		{
			state.nextCategoryOverride = null;
		}

		// Never spend into a negative balance: a draft can only have been
		// offered as affordable, but a curve retune between roll and pick
		// (or a hand-edited profile) may have shrunk the bank since.
		state.spentPoints += Math.min(ThresholdCurve.cost(state.choiceIndex),
			Math.max(0, state.availablePoints()));

		ProfileState.ChoiceRecord record = new ProfileState.ChoiceRecord();
		record.choiceIndex = state.choiceIndex;
		record.offers = draft.offers;
		record.picked = picked;
		record.rerollsUsed = draft.rerollsUsed;
		record.pickedAtMillis = clock.getAsLong();
		state.history.add(record);

		state.choiceIndex++;
		state.pendingDraft = null;
		return picked;
	}

	/**
	 * The category the NEXT new draft will use: a pending override (instant
	 * skill keystone for new/migrated profiles) wins over the rotation.
	 */
	public DraftCategory categoryFor(ProfileState state)
	{
		if (state.nextCategoryOverride != null)
		{
			return state.nextCategoryOverride;
		}
		return rotationCategoryFor(state.choiceIndex);
	}

	/**
	 * The fixed category rotation (1-based choice number): every 10th a skill
	 * keystone, every other 5th a free pick, the rest wheeling through
	 * region / monster / item.
	 */
	public DraftCategory rotationCategoryFor(int choiceIndex)
	{
		int choiceNumber = choiceIndex + 1;
		if (choiceNumber % KEYSTONE_INTERVAL == 0)
		{
			return DraftCategory.SKILL;
		}
		if (choiceNumber % FREE_PICK_INTERVAL == 0)
		{
			return DraftCategory.FREE;
		}
		switch (choiceIndex % 3)
		{
			case 0:
				return DraftCategory.REGION;
			case 1:
				return DraftCategory.MONSTER;
			default:
				return DraftCategory.ITEM;
		}
	}

	List<DraftOption> rollOffers(ProfileState state, DraftCategory category, int rerollCount)
	{
		Random rng = new Random(state.seed * 1_000_003L + state.choiceIndex * 101L + rerollCount);
		int band = tierBand(state);

		List<DraftOption> primary;
		switch (category)
		{
			case REGION:
				primary = regionFrontier(state);
				break;
			case MONSTER:
				primary = monsterCandidates(state);
				break;
			case ITEM:
				primary = itemCandidates(state, band);
				break;
			case SKILL:
				primary = skillCandidates(state, band);
				break;
			default:
				primary = new ArrayList<>();
				break;
		}

		if (category != DraftCategory.FREE && !primary.isEmpty())
		{
			return sample(primary, OFFERS_PER_CATEGORY, band, rng);
		}

		// FREE pick — or a category that ran dry, which degrades to one: a
		// single weighted sample across everything still locked, so drafts
		// keep flowing until the run is 100% complete.
		List<DraftOption> combined = new ArrayList<>();
		combined.addAll(regionFrontier(state));
		combined.addAll(monsterCandidates(state));
		combined.addAll(itemCandidates(state, band));
		combined.addAll(skillCandidates(state, band));
		return sample(combined, FREE_PICK_OFFERS, band, rng);
	}

	/**
	 * Locked regions adjacent to at least one unlocked region, in stable
	 * content-file order.
	 */
	public List<DraftOption> regionFrontier(ProfileState state)
	{
		List<DraftOption> frontier = new ArrayList<>();
		for (RegionDef region : content.getRegions())
		{
			if (state.isRegionUnlocked(region.id()))
			{
				continue;
			}
			boolean adjacent = false;
			for (int neighbourId : content.neighboursOf(region.id()))
			{
				if (state.isRegionUnlocked(neighbourId))
				{
					adjacent = true;
					break;
				}
			}
			if (adjacent)
			{
				String detail = "Region · tier " + region.getTier()
					+ (region.getNotes() != null ? " · " + region.getNotes() : "");
				frontier.add(new DraftOption(DraftCategory.REGION, region.getName(), region.id(),
					region.getTier(), detail));
			}
		}
		return frontier;
	}

	/**
	 * Locked monsters with at least one unlocked home region, in stable
	 * content-file order.
	 */
	public List<DraftOption> monsterCandidates(ProfileState state)
	{
		List<DraftOption> candidates = new ArrayList<>();
		for (MonsterDef monster : content.getMonsters())
		{
			if (state.isMonsterUnlocked(monster.getName()))
			{
				continue;
			}
			boolean homeUnlocked = false;
			for (String regionName : monster.getRegions())
			{
				RegionDef region = content.regionByName(regionName);
				if (region != null && state.isRegionUnlocked(region.id()))
				{
					homeUnlocked = true;
					break;
				}
			}
			if (homeUnlocked)
			{
				candidates.add(new DraftOption(DraftCategory.MONSTER, monster.getName(), 0,
					monster.getTier(), "Monster · level " + monster.getCombatLevel()));
			}
		}
		return candidates;
	}

	/**
	 * Locked item tags, honoring the metal-tier chain: a tiered tag is only
	 * offered once the tier below it is owned (bronze is always reachable).
	 * Untiered tags (food, tools, ...) are always on the table.
	 *
	 * @param band used as the neutral weighting tier for untiered tags
	 */
	public List<DraftOption> itemCandidates(ProfileState state, int band)
	{
		List<DraftOption> candidates = new ArrayList<>();
		for (ItemTagDef tag : content.getItemTags())
		{
			if (state.isTagUnlocked(tag.getName()))
			{
				continue;
			}
			if (tag.getTier() != null && tag.getTier() > 1
				&& !previousTierUnlocked(state, tag.getTier()))
			{
				continue;
			}
			int weightTier = tag.getTier() != null ? tag.getTier() : band;
			String detail = "Items · " + tag.getCategory()
				+ (tag.getTier() != null ? " · tier " + tag.getTier() : "")
				+ " · " + tag.getItemNames().size() + " items";
			candidates.add(new DraftOption(DraftCategory.ITEM, tag.getName(), 0, weightTier, detail));
		}
		return candidates;
	}

	private boolean previousTierUnlocked(ProfileState state, int tier)
	{
		for (ItemTagDef tag : content.getItemTags())
		{
			if (tag.getTier() != null && tag.getTier() == tier - 1
				&& state.isTagUnlocked(tag.getName()))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Locked skills. No prerequisites — every locked skill is always draftable,
	 * weighted neutrally at the player's band.
	 */
	public List<DraftOption> skillCandidates(ProfileState state, int band)
	{
		List<DraftOption> candidates = new ArrayList<>();
		for (String skillName : content.getSkillNames())
		{
			if (state.isSkillUnlocked(skillName))
			{
				continue;
			}
			candidates.add(new DraftOption(DraftCategory.SKILL, skillName, 0, band,
				"Skill · keystone unlock"));
		}
		return candidates;
	}

	/**
	 * The player's current progression band: the highest region tier they own.
	 */
	int tierBand(ProfileState state)
	{
		int band = 1;
		for (int regionId : state.unlockedRegions)
		{
			RegionDef region = content.regionById(regionId);
			if (region != null && region.getTier() > band)
			{
				band = region.getTier();
			}
		}
		return band;
	}

	/**
	 * Weighted sampling without replacement, biased toward tiers at or just
	 * above the player's band so offers stay coherent without killing variance.
	 */
	private List<DraftOption> sample(List<DraftOption> candidates, int count, int band, Random rng)
	{
		List<DraftOption> pool = new ArrayList<>(candidates);
		List<DraftOption> out = new ArrayList<>();
		while (!pool.isEmpty() && out.size() < count)
		{
			double totalWeight = 0;
			for (DraftOption option : pool)
			{
				totalWeight += weight(option.getTier(), band);
			}
			double roll = rng.nextDouble() * totalWeight;
			int chosen = pool.size() - 1;
			double cumulative = 0;
			for (int i = 0; i < pool.size(); i++)
			{
				cumulative += weight(pool.get(i).getTier(), band);
				if (roll < cumulative)
				{
					chosen = i;
					break;
				}
			}
			out.add(pool.remove(chosen));
		}
		return out;
	}

	private double weight(int tier, int band)
	{
		if (tier <= band + 1)
		{
			return 4.0 / (1 + Math.abs(tier - band));
		}
		return tier == band + 2 ? 0.5 : 0.15;
	}
}
