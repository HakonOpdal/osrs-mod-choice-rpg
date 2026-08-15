package com.pathlocked.ui;

import com.pathlocked.PathlockedConfig;
import com.pathlocked.PathlockedPlugin;
import com.pathlocked.enforcement.RegionStatus;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Top-center banner: warns when standing in a locked/uncharted region and
 * announces when a draft choice is waiting in the side panel.
 */
public class StatusOverlay extends OverlayPanel
{
	private static final Color WARN = new Color(214, 108, 86);
	private static final Color READY = new Color(126, 176, 132);
	private static final Color INFO = new Color(180, 175, 160);

	private final PathlockedPlugin plugin;
	private final PathlockedConfig config;

	public StatusOverlay(PathlockedPlugin plugin, PathlockedConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_CENTER);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showStatusOverlay() || !plugin.isProfileLoaded())
		{
			return null;
		}
		panelComponent.getChildren().clear();

		RegionStatus status = plugin.getCurrentRegionStatus();
		if (status == RegionStatus.LOCKED)
		{
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("LOCKED REGION")
				.color(WARN)
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left(plugin.getCurrentRegionName())
				.right("leave or unlock")
				.build());
		}
		else if (status == RegionStatus.UNCHARTED)
		{
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("Uncharted area")
				.color(INFO)
				.build());
		}

		if (plugin.hasPendingDraft())
		{
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("Choice ready!")
				.color(READY)
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Open the Pathlocked panel")
				.build());
		}

		if (panelComponent.getChildren().isEmpty())
		{
			return null;
		}
		return super.render(graphics);
	}
}
