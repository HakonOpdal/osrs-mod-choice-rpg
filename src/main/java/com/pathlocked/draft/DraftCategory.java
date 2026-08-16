package com.pathlocked.draft;

public enum DraftCategory
{
	REGION,
	MONSTER,
	/**
	 * An item-tag unlock ("Bronze tier", "Basic food") — a class of items
	 * unlocked as a unit.
	 */
	ITEM,
	/**
	 * A skill keystone — every tenth choice, the build-defining pick.
	 */
	SKILL,
	/**
	 * Every fifth choice: offers sampled from all categories at once.
	 */
	FREE
}
