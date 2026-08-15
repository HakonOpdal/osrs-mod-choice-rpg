package com.pathlocked.draft;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DraftOption
{
	private DraftCategory category;
	private String name;
	/**
	 * Region id for REGION options; 0 for monsters.
	 */
	private int regionId;
	private int tier;
	/**
	 * Short human-readable descriptor shown on the draft card.
	 */
	private String detail;
}
