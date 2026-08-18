package com.pathlocked.content;

import java.util.List;
import lombok.Data;

/**
 * One draftable item tag: a named class of items ("Bronze tier", "Basic food")
 * unlocked as a unit. Items are matched by wiki name, case-insensitively, like
 * monsters — see docs/item-tags-schema.md.
 */
@Data
public class ItemTagDef
{
	private String name;

	/**
	 * 1..7 for the metal-equipment progression (bronze → rune), null for tags
	 * with no natural order (food, tools, ...). Tiered tags form a draft chain:
	 * tier t is only offered once tier t−1 is unlocked.
	 */
	private Integer tier;

	private String category;
	private List<String> itemNames;
	private String notes;

	public String key()
	{
		return name.toLowerCase();
	}
}
