package com.pathlocked.content;

import java.util.List;
import lombok.Data;

@Data
public class MonsterDef
{
	private String name;
	private int combatLevel;
	private int tier;
	private List<String> regions;
	private String notes;

	public String key()
	{
		return name.toLowerCase();
	}
}
