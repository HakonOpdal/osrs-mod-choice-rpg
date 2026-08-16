package com.pathlocked.content;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/**
 * Loads and indexes the static content data (regions, monsters, starter kit)
 * bundled as JSON resources. Pure logic: no RuneLite client dependencies.
 */
public class ContentRepository
{
	private static class RegionsFile
	{
		List<RegionDef> regions;
		List<List<String>> links;
	}

	private static class MonstersFile
	{
		List<MonsterDef> monsters;
	}

	private static class StarterKitFile
	{
		List<String> regions;
		List<String> monsters;
	}

	public static final String HOME_REGION = "Lumbridge";

	@Getter
	private final List<RegionDef> regions;
	@Getter
	private final List<MonsterDef> monsters;
	@Getter
	private final List<String> starterRegions;
	@Getter
	private final List<String> starterMonsters;

	private final List<List<String>> links;
	private final Map<Integer, RegionDef> regionsById = new LinkedHashMap<>();
	private final Map<String, RegionDef> regionsByName = new LinkedHashMap<>();
	private final Map<String, MonsterDef> monstersByKey = new LinkedHashMap<>();
	private final Map<Integer, Set<Integer>> adjacency = new HashMap<>();
	/** underground map-square id -> owning surface region id (explicit mappings only). */
	private final Map<Integer, Integer> undergroundOwner = new HashMap<>();

	/**
	 * @param gson the client's injected Gson (Plugin Hub forbids instantiating Gson directly)
	 */
	public static ContentRepository load(Gson gson)
	{
		RegionsFile regionsFile = readResource(gson, "regions.json", RegionsFile.class);
		MonstersFile monstersFile = readResource(gson, "monsters.json", MonstersFile.class);
		StarterKitFile starterFile = readResource(gson, "starter_kit.json", StarterKitFile.class);
		return new ContentRepository(regionsFile.regions, regionsFile.links,
			monstersFile.monsters, starterFile.regions, starterFile.monsters);
	}

	ContentRepository(List<RegionDef> regions, List<List<String>> links, List<MonsterDef> monsters,
		List<String> starterRegions, List<String> starterMonsters)
	{
		this.regions = regions;
		this.links = links == null ? new ArrayList<>() : links;
		this.monsters = monsters;
		this.starterRegions = starterRegions;
		this.starterMonsters = starterMonsters;

		for (RegionDef region : regions)
		{
			regionsById.put(region.id(), region);
			regionsByName.put(region.getName(), region);
		}
		for (MonsterDef monster : monsters)
		{
			monstersByKey.put(monster.key(), monster);
		}
		buildAdjacency();
		buildUndergroundOwners();
	}

	private void buildUndergroundOwners()
	{
		for (RegionDef region : regions)
		{
			if (region.getUnderground() == null)
			{
				continue;
			}
			for (List<Integer> square : region.getUnderground())
			{
				if (!isWellFormedSquare(square))
				{
					continue;
				}
				int squareId = (square.get(0) << 8) | square.get(1);
				// First writer wins; validate() reports any conflicting duplicates.
				undergroundOwner.putIfAbsent(squareId, region.id());
			}
		}
	}

	/** A well-formed underground square is a two-element [rx, ry] pair with no null coordinate. */
	private static boolean isWellFormedSquare(List<Integer> square)
	{
		return square != null && square.size() == 2 && square.get(0) != null && square.get(1) != null;
	}

	private static <T> T readResource(Gson gson, String resourceName, Class<T> type)
	{
		InputStream stream = ContentRepository.class.getResourceAsStream("/com/pathlocked/" + resourceName);
		if (stream == null)
		{
			throw new IllegalStateException("Missing content resource: " + resourceName);
		}
		try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
		{
			return gson.fromJson(reader, type);
		}
		catch (Exception e)
		{
			throw new IllegalStateException("Failed to load content resource: " + resourceName, e);
		}
	}

	private void buildAdjacency()
	{
		for (RegionDef region : regions)
		{
			Set<Integer> neighbours = new HashSet<>();
			int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (int[] offset : offsets)
			{
				int neighbourId = ((region.getRx() + offset[0]) << 8) | (region.getRy() + offset[1]);
				if (regionsById.containsKey(neighbourId))
				{
					neighbours.add(neighbourId);
				}
			}
			adjacency.put(region.id(), neighbours);
		}
		for (List<String> link : links)
		{
			RegionDef a = regionsByName.get(link.get(0));
			RegionDef b = regionsByName.get(link.get(1));
			if (a != null && b != null)
			{
				adjacency.get(a.id()).add(b.id());
				adjacency.get(b.id()).add(a.id());
			}
		}
	}

	public RegionDef regionById(int id)
	{
		return regionsById.get(id);
	}

	public RegionDef regionByName(String name)
	{
		return regionsByName.get(name);
	}

	public boolean isKnownRegion(int id)
	{
		return regionsById.containsKey(id);
	}

	/**
	 * The surface region id that owns the unlock for an explicitly-mapped
	 * underground map square, or {@code null} if the square has no explicit
	 * mapping. Callers should fall back to the generic {@code ry−100} rule when
	 * this returns null. See {@code docs/integration-notes/lane-B.md}.
	 *
	 * @param regionId an underground map-square region id
	 * @return owning surface region id, or null when unmapped
	 */
	public Integer surfaceOwnerOf(int regionId)
	{
		return undergroundOwner.get(regionId);
	}

	public Set<Integer> neighboursOf(int regionId)
	{
		return adjacency.getOrDefault(regionId, Set.of());
	}

	public MonsterDef monsterByName(String npcName)
	{
		return npcName == null ? null : monstersByKey.get(npcName.toLowerCase());
	}

	public boolean isListedMonster(String npcName)
	{
		return monsterByName(npcName) != null;
	}

	/**
	 * Data sanity checks used by tests; returns a list of problems (empty = valid).
	 */
	public List<String> validate()
	{
		List<String> problems = new ArrayList<>();

		Set<String> regionNames = new HashSet<>();
		Set<Integer> regionIds = new HashSet<>();
		for (RegionDef region : regions)
		{
			if (!regionNames.add(region.getName()))
			{
				problems.add("Duplicate region name: " + region.getName());
			}
			if (!regionIds.add(region.id()))
			{
				problems.add("Duplicate region id (rx/ry): " + region.getName());
			}
			if (region.getTier() < 1 || region.getTier() > 5)
			{
				problems.add("Region tier out of range 1-5: " + region.getName());
			}
		}

		// Underground mappings: every square well-formed, owned once, and never
		// colliding with a surface square (a square is surface XOR underground).
		Map<Integer, String> undergroundClaimedBy = new HashMap<>();
		for (RegionDef region : regions)
		{
			if (region.getUnderground() == null)
			{
				continue;
			}
			for (List<Integer> square : region.getUnderground())
			{
				if (!isWellFormedSquare(square))
				{
					problems.add("Underground square must be a non-null [rx, ry] pair in region: " + region.getName());
					continue;
				}
				int squareId = (square.get(0) << 8) | square.get(1);
				if (regionsById.containsKey(squareId))
				{
					problems.add("Underground square " + square + " in " + region.getName()
						+ " collides with surface region: " + regionsById.get(squareId).getName());
				}
				String previousOwner = undergroundClaimedBy.putIfAbsent(squareId, region.getName());
				if (previousOwner != null)
				{
					problems.add("Underground square " + square + " mapped twice: "
						+ previousOwner + " and " + region.getName());
				}
			}
		}

		for (List<String> link : links)
		{
			if (link.size() != 2)
			{
				problems.add("Link must have exactly two region names: " + link);
				continue;
			}
			for (String name : link)
			{
				if (!regionsByName.containsKey(name))
				{
					problems.add("Link references unknown region: " + name);
				}
			}
		}

		Set<String> monsterNames = new HashSet<>();
		for (MonsterDef monster : monsters)
		{
			if (!monsterNames.add(monster.key()))
			{
				problems.add("Duplicate monster name: " + monster.getName());
			}
			if (monster.getTier() < 1 || monster.getTier() > 5)
			{
				problems.add("Monster tier out of range 1-5: " + monster.getName());
			}
			if (monster.getRegions() == null || monster.getRegions().isEmpty())
			{
				problems.add("Monster has no home regions: " + monster.getName());
			}
			else
			{
				for (String regionName : monster.getRegions())
				{
					if (!regionsByName.containsKey(regionName))
					{
						problems.add("Monster " + monster.getName() + " references unknown region: " + regionName);
					}
				}
			}
		}

		for (String name : starterRegions)
		{
			if (!regionsByName.containsKey(name))
			{
				problems.add("Starter kit references unknown region: " + name);
			}
		}
		for (String name : starterMonsters)
		{
			if (!monstersByKey.containsKey(name.toLowerCase()))
			{
				problems.add("Starter kit references unknown monster: " + name);
			}
		}

		RegionDef home = regionsByName.get(HOME_REGION);
		if (home == null)
		{
			problems.add("Home region missing: " + HOME_REGION);
		}
		else
		{
			Set<Integer> reachable = new HashSet<>();
			Deque<Integer> queue = new ArrayDeque<>();
			queue.add(home.id());
			reachable.add(home.id());
			while (!queue.isEmpty())
			{
				int current = queue.poll();
				for (int neighbour : neighboursOf(current))
				{
					if (reachable.add(neighbour))
					{
						queue.add(neighbour);
					}
				}
			}
			for (RegionDef region : regions)
			{
				if (!reachable.contains(region.id()))
				{
					problems.add("Region not reachable from " + HOME_REGION + ": " + region.getName());
				}
			}
		}

		return problems;
	}
}
