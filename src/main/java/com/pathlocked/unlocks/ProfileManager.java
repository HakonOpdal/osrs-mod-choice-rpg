package com.pathlocked.unlocks;

import com.google.gson.Gson;
import com.pathlocked.content.ContentRepository;
import com.pathlocked.content.RegionDef;
import com.pathlocked.draft.DraftCategory;
import com.pathlocked.points.ThresholdCurve;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
					ProfileState normalized = normalize(state);
					if (migrateToV2(normalized, content))
					{
						save(accountHash, normalized);
					}
					return normalized;
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

	/**
	 * Repairs null fields in a hand-edited or partially corrupt but parseable
	 * profile, so one bad field degrades gracefully instead of NPE-ing every login.
	 */
	private static ProfileState normalize(ProfileState state)
	{
		if (state.unlockedMonsters == null)
		{
			state.unlockedMonsters = new LinkedHashSet<>();
		}
		if (state.unlockedTags == null)
		{
			state.unlockedTags = new LinkedHashSet<>();
		}
		if (state.unlockedSkills == null)
		{
			state.unlockedSkills = new LinkedHashSet<>();
		}
		if (state.voidXpBySkill == null)
		{
			state.voidXpBySkill = new LinkedHashMap<>();
		}
		if (state.history == null)
		{
			state.history = new ArrayList<>();
		}
		if (state.pendingDraft != null
			&& (state.pendingDraft.offers == null || state.pendingDraft.offers.isEmpty()
				|| state.pendingDraft.offers.stream()
					.anyMatch(offer -> offer == null || offer.getCategory() == null)))
		{
			// Empty or malformed offers (null category would NPE the pick
			// switch): drop the draft; a fresh one rolls on the next tick.
			state.pendingDraft = null;
		}
		if (state.pendingDraft != null && state.pendingDraft.category == null)
		{
			// Pre-category profile: infer so a reroll keeps the same kind of
			// draft. Mixed offers can only have come from a free pick.
			DraftCategory first = state.pendingDraft.offers.get(0).getCategory();
			boolean uniform = state.pendingDraft.offers.stream()
				.allMatch(offer -> offer.getCategory() == first);
			state.pendingDraft.category = uniform ? first : DraftCategory.FREE;
		}
		return state;
	}

	/**
	 * Upgrades a v0.1 profile (no skill/tag unlocks yet) in place: grants the
	 * starter skills and tags, and banks one extra threshold with a forced
	 * SKILL draft so the mid-run account picks its identity skill immediately
	 * instead of waiting for the next keystone.
	 *
	 * @return true when a migration happened and the profile should be re-saved
	 */
	private static boolean migrateToV2(ProfileState state, ContentRepository content)
	{
		if (!state.unlockedSkills.isEmpty())
		{
			return false;
		}
		grantStarterSkillsAndTags(state, content);
		// Bank the threshold the forced skill draft will spend. With a pending
		// v0.1 draft still open, that draft consumes cost(choiceIndex) first,
		// so the skill draft's own cost is the NEXT threshold.
		int skillDraftIndex = state.pendingDraft != null ? state.choiceIndex + 1 : state.choiceIndex;
		state.totalPoints += ThresholdCurve.cost(skillDraftIndex);
		state.nextCategoryOverride = DraftCategory.SKILL;
		return true;
	}

	private static void grantStarterSkillsAndTags(ProfileState state, ContentRepository content)
	{
		for (String skillName : content.getStarterSkills())
		{
			state.unlockedSkills.add(skillName.toLowerCase());
		}
		for (String tagName : content.getStarterTags())
		{
			state.unlockedTags.add(tagName.toLowerCase());
		}
	}

	private ProfileState createProfile(ContentRepository content, long seed)
	{
		ProfileState state = new ProfileState();
		state.seed = seed;
		// New runs start with the first threshold already banked so the very
		// first login opens a draft — a taste of the loop before the grind.
		state.totalPoints = ThresholdCurve.cost(0);
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
		grantStarterSkillsAndTags(state, content);
		// The banked first threshold plus a forced SKILL category makes the very
		// first draft the identity-skill pick — the opening act of a run.
		state.nextCategoryOverride = DraftCategory.SKILL;
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
