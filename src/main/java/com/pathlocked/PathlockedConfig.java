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
		keyName = "enforceItems",
		name = "Enforce item locks",
		description = "Deprioritize Wield/Wear/Eat/Drink on items whose every tag is still locked",
		position = 3
	)
	default boolean enforceItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hardBlockItemUse",
		name = "Hard-block item use",
		description = "Consume Wield/Wear/Eat/Drink clicks on locked items entirely instead of only deprioritizing them",
		position = 4
	)
	default boolean hardBlockItemUse()
	{
		return true;
	}

	@ConfigItem(
		keyName = "greyLockedItems",
		name = "Grey locked items",
		description = "Shade locked items in the inventory, bank and equipment with a padlock marker",
		position = 5
	)
	default boolean greyLockedItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shadeLockedTiles",
		name = "Shade locked regions",
		description = "Tint ground tiles of locked map squares near the player",
		position = 6
	)
	default boolean shadeLockedTiles()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showStatusOverlay",
		name = "Show status overlay",
		description = "Show the locked-region warning and choice-ready banner",
		position = 7
	)
	default boolean showStatusOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showCardOverlay",
		name = "In-game draft cards",
		description = "Show the center-screen draft cards when a choice is ready. Off = draft only in the side panel.",
		position = 8
	)
	default boolean showCardOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "seedOverride",
		name = "Seed override",
		description = "Optional numeric seed applied when a NEW profile is created (for shared/seeded runs). Leave empty to derive the seed from your account.",
		position = 9
	)
	default String seedOverride()
	{
		return "";
	}
}
