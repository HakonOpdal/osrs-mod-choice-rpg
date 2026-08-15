package com.pathlocked;

import com.google.gson.Gson;
import com.pathlocked.content.ContentRepository;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
		assertTrue("Starter kit needs regions", !content.getStarterRegions().isEmpty());
		assertTrue("Starter kit needs monsters", !content.getStarterMonsters().isEmpty());
	}

	@Test
	public void lumbridgeIdMatchesKnownValue()
	{
		ContentRepository content = ContentRepository.load(new Gson());
		// Lumbridge is map square (50, 50) -> region id 12850, a well-known constant.
		assertEquals(12850, content.regionByName("Lumbridge").id());
	}
}
