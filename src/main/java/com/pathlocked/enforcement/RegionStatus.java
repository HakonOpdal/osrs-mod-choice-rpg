package com.pathlocked.enforcement;

public enum RegionStatus
{
	UNLOCKED,
	LOCKED,
	/**
	 * Not in the content data (dungeons, members areas): informational only,
	 * never counted as a violation in v0.1.
	 */
	UNCHARTED
}
