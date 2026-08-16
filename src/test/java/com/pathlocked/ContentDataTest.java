package com.pathlocked;

import com.google.gson.Gson;
import com.pathlocked.content.ContentRepository;
import com.pathlocked.content.RegionDef;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ContentDataTest
{
	@Test
	public void contentLoadsAndValidates()
	{
		ContentRepository content = ContentRepository.load(new Gson());
		List<String> problems = content.validate();
		assertEquals("Content data problems:\n" + String.join("\n", problems), 0, problems.size());
	}

	@Test
	public void contentHasExpectedScale()
	{
		ContentRepository content = ContentRepository.load(new Gson());
		assertTrue("Expected at least 70 regions", content.getRegions().size() >= 70);
		assertTrue("Expected at least 30 monsters", content.getMonsters().size() >= 30);
		assertTrue("Expected at least 15 item tags", content.getItemTags().size() >= 15);
		assertTrue("Expected at least 10 skills", content.getSkillNames().size() >= 10);
		assertTrue("Starter kit needs regions", !content.getStarterRegions().isEmpty());
		assertTrue("Starter kit needs monsters", !content.getStarterMonsters().isEmpty());
		assertTrue("Starter kit needs skills", !content.getStarterSkills().isEmpty());
		assertTrue("Starter kit needs item tags", !content.getStarterTags().isEmpty());
	}

	@Test
	public void itemLookupIsCaseInsensitiveAndSharedItemsListEveryTag()
	{
		ContentRepository content = ContentRepository.load(new Gson());
		assertTrue("Bronze dagger should be tagged",
			!content.tagsForItem("BRONZE DAGGER").isEmpty());
		assertTrue("Unlisted items are uncharted",
			content.tagsForItem("Twisted bow").isEmpty());
		// Bronze pickaxe sits in both a metal tier and Tools; the lookup must
		// return every containing tag or lock checks would be too strict.
		assertTrue(content.tagsForItem("Bronze pickaxe").size() >= 2);
	}

	@Test
	public void lumbridgeIdMatchesKnownValue()
	{
		ContentRepository content = ContentRepository.load(new Gson());
		// Lumbridge is map square (50, 50) -> region id 12850, a well-known constant.
		assertEquals(12850, content.regionByName("Lumbridge").id());
	}

	@Test
	public void undergroundSquaresResolveToTheirOwningRegion()
	{
		ContentRepository content = ContentRepository.load(new Gson());
		int mapped = 0;
		for (RegionDef region : content.getRegions())
		{
			if (region.getUnderground() == null)
			{
				continue;
			}
			for (List<Integer> square : region.getUnderground())
			{
				int squareId = (square.get(0) << 8) | square.get(1);
				assertEquals("Underground square " + square + " should resolve to " + region.getName(),
					Integer.valueOf(region.id()), content.surfaceOwnerOf(squareId));
			}
			mapped++;
		}
		assertTrue("Expected several regions to declare explicit underground squares", mapped >= 5);
	}

	@Test
	public void dwarvenMineMapsToItsSurfaceEntrance()
	{
		ContentRepository content = ContentRepository.load(new Gson());
		RegionDef mine = content.regionByName("Dwarven Mine");
		assertTrue("Dwarven Mine should declare explicit underground squares",
			mine.getUnderground() != null && !mine.getUnderground().isEmpty());
		List<Integer> firstSquare = mine.getUnderground().get(0);
		int squareId = (firstSquare.get(0) << 8) | firstSquare.get(1);
		assertEquals(Integer.valueOf(mine.id()), content.surfaceOwnerOf(squareId));
	}

	@Test
	public void unmappedSquareHasNoSurfaceOwner()
	{
		ContentRepository content = ContentRepository.load(new Gson());
		// Lumbridge itself is a surface square, never an underground owner target.
		assertNull(content.surfaceOwnerOf(content.regionByName("Lumbridge").id()));
	}
}
