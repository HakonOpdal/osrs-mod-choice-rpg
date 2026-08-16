package com.pathlocked.enforcement;

import com.pathlocked.PathlockedConfig;
import com.pathlocked.PathlockedPlugin;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Greys out items whose every containing tag is still locked, in the inventory
 * and bank, with a small padlock glyph in the corner. Render-only: the actual
 * use/equip restriction is menu-entry enforcement in the plugin class.
 */
public class ItemLockOverlay extends WidgetItemOverlay
{
	private static final Color SHADE = new Color(0, 0, 0, 130);
	private static final Color LOCK_BODY = new Color(210, 210, 210, 220);

	private final PathlockedPlugin plugin;
	private final PathlockedConfig config;

	public ItemLockOverlay(PathlockedPlugin plugin, PathlockedConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		showOnInventory();
		showOnBank();
		showOnEquipment();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.greyLockedItems() || !plugin.isItemIdLocked(itemId))
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.setColor(SHADE);
		graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

		// Minimal padlock: shackle arc over a body square, bottom-right corner.
		int size = 8;
		int x = bounds.x + bounds.width - size - 2;
		int y = bounds.y + bounds.height - size - 2;
		graphics.setColor(LOCK_BODY);
		graphics.fillRect(x, y + 3, size, size - 3);
		graphics.drawArc(x + 1, y - 2, size - 2, 7, 0, 180);
	}
}
