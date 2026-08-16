package com.pathlocked.content;

import java.util.List;
import lombok.Data;

@Data
public class RegionDef
{
	private int rx;
	private int ry;
	private String name;
	private int tier;
	private String notes;

	/**
	 * Optional explicit list of underground map squares owned by this region's
	 * unlock, each entry a two-element {@code [rx, ry]} pair. Overrides the
	 * plugin's generic {@code ry−100} fallback for dungeons whose underground
	 * squares do not sit directly beneath their surface owner (e.g. the Dwarven
	 * Mine, which spreads under several surface columns). Null/empty means the
	 * region owns no explicitly-mapped underground squares.
	 */
	private List<List<Integer>> underground;

	public int id()
	{
		return (rx << 8) | ry;
	}
}
