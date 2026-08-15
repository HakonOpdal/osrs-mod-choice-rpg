package com.pathlocked.enforcement;

import com.pathlocked.PathlockedConfig;
import com.pathlocked.PathlockedPlugin;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Tints ground tiles that belong to locked map squares, within a small radius
 * of the player (Tileman-style scene shading, kept cheap).
 */
public class RegionLockOverlay extends Overlay
{
	private static final int RADIUS = 10;
	private static final Color LOCKED_FILL = new Color(138, 75, 60, 45);

	private final Client client;
	private final PathlockedPlugin plugin;
	private final PathlockedConfig config;

	public RegionLockOverlay(Client client, PathlockedPlugin plugin, PathlockedConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.shadeLockedTiles())
		{
			return null;
		}
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		WorldPoint centre = player.getWorldLocation();
		int baseX = centre.getX();
		int baseY = centre.getY();

		// The 21x21 box overlaps at most 4 map squares; bail out cheaply when
		// none of them is locked instead of checking every tile per frame.
		Set<Integer> lockedRegionIds = new HashSet<>();
		for (int cornerX : new int[]{baseX - RADIUS, baseX + RADIUS})
		{
			for (int cornerY : new int[]{baseY - RADIUS, baseY + RADIUS})
			{
				int regionId = ((cornerX >> 6) << 8) | (cornerY >> 6);
				if (plugin.regionStatus(regionId) == RegionStatus.LOCKED)
				{
					lockedRegionIds.add(regionId);
				}
			}
		}
		if (lockedRegionIds.isEmpty())
		{
			return null;
		}

		graphics.setColor(LOCKED_FILL);
		for (int dx = -RADIUS; dx <= RADIUS; dx++)
		{
			for (int dy = -RADIUS; dy <= RADIUS; dy++)
			{
				int x = baseX + dx;
				int y = baseY + dy;
				if (!lockedRegionIds.contains(((x >> 6) << 8) | (y >> 6)))
				{
					continue;
				}
				LocalPoint localPoint = LocalPoint.fromWorld(client, x, y);
				if (localPoint == null)
				{
					continue;
				}
				Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
				if (poly != null)
				{
					graphics.fill(poly);
				}
			}
		}
		return null;
	}
}
