package com.pathlocked.content;

import lombok.Data;

@Data
public class RegionDef
{
	private int rx;
	private int ry;
	private String name;
	private int tier;
	private String notes;

	public int id()
	{
		return (rx << 8) | ry;
	}
}
