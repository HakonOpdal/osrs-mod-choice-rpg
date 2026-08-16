package com.pathlocked;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("pathlocked")
public interface PathlockedConfig extends Config
{
	@ConfigItem(
		keyName = "enforceMonsters",
		name = "Enforce monster locks",
		description = "Deprioritize the Attack option on locked monsters",
		position = 1
	)
	default boolean enforceMonsters()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hardBlockAttack",
		name = "Hard-block attacks",
		description = "Consume Attack clicks on locked monsters entirely instead of only deprioritizing them",
		position = 2
	)
	default boolean hardBlockAttack()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shadeLockedTiles",
		name = "Shade locked regions",
		description = "Tint ground tiles of locked map squares near the player",
		position = 3
	)
	default boolean shadeLockedTiles()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showStatusOverlay",
		name = "Show status overlay",
		description = "Show the locked-region warning and choice-ready banner",
		position = 4
	)
	default boolean showStatusOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "seedOverride",
		name = "Seed override",
		description = "Optional numeric seed applied when a NEW profile is created (for shared/seeded runs). Leave empty to derive the seed from your account.",
		position = 5
	)
	default String seedOverride()
	{
		return "";
	}
}
