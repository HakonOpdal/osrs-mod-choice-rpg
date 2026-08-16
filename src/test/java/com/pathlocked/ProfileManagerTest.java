package com.pathlocked;

import com.google.gson.Gson;
import com.pathlocked.content.ContentRepository;
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
