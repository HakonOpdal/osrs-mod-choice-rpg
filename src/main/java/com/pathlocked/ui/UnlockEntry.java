package com.pathlocked.ui;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * One row of the unlock-tree tab: an unlockable and whether this profile owns
 * it, grouped by section ("Regions", "Monsters", "Items", "Skills").
 */
@Value
@AllArgsConstructor
public class UnlockEntry
{
	String section;
	String name;
	boolean unlocked;
}
