package com.pathlocked.unlocks;

import com.google.gson.Gson;
import com.pathlocked.content.ContentRepository;
import com.pathlocked.content.RegionDef;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads and saves one JSON profile file per account hash. The directory is
 * injected so the core stays testable without a RuneLite client.
 */
@Slf4j
public class ProfileManager
{
	private final File directory;
	private final Gson gson;

	/**
	 * True when the last loadOrCreate created a fresh profile.
	 */
	@Getter
	private boolean createdNewProfile;

	/**
	 * @param gson the client's injected Gson; a pretty-printing variant is derived
	 * from it rather than instantiating Gson directly (Plugin Hub rule)
	 */
	public ProfileManager(File directory, Gson gson)
	{
		this.directory = directory;
		this.gson = gson.newBuilder().setPrettyPrinting().create();
	}

	public ProfileState loadOrCreate(long accountHash, ContentRepository content, long seed)
	{
		createdNewProfile = false;
		File file = profileFile(accountHash);
		if (file.exists())
		{
			try
			{
				String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
				ProfileState state = gson.fromJson(json, ProfileState.class);
				if (state != null && state.unlockedRegions != null && !state.unlockedRegions.isEmpty())
				{
					return state;
				}
				log.warn("Profile file {} parsed to an empty state; recreating", file);
			}
			catch (Exception e)
			{
				log.warn("Failed to read profile file {}; backing it up and recreating", file, e);
			}
			backupCorruptFile(file);
		}

		ProfileState state = createProfile(content, seed);
		createdNewProfile = true;
		save(accountHash, state);
		return state;
	}

	private ProfileState createProfile(ContentRepository content, long seed)
	{
		ProfileState state = new ProfileState();
		state.seed = seed;
		for (String regionName : content.getStarterRegions())
		{
			RegionDef region = content.regionByName(regionName);
			if (region != null)
			{
				state.unlockedRegions.add(region.id());
			}
		}
		for (String monsterName : content.getStarterMonsters())
		{
			state.unlockedMonsters.add(monsterName.toLowerCase());
		}
		return state;
	}

	/**
	 * @return true if the profile was written; callers should keep their dirty
	 * state on failure so the write is retried.
	 */
	public boolean save(long accountHash, ProfileState state)
	{
		try
		{
			Files.createDirectories(directory.toPath());
			Path target = profileFile(accountHash).toPath();
			Path temp = target.resolveSibling(target.getFileName() + ".tmp");
			Files.write(temp, gson.toJson(state).getBytes(StandardCharsets.UTF_8));
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			return true;
		}
		catch (Exception e)
		{
			log.error("Failed to save Pathlocked profile", e);
			return false;
		}
	}

	private void backupCorruptFile(File file)
	{
		try
		{
			Path source = file.toPath();
			Files.move(source, source.resolveSibling(file.getName() + ".bad-" + System.currentTimeMillis()),
				StandardCopyOption.REPLACE_EXISTING);
		}
		catch (Exception e)
		{
			log.warn("Failed to back up corrupt profile file {}", file, e);
		}
	}

	private File profileFile(long accountHash)
	{
		return new File(directory, "profile-" + Long.toUnsignedString(accountHash) + ".json");
	}
}
