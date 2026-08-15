package com.pathlocked.draft;

import com.pathlocked.content.ContentRepository;
import com.pathlocked.content.MonsterDef;
import com.pathlocked.content.RegionDef;
import com.pathlocked.points.ThresholdCurve;
import com.pathlocked.unlocks.ProfileState;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
	private static final int FREE_PICK_INTERVAL = 5;

	private final ContentRepository content;

	public DraftService(ContentRepository content)
	{
		this.content = content;
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
		List<DraftOption> offers = rollOffers(state, 0);
		if (offers.isEmpty())
		{
			return false;
		}
		ProfileState.PendingDraft draft = new ProfileState.PendingDraft();
		draft.choiceIndex = state.choiceIndex;
		draft.offers = offers;
		draft.rerollsUsed = 0;
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
		draft.offers = rollOffers(state, draft.rerollsUsed);
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
		if (picked.getCategory() == DraftCategory.REGION)
		{
			state.unlockedRegions.add(picked.getRegionId());
		}
		else
		{
			state.unlockedMonsters.add(picked.getName().toLowerCase());
		}

		state.spentPoints += ThresholdCurve.cost(state.choiceIndex);

		ProfileState.ChoiceRecord record = new ProfileState.ChoiceRecord();
		record.choiceIndex = state.choiceIndex;
		record.offers = draft.offers;
		record.picked = picked;
		record.rerollsUsed = draft.rerollsUsed;
		state.history.add(record);

		state.choiceIndex++;
		state.pendingDraft = null;
		return picked;
	}

	public DraftCategory categoryFor(int choiceIndex)
	{
		if (choiceIndex % FREE_PICK_INTERVAL == FREE_PICK_INTERVAL - 1)
		{
			return DraftCategory.FREE;
		}
		return choiceIndex % 2 == 0 ? DraftCategory.REGION : DraftCategory.MONSTER;
	}

	List<DraftOption> rollOffers(ProfileState state, int rerollCount)
	{
		DraftCategory category = categoryFor(state.choiceIndex);
		Random rng = new Random(state.seed * 1_000_003L + state.choiceIndex * 101L + rerollCount);
		int band = tierBand(state);

		List<DraftOption> regionCandidates = regionFrontier(state);
		List<DraftOption> monsterCandidates = monsterCandidates(state);

		List<DraftOption> offers = new ArrayList<>();
		switch (category)
		{
			case REGION:
				offers.addAll(sample(regionCandidates, OFFERS_PER_CATEGORY, band, rng));
				if (offers.isEmpty())
				{
					offers.addAll(sample(monsterCandidates, OFFERS_PER_CATEGORY, band, rng));
				}
				break;
			case MONSTER:
				offers.addAll(sample(monsterCandidates, OFFERS_PER_CATEGORY, band, rng));
				if (offers.isEmpty())
				{
					offers.addAll(sample(regionCandidates, OFFERS_PER_CATEGORY, band, rng));
				}
				break;
			case FREE:
				offers.addAll(sample(regionCandidates, OFFERS_PER_CATEGORY, band, rng));
				offers.addAll(sample(monsterCandidates, OFFERS_PER_CATEGORY, band, rng));
				break;
		}
		return offers;
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
