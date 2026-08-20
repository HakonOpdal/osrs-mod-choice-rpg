package com.pathlocked;

import com.google.gson.Gson;
import com.pathlocked.content.ContentRepository;
import com.pathlocked.draft.DraftCategory;
import com.pathlocked.points.ThresholdCurve;
import com.pathlocked.unlocks.ProfileManager;
import com.pathlocked.unlocks.ProfileState;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfileManagerTest
{
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void newProfileGetsStarterKitAndPersists()
	{
		ContentRepository content = ContentRepository.load(new Gson());
		File directory = temporaryFolder.getRoot();
		ProfileManager manager = new ProfileManager(directory, new Gson());

		ProfileState created = manager.loadOrCreate(123L, content, 999L);
		assertTrue(manager.isCreatedNewProfile());
		assertEquals(999L, created.seed);
		assertEquals("New profiles bank the first threshold for an instant first draft",
			ThresholdCurve.cost(0), created.totalPoints);
		assertEquals(content.getStarterRegions().size(), created.unlockedRegions.size());
		assertEquals(content.getStarterMonsters().size(), created.unlockedMonsters.size());
		assertEquals(content.getStarterSkills().size(), created.unlockedSkills.size());
		assertEquals(content.getStarterTags().size(), created.unlockedTags.size());
		assertEquals("First draft must be the identity-skill pick",
			DraftCategory.SKILL, created.nextCategoryOverride);

		created.totalPoints = 5000;
		created.choiceIndex = 3;
		manager.save(123L, created);

		ProfileManager reloadedManager = new ProfileManager(directory, new Gson());
		ProfileState reloaded = reloadedManager.loadOrCreate(123L, content, 111L);
		assertFalse(reloadedManager.isCreatedNewProfile());
		assertEquals(5000, reloaded.totalPoints);
		assertEquals(3, reloaded.choiceIndex);
		assertEquals(999L, reloaded.seed);
	}

	@Test
	public void v01ProfileMigratesToV2WithInstantSkillDraft() throws Exception
	{
		ContentRepository content = ContentRepository.load(new Gson());
		File directory = temporaryFolder.getRoot();
		ProfileManager manager = new ProfileManager(directory, new Gson());

		// A mid-run v0.1 profile: no skill/tag fields at all.
		String v01Json = "{\"seed\":7,\"totalPoints\":900,\"spentPoints\":875,\"choiceIndex\":2,"
			+ "\"unlockedRegions\":[12850,12849],\"unlockedMonsters\":[\"chicken\"],"
			+ "\"history\":[],\"violationTicks\":0,\"illegalKills\":0,\"xpRemainder\":0}";
		File profileFile = new File(directory, "profile-77.json");
		Files.write(profileFile.toPath(), v01Json.getBytes(StandardCharsets.UTF_8));

		ProfileState migrated = manager.loadOrCreate(77L, content, 1L);
		assertFalse(manager.isCreatedNewProfile());
		assertEquals(content.getStarterSkills().size(), migrated.unlockedSkills.size());
		assertEquals(content.getStarterTags().size(), migrated.unlockedTags.size());
		// Curve rebase first: spent becomes cost(0)+cost(1) on the new curve,
		// the old 25-point surplus carries, then the v2 migration banks cost(2).
		long rebasedSpent = ThresholdCurve.cost(0) + ThresholdCurve.cost(1);
		assertEquals(rebasedSpent, migrated.spentPoints);
		assertEquals("Rebase carries the old surplus, migration banks the skill draft",
			rebasedSpent + 25 + ThresholdCurve.cost(2), migrated.totalPoints);
		assertEquals(2, migrated.curveVersion);
		assertEquals(DraftCategory.SKILL, migrated.nextCategoryOverride);

		// Migration persists: a second load must not bank another threshold.
		ProfileState reloaded = new ProfileManager(directory, new Gson()).loadOrCreate(77L, content, 1L);
		assertEquals(migrated.totalPoints, reloaded.totalPoints);
	}

	@Test
	public void migrationWithPendingDraftBanksTheNextThreshold() throws Exception
	{
		ContentRepository content = ContentRepository.load(new Gson());
		File directory = temporaryFolder.getRoot();
		ProfileManager manager = new ProfileManager(directory, new Gson());

		// v0.1 profile mid-draft: the open draft will spend cost(2) on pick, so
		// the promised instant skill draft costs cost(3) — bank that, not cost(2).
		String v01Json = "{\"seed\":7,\"totalPoints\":1200,\"spentPoints\":875,\"choiceIndex\":2,"
			+ "\"unlockedRegions\":[12850],\"unlockedMonsters\":[\"chicken\"],\"history\":[],"
			+ "\"pendingDraft\":{\"choiceIndex\":2,\"rerollsUsed\":0,\"offers\":["
			+ "{\"category\":\"REGION\",\"name\":\"Al Kharid\",\"regionId\":13105,\"tier\":1,\"detail\":\"d\"}]}}";
		Files.write(new File(directory, "profile-88.json").toPath(),
			v01Json.getBytes(StandardCharsets.UTF_8));

		ProfileState migrated = manager.loadOrCreate(88L, content, 1L);
		// Rebase keeps the pending draft exactly payable (cost(2) carried),
		// then the v2 migration banks the NEXT threshold for the skill draft.
		long rebasedSpent = ThresholdCurve.cost(0) + ThresholdCurve.cost(1);
		assertEquals(rebasedSpent + ThresholdCurve.cost(2) + ThresholdCurve.cost(3),
			migrated.totalPoints);
		assertEquals("Legacy pending draft gets its category backfilled",
			DraftCategory.REGION, migrated.pendingDraft.category);
		assertEquals(DraftCategory.SKILL, migrated.nextCategoryOverride);
		assertFalse("The pre-override pending draft must not clear the override on pick",
			migrated.pendingDraft.consumedOverride);
	}

	@Test
	public void curveRebaseCapsAnOldFatBankAtOneThreshold() throws Exception
	{
		ContentRepository content = ContentRepository.load(new Gson());
		ProfileManager manager = new ProfileManager(temporaryFolder.getRoot(), new Gson());

		// v0.2 profile (skills present, no curveVersion): 15,000 banked under
		// the old curve at choiceIndex 20 would afford ~9 paced drafts.
		String v02Json = "{\"seed\":7,\"totalPoints\":45000,\"spentPoints\":30000,\"choiceIndex\":20,"
			+ "\"unlockedRegions\":[12850],\"unlockedMonsters\":[\"chicken\"],"
			+ "\"unlockedSkills\":[\"attack\",\"strength\",\"hitpoints\"],"
			+ "\"unlockedTags\":[\"bronze tier\"],\"history\":[]}";
		Files.write(new File(temporaryFolder.getRoot(), "profile-99.json").toPath(),
			v02Json.getBytes(StandardCharsets.UTF_8));

		ProfileState rebased = manager.loadOrCreate(99L, content, 1L);
		assertEquals(2, rebased.curveVersion);
		assertTrue("No draft burst: at most one threshold may be banked",
			rebased.availablePoints() <= ThresholdCurve.cost(20));
	}

	@Test
	public void curveRebaseRestoresTheInstantFirstDraft() throws Exception
	{
		ContentRepository content = ContentRepository.load(new Gson());
		ProfileManager manager = new ProfileManager(temporaryFolder.getRoot(), new Gson());

		// Zero-progress v0.2 profile pre-banked with the OLD cost(0)=250,
		// which no longer meets the new 350 first threshold.
		String v02Json = "{\"seed\":7,\"totalPoints\":250,\"spentPoints\":0,\"choiceIndex\":0,"
			+ "\"unlockedRegions\":[12850],\"unlockedMonsters\":[\"chicken\"],"
			+ "\"unlockedSkills\":[\"attack\",\"strength\",\"hitpoints\"],"
			+ "\"unlockedTags\":[\"bronze tier\"],\"history\":[]}";
		Files.write(new File(temporaryFolder.getRoot(), "profile-101.json").toPath(),
			v02Json.getBytes(StandardCharsets.UTF_8));

		ProfileState rebased = manager.loadOrCreate(101L, content, 1L);
		assertEquals("Instant-first-draft promise survives the curve swap",
			ThresholdCurve.cost(0), rebased.availablePoints());
	}

	@Test
	public void corruptProfileIsBackedUpAndRecreated() throws Exception
	{
		ContentRepository content = ContentRepository.load(new Gson());
		File directory = temporaryFolder.getRoot();
		ProfileManager manager = new ProfileManager(directory, new Gson());
		manager.loadOrCreate(55L, content, 1L);

		File profileFile = new File(directory, "profile-55.json");
		assertTrue(profileFile.exists());
		Files.write(profileFile.toPath(), "{not valid json!!".getBytes(StandardCharsets.UTF_8));

		ProfileState recovered = manager.loadOrCreate(55L, content, 2L);
		assertTrue(manager.isCreatedNewProfile());
		assertEquals(2L, recovered.seed);
		boolean backupExists = false;
		for (File file : directory.listFiles())
		{
			if (file.getName().startsWith("profile-55.json.bad-"))
			{
				backupExists = true;
			}
		}
		assertTrue("Corrupt file should be backed up", backupExists);
	}
}
