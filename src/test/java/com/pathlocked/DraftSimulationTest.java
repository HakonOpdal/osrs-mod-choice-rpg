package com.pathlocked;

import com.google.gson.Gson;
import com.pathlocked.content.ContentRepository;
import com.pathlocked.draft.DraftCategory;
import com.pathlocked.draft.DraftService;
import com.pathlocked.points.ThresholdCurve;
import com.pathlocked.unlocks.ProfileState;
import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The softlock guarantee: from the starter kit, any seed must be able to
 * draft its way to 100% completion, and the player must always have
 * something attackable along the way.
 */
public class DraftSimulationTest
{
	private static final int SEEDS = 500;

	@Test
	public void everySeedCanReachFullCompletion()
	{
		ContentRepository content = ContentRepository.load(new Gson());
		DraftService draftService = new DraftService(content);
		int totalUnlockables = content.getRegions().size() + content.getMonsters().size()
			+ content.getItemTags().size() + content.getSkillNames().size();

		for (long seed = 0; seed < SEEDS; seed++)
		{
			ProfileState state = new ProfileState();
			state.seed = seed;
			for (String name : content.getStarterRegions())
			{
				state.unlockedRegions.add(content.regionByName(name).id());
			}
			for (String name : content.getStarterMonsters())
			{
				state.unlockedMonsters.add(name.toLowerCase());
			}
			for (String name : content.getStarterSkills())
			{
				state.unlockedSkills.add(name.toLowerCase());
			}
			for (String name : content.getStarterTags())
			{
				state.unlockedTags.add(name.toLowerCase());
			}
			// Mirror a real new profile: the first draft is the forced skill pick.
			state.nextCategoryOverride = DraftCategory.SKILL;

			Random pickRng = new Random(seed);
			int safety = totalUnlockables + 10;
			while (safety-- > 0)
			{
				state.totalPoints += ThresholdCurve.cost(state.choiceIndex);
				if (!draftService.maybeStartDraft(state))
				{
					break;
				}
				assertTrue("Seed " + seed + ": player must always have monsters to fight",
					state.unlockedMonsters.size() >= 3);
				int pick = pickRng.nextInt(state.pendingDraft.offers.size());
				draftService.pick(state, pick);
			}

			assertEquals("Seed " + seed + " could not unlock every region",
				content.getRegions().size(), state.unlockedRegions.size());
			assertEquals("Seed " + seed + " could not unlock every monster",
				content.getMonsters().size(), state.unlockedMonsters.size());
			assertEquals("Seed " + seed + " could not unlock every item tag",
				content.getItemTags().size(), state.unlockedTags.size());
			assertEquals("Seed " + seed + " could not unlock every skill",
				content.getSkillNames().size(), state.unlockedSkills.size());
		}
	}
}
