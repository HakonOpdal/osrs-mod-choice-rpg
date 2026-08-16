package com.pathlocked;

import com.google.gson.Gson;
import com.pathlocked.content.ContentRepository;
import com.pathlocked.content.RegionDef;
import com.pathlocked.draft.DraftCategory;
import com.pathlocked.draft.DraftOption;
import com.pathlocked.draft.DraftService;
import com.pathlocked.points.ThresholdCurve;
import com.pathlocked.unlocks.ProfileState;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DraftServiceTest
{
	private ContentRepository content;
	private DraftService draftService;

	@Before
	public void setUp()
	{
		content = ContentRepository.load(new Gson());
		draftService = new DraftService(content);
	}

	private ProfileState newStarterProfile(long seed)
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
		return state;
	}

	@Test
	public void noDraftWithoutEnoughPoints()
	{
		ProfileState state = newStarterProfile(42);
		state.totalPoints = ThresholdCurve.cost(0) - 1;
		assertFalse(draftService.maybeStartDraft(state));
	}

	@Test
	public void draftStartsAtThresholdWithThreeOffers()
	{
		ProfileState state = newStarterProfile(42);
		state.totalPoints = ThresholdCurve.cost(0);
		assertTrue(draftService.maybeStartDraft(state));
		assertNotNull(state.pendingDraft);
		assertEquals(DraftService.OFFERS_PER_CATEGORY, state.pendingDraft.offers.size());
	}

	@Test
	public void categoryWheelRotatesWithFreeFifthAndSkillTenth()
	{
		assertEquals(DraftCategory.REGION, draftService.rotationCategoryFor(0));
		assertEquals(DraftCategory.MONSTER, draftService.rotationCategoryFor(1));
		assertEquals(DraftCategory.ITEM, draftService.rotationCategoryFor(2));
		assertEquals(DraftCategory.REGION, draftService.rotationCategoryFor(3));
		assertEquals(DraftCategory.FREE, draftService.rotationCategoryFor(4));
		assertEquals(DraftCategory.ITEM, draftService.rotationCategoryFor(5));
		assertEquals(DraftCategory.SKILL, draftService.rotationCategoryFor(9));
		assertEquals(DraftCategory.FREE, draftService.rotationCategoryFor(14));
		assertEquals(DraftCategory.SKILL, draftService.rotationCategoryFor(19));
	}

	@Test
	public void overrideForcesSkillDraftAndClearsOnPick()
	{
		ProfileState state = newStarterProfile(42);
		state.nextCategoryOverride = DraftCategory.SKILL;
		state.totalPoints = ThresholdCurve.cost(0);

		assertTrue(draftService.maybeStartDraft(state));
		for (DraftOption option : state.pendingDraft.offers)
		{
			assertEquals(DraftCategory.SKILL, option.getCategory());
		}
		assertTrue("Reroll must keep the forced category", draftService.reroll(state));
		for (DraftOption option : state.pendingDraft.offers)
		{
			assertEquals(DraftCategory.SKILL, option.getCategory());
		}

		int skillsBefore = state.unlockedSkills.size();
		DraftOption picked = draftService.pick(state, 0);
		assertNotNull(picked);
		assertEquals(skillsBefore + 1, state.unlockedSkills.size());
		assertTrue(state.isSkillUnlocked(picked.getName()));
		assertNull("Override must clear once its draft is picked", state.nextCategoryOverride);
	}

	@Test
	public void itemTierChainOnlyOffersTheNextTier()
	{
		ProfileState state = newStarterProfile(7);
		// Starter tags include Bronze tier (tier 1), so tier 2 is the frontier.
		List<String> names = draftService.itemCandidates(state, 1).stream()
			.map(DraftOption::getName).collect(Collectors.toList());
		assertTrue("Iron tier should be draftable after Bronze", names.contains("Iron tier"));
		assertFalse("Steel tier must wait for Iron", names.contains("Steel tier"));
		assertFalse("Unlocked starter tag must not be re-offered", names.contains("Bronze tier"));
		assertTrue("Untiered tags are always draftable", names.contains("Basic runes"));
	}

	@Test
	public void freePickMixesCategoriesUpToSixOffers()
	{
		ProfileState state = newStarterProfile(11);
		state.choiceIndex = 4;
		state.totalPoints = ThresholdCurve.cost(4) * 4;
		assertTrue(draftService.maybeStartDraft(state));
		assertTrue("Free pick offers at most 6 cards",
			state.pendingDraft.offers.size() <= DraftService.FREE_PICK_OFFERS);
		assertTrue("Free pick offers more than one category",
			state.pendingDraft.offers.stream().map(DraftOption::getCategory).distinct().count() > 1);
	}

	@Test
	public void itemPickUnlocksTheTag()
	{
		ProfileState state = newStarterProfile(3);
		state.choiceIndex = 2;
		state.totalPoints = ThresholdCurve.cost(2) * 3;
		assertTrue(draftService.maybeStartDraft(state));
		for (DraftOption option : state.pendingDraft.offers)
		{
			assertEquals(DraftCategory.ITEM, option.getCategory());
		}
		DraftOption picked = draftService.pick(state, 0);
		assertNotNull(picked);
		assertTrue(state.isTagUnlocked(picked.getName()));
	}

	@Test
	public void offersAreDeterministicForSameSeed()
	{
		ProfileState first = newStarterProfile(1234);
		first.totalPoints = ThresholdCurve.cost(0);
		ProfileState second = newStarterProfile(1234);
		second.totalPoints = ThresholdCurve.cost(0);

		assertTrue(draftService.maybeStartDraft(first));
		assertTrue(draftService.maybeStartDraft(second));
		assertEquals(names(first.pendingDraft.offers), names(second.pendingDraft.offers));
	}

	@Test
	public void differentSeedsProduceDifferentOffersSomewhere()
	{
		boolean sawDifference = false;
		for (long seed = 0; seed < 20 && !sawDifference; seed++)
		{
			ProfileState a = newStarterProfile(seed);
			a.totalPoints = ThresholdCurve.cost(0);
			ProfileState b = newStarterProfile(seed + 1000);
			b.totalPoints = ThresholdCurve.cost(0);
			draftService.maybeStartDraft(a);
			draftService.maybeStartDraft(b);
			sawDifference = !names(a.pendingDraft.offers).equals(names(b.pendingDraft.offers));
		}
		assertTrue("20 seed pairs all produced identical offers", sawDifference);
	}

	@Test
	public void rerollChangesOffersDeterministically()
	{
		ProfileState state = newStarterProfile(77);
		state.totalPoints = ThresholdCurve.cost(0);
		assertTrue(draftService.maybeStartDraft(state));
		List<String> original = names(state.pendingDraft.offers);

		assertTrue(draftService.reroll(state));
		List<String> rerolled = names(state.pendingDraft.offers);
		assertFalse("Second reroll should be refused", draftService.reroll(state));

		ProfileState replay = newStarterProfile(77);
		replay.totalPoints = ThresholdCurve.cost(0);
		assertTrue(draftService.maybeStartDraft(replay));
		assertEquals(original, names(replay.pendingDraft.offers));
		assertTrue(draftService.reroll(replay));
		assertEquals("Reroll must be deterministic", rerolled, names(replay.pendingDraft.offers));
	}

	@Test
	public void firstDraftOffersOnlyFrontierRegions()
	{
		ProfileState state = newStarterProfile(7);
		state.totalPoints = ThresholdCurve.cost(0);
		assertTrue(draftService.maybeStartDraft(state));
		for (DraftOption option : state.pendingDraft.offers)
		{
			assertEquals(DraftCategory.REGION, option.getCategory());
			assertFalse("Offered region already unlocked",
				state.isRegionUnlocked(option.getRegionId()));
			boolean adjacent = content.neighboursOf(option.getRegionId()).stream()
				.anyMatch(state::isRegionUnlocked);
			assertTrue("Offered region " + option.getName() + " is not adjacent to an unlock", adjacent);
		}
	}

	@Test
	public void pickUnlocksSpendsAndAdvances()
	{
		ProfileState state = newStarterProfile(7);
		long cost = ThresholdCurve.cost(0);
		state.totalPoints = cost + 10;
		assertTrue(draftService.maybeStartDraft(state));

		int regionsBefore = state.unlockedRegions.size();
		DraftOption picked = draftService.pick(state, 0);

		assertNotNull(picked);
		assertEquals(regionsBefore + 1, state.unlockedRegions.size());
		assertEquals(cost, state.spentPoints);
		assertEquals(10, state.availablePoints());
		assertEquals(1, state.choiceIndex);
		assertEquals(1, state.history.size());
		assertEquals(picked.getName(), state.history.get(0).picked.getName());
		assertNotEquals("Pending draft should be cleared", true, state.pendingDraft != null);
	}

	private static List<String> names(List<DraftOption> offers)
	{
		return offers.stream().map(DraftOption::getName).collect(Collectors.toList());
	}

	@Test
	public void tierOneRegionsExistOnStarterFrontier()
	{
		ProfileState state = newStarterProfile(7);
		List<DraftOption> frontier = draftService.regionFrontier(state);
		assertTrue("Starter frontier should not be empty", !frontier.isEmpty());
		boolean hasLowTier = frontier.stream()
			.map(DraftOption::getRegionId)
			.map(content::regionById)
			.map(RegionDef::getTier)
			.anyMatch(tier -> tier <= 2);
		assertTrue("Starter frontier should include low-tier regions", hasLowTier);
	}
}
