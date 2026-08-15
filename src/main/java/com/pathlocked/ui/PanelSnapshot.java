package com.pathlocked.ui;

import com.pathlocked.draft.DraftOption;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Immutable view of the profile built on the client thread and handed to the
 * Swing panel, so the two threads never share mutable state.
 */
@Value
@Builder
public class PanelSnapshot
{
	boolean loggedIn;
	long totalPoints;
	long availablePoints;
	long nextCost;
	int choiceIndex;
	/**
	 * Null when no draft is pending.
	 */
	List<DraftOption> offers;
	int rerollsLeft;
	int regionsUnlocked;
	int regionsTotal;
	int monstersUnlocked;
	int monstersTotal;
	List<String> recentHistory;
	long illegalKills;
	long violationTicks;
}
