package com.pathlocked.ui;

import com.pathlocked.draft.DraftCategory;
import com.pathlocked.draft.DraftOption;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Center-screen, trading-card style draft picker drawn straight onto the game
 * canvas. When a draft is pending it shows each offer as a large card (region
 * vs monster get distinct visual treatments) plus a reroll pill.
 *
 * <p><b>Clickability.</b> RuneLite overlays are not clickable on their own, so
 * we register a {@link MouseListener} (see {@link #getMouseListener()}) and
 * hit-test the card rectangles this overlay laid out on its last render. A
 * press inside a card fires {@code actions.pickOption(index, name)}; a press
 * anywhere on the card surface is consumed so it never leaks through as a
 * walk-here/menu click on the world beneath. The picks stay identified by
 * {@code (index, expectedName)}, so a click racing a reroll is rejected by the
 * plugin exactly as a panel click would be.
 */
public class DraftCardOverlay extends Overlay implements DraftCardPresenter
{
	private static final int CARD_W = 138;
	private static final int CARD_H = 182;
	private static final int CARD_GAP = 16;
	private static final int HEADER_H = 26;
	private static final int TITLE_BAND = 46;
	private static final int REROLL_BAND = 48;
	private static final int MARGIN = 24;
	private static final int ARC = 14;

	private static final Color SCRIM = new Color(0, 0, 0, 150);
	private static final Color CARD_BG = new Color(34, 32, 28);
	private static final Color CARD_BG_HOVER = new Color(48, 45, 38);
	private static final Color SHADOW = new Color(0, 0, 0, 120);
	private static final Color TITLE_GOLD = new Color(220, 190, 120);
	private static final Color SUBTLE = new Color(176, 170, 156);
	private static final Color NAME_COLOR = new Color(238, 234, 224);
	private static final Color PIP_ON = new Color(226, 200, 130);
	private static final Color PIP_OFF = new Color(90, 84, 74);

	private static final Color REGION_ACCENT = new Color(104, 164, 122);
	private static final Color MONSTER_ACCENT = new Color(198, 92, 78);

	private final Client client;
	private final PathlockedPanel.Actions actions;

	private volatile PanelSnapshot snapshot;
	private volatile boolean enabled = true;

	/** Hit map from the last render; replaced wholesale so readers see a consistent list. */
	private volatile List<Clickable> clickables = Collections.emptyList();
	/** Bounding box of the whole card surface, used to swallow click-through. */
	private volatile Rectangle surface = null;
	/** Index of the card under the cursor, or -1. Updated from the mouse thread. */
	private volatile int hoveredIndex = -1;

	private Fonts fonts;

	private final MouseListener mouseListener = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent event)
		{
			return handlePress(event);
		}

		@Override
		public MouseEvent mouseClicked(MouseEvent event)
		{
			// Consume the follow-up click too so it can never reach the world.
			return isInsideSurface(event) ? consume(event) : event;
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent event)
		{
			return isInsideSurface(event) ? consume(event) : event;
		}

		@Override
		public MouseEvent mouseMoved(MouseEvent event)
		{
			updateHover(event.getPoint());
			return event;
		}

		@Override
		public MouseEvent mouseDragged(MouseEvent event)
		{
			updateHover(event.getPoint());
			return event;
		}
	};

	public DraftCardOverlay(Client client, PathlockedPanel.Actions actions)
	{
		this.client = client;
		this.actions = actions;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	/** The mouse listener the plugin must register/unregister with {@code MouseManager}. */
	public MouseListener getMouseListener()
	{
		return mouseListener;
	}

	@Override
	public void setSnapshot(PanelSnapshot snapshot)
	{
		this.snapshot = snapshot;
		if (!isActive(snapshot))
		{
			// Drop stale hit regions the moment the draft clears.
			clickables = Collections.emptyList();
			surface = null;
			hoveredIndex = -1;
		}
	}

	@Override
	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
		if (!enabled)
		{
			clickables = Collections.emptyList();
			surface = null;
			hoveredIndex = -1;
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		PanelSnapshot snap = this.snapshot;
		if (!enabled || !isActive(snap))
		{
			return null;
		}
		int canvasW = client.getCanvasWidth();
		int canvasH = client.getCanvasHeight();
		if (canvasW <= 0 || canvasH <= 0)
		{
			return null;
		}
		if (fonts == null)
		{
			fonts = Fonts.resolve();
		}

		Rendered rendered = paint(graphics, canvasW, canvasH, snap, fonts, actions, hoveredIndex);
		this.clickables = rendered.clickables;
		this.surface = rendered.surface;
		return null;
	}

	// ---- rendering ---------------------------------------------------------

	/**
	 * Draws the whole card surface and returns the click map + outer bounds.
	 * Package-private and free of any client dependency so a headless test can
	 * drive it against a {@link java.awt.image.BufferedImage}.
	 */
	static Rendered paint(Graphics2D graphics, int canvasW, int canvasH, PanelSnapshot snap,
		Fonts fonts, PathlockedPanel.Actions actions, int hoveredIndex)
	{
		List<DraftOption> offers = snap.getOffers();
		int count = offers.size();

		int columns = columnsFor(count, canvasW);
		int rows = (count + columns - 1) / columns;
		int gridW = columns * CARD_W + (columns - 1) * CARD_GAP;
		int gridH = rows * CARD_H + (rows - 1) * CARD_GAP;

		int totalH = TITLE_BAND + gridH + REROLL_BAND;
		int gridLeft = (canvasW - gridW) / 2;
		int top = Math.max(MARGIN, (canvasH - totalH) / 2);
		int gridTop = top + TITLE_BAND;

		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			Rectangle surface = new Rectangle(
				Math.min(gridLeft, (canvasW - 260) / 2) - 14,
				top - 8,
				Math.max(gridW, 260) + 28,
				totalH + 16);
			g.setColor(SCRIM);
			g.fill(new RoundRectangle2D.Float(surface.x, surface.y, surface.width, surface.height, 22, 22));

			// Title band.
			g.setFont(fonts.title);
			drawCentered(g, "CHOOSE YOUR UNLOCK", canvasW / 2, top + 16, TITLE_GOLD);
			g.setFont(fonts.small);
			String sub = "Choice " + (snap.getChoiceIndex() + 1) + "   ·   click a card to unlock it";
			drawCentered(g, sub, canvasW / 2, top + 34, SUBTLE);

			List<Clickable> clickables = new ArrayList<>();
			for (int i = 0; i < count; i++)
			{
				int row = i / columns;
				int col = i % columns;
				int inRow = Math.min(columns, count - row * columns);
				int rowW = inRow * CARD_W + (inRow - 1) * CARD_GAP;
				int rowLeft = (canvasW - rowW) / 2;
				int x = rowLeft + col * (CARD_W + CARD_GAP);
				int y = gridTop + row * (CARD_H + CARD_GAP);

				DraftOption option = offers.get(i);
				drawCard(g, x, y, option, fonts, i == hoveredIndex);

				final int index = i;
				final String expectedName = option.getName();
				clickables.add(new Clickable(new Rectangle(x, y, CARD_W, CARD_H),
					() -> actions.pickOption(index, expectedName)));
			}

			// Reroll pill.
			int rerolls = snap.getRerollsLeft();
			boolean canReroll = rerolls > 0;
			int pillW = Math.min(240, Math.max(180, gridW));
			int pillH = 34;
			int pillX = (canvasW - pillW) / 2;
			int pillY = gridTop + gridH + (REROLL_BAND - pillH) / 2;
			Rectangle pill = new Rectangle(pillX, pillY, pillW, pillH);
			drawReroll(g, pill, canReroll ? "Reroll (" + rerolls + " left)" : "No rerolls left",
				canReroll, fonts);
			if (canReroll)
			{
				clickables.add(new Clickable(pill, actions::rerollDraft));
			}

			return new Rendered(clickables, surface);
		}
		finally
		{
			g.dispose();
		}
	}

	private static void drawCard(Graphics2D g, int x, int y, DraftOption option, Fonts fonts, boolean hovered)
	{
		boolean region = option.getCategory() == DraftCategory.REGION;
		Color accent = region ? REGION_ACCENT : MONSTER_ACCENT;

		// Shadow.
		g.setColor(SHADOW);
		g.fill(new RoundRectangle2D.Float(x + 3, y + 4, CARD_W, CARD_H, ARC, ARC));

		// Body.
		Shape body = new RoundRectangle2D.Float(x, y, CARD_W, CARD_H, ARC, ARC);
		g.setColor(hovered ? CARD_BG_HOVER : CARD_BG);
		g.fill(body);

		// Header band (accent), clipped to the rounded top.
		Shape oldClip = g.getClip();
		g.setClip(body);
		g.setColor(accent);
		g.fillRect(x, y, CARD_W, HEADER_H);
		g.setClip(oldClip);

		g.setColor(darken(accent, 0.65f));
		g.setFont(fonts.headerLabel);
		drawCentered(g, region ? "REGION" : "MONSTER", x + CARD_W / 2, y + 17,
			new Color(20, 22, 18));

		// Emblem.
		drawEmblem(g, x + CARD_W / 2, y + HEADER_H + 34, accent, region);

		// Name (wrapped, up to 3 lines).
		g.setFont(fonts.name);
		List<String> nameLines = wrap(g, option.getName(), CARD_W - 18, 3);
		int ny = y + HEADER_H + 74;
		for (String line : nameLines)
		{
			drawCentered(g, line, x + CARD_W / 2, ny, NAME_COLOR);
			ny += g.getFontMetrics().getHeight();
		}

		// Tier pips.
		drawTierPips(g, x + CARD_W / 2, y + CARD_H - 40, option.getTier());

		// Detail line (wrapped, 2 lines).
		g.setFont(fonts.small);
		List<String> detailLines = wrap(g, option.getDetail(), CARD_W - 16, 2);
		int dy = y + CARD_H - 20;
		for (int i = detailLines.size() - 1; i >= 0; i--)
		{
			drawCentered(g, detailLines.get(i), x + CARD_W / 2, dy, SUBTLE);
			dy -= g.getFontMetrics().getHeight();
		}

		// Border (brighter when hovered).
		g.setStroke(new BasicStroke(hovered ? 2.4f : 1.4f));
		g.setColor(hovered ? brighten(accent, 0.35f) : darken(accent, 0.85f));
		g.draw(body);
	}

	private static void drawEmblem(Graphics2D g, int cx, int cy, Color accent, boolean region)
	{
		g.setColor(darken(accent, 0.75f));
		if (region)
		{
			// Compass diamond.
			Polygon diamond = new Polygon(
				new int[]{cx, cx + 16, cx, cx - 16},
				new int[]{cy - 18, cy, cy + 18, cy}, 4);
			g.fill(diamond);
			g.setColor(brighten(accent, 0.5f));
			g.setStroke(new BasicStroke(1.6f));
			g.drawLine(cx, cy - 18, cx, cy + 18);
			g.drawLine(cx - 16, cy, cx + 16, cy);
		}
		else
		{
			// Blunt fang/skull mark: circle head + tapered jaw.
			g.fillOval(cx - 15, cy - 18, 30, 26);
			Polygon jaw = new Polygon(
				new int[]{cx - 9, cx + 9, cx},
				new int[]{cy + 4, cy + 4, cy + 18}, 3);
			g.fill(jaw);
			g.setColor(new Color(20, 22, 18));
			g.fillOval(cx - 8, cy - 10, 6, 7);
			g.fillOval(cx + 2, cy - 10, 6, 7);
		}
	}

	private static void drawTierPips(Graphics2D g, int cx, int y, int tier)
	{
		int shown = Math.max(1, Math.min(6, tier));
		int pipW = 9;
		int gap = 4;
		int totalW = shown * pipW + (shown - 1) * gap;
		int left = cx - totalW / 2;
		for (int i = 0; i < shown; i++)
		{
			int px = left + i * (pipW + gap);
			Polygon pip = new Polygon(
				new int[]{px + pipW / 2, px + pipW, px + pipW / 2, px},
				new int[]{y, y + pipW / 2, y + pipW, y + pipW / 2}, 4);
			g.setColor(i < tier ? PIP_ON : PIP_OFF);
			g.fill(pip);
		}
	}

	private static void drawReroll(Graphics2D g, Rectangle pill, String label, boolean enabled, Fonts fonts)
	{
		Shape shape = new RoundRectangle2D.Float(pill.x, pill.y, pill.width, pill.height,
			pill.height, pill.height);
		g.setColor(enabled ? new Color(58, 54, 46) : new Color(40, 38, 34));
		g.fill(shape);
		g.setStroke(new BasicStroke(1.4f));
		g.setColor(enabled ? new Color(150, 140, 118) : new Color(78, 74, 66));
		g.draw(shape);
		g.setFont(fonts.name);
		drawCentered(g, label, pill.x + pill.width / 2, pill.y + pill.height / 2 + 5,
			enabled ? new Color(232, 224, 206) : new Color(120, 114, 104));
	}

	// ---- geometry helpers --------------------------------------------------

	static int columnsFor(int count, int canvasW)
	{
		int fit = Math.max(1, (canvasW - 2 * MARGIN + CARD_GAP) / (CARD_W + CARD_GAP));
		int desired = count <= 3 ? count : 3;
		return Math.max(1, Math.min(desired, fit));
	}

	private static boolean isActive(PanelSnapshot snap)
	{
		return snap != null && snap.getOffers() != null && !snap.getOffers().isEmpty();
	}

	// ---- text helpers ------------------------------------------------------

	private static void drawCentered(Graphics2D g, String text, int cx, int baseline, Color color)
	{
		int w = g.getFontMetrics().stringWidth(text);
		g.setColor(color);
		g.drawString(text, cx - w / 2, baseline);
	}

	private static List<String> wrap(Graphics2D g, String text, int maxWidth, int maxLines)
	{
		List<String> lines = new ArrayList<>();
		if (text == null || text.isEmpty())
		{
			return lines;
		}
		FontRenderContext frc = g.getFontRenderContext();
		Font font = g.getFont();
		String[] words = text.split("\\s+");
		StringBuilder current = new StringBuilder();
		for (String word : words)
		{
			String candidate = current.length() == 0 ? word : current + " " + word;
			if (font.getStringBounds(candidate, frc).getWidth() > maxWidth && current.length() > 0)
			{
				lines.add(current.toString());
				current = new StringBuilder(word);
				if (lines.size() == maxLines - 1)
				{
					break;
				}
			}
			else
			{
				current = new StringBuilder(candidate);
			}
		}
		String tail = current.toString();
		// Fold any remaining words into the last allowed line and ellipsize.
		int consumed = 0;
		for (String line : lines)
		{
			consumed += line.split("\\s+").length;
		}
		if (consumed < words.length)
		{
			StringBuilder rest = new StringBuilder();
			for (int i = consumed; i < words.length; i++)
			{
				rest.append(i == consumed ? "" : " ").append(words[i]);
			}
			tail = rest.toString();
		}
		lines.add(ellipsize(g, tail, maxWidth));
		return lines;
	}

	private static String ellipsize(Graphics2D g, String text, int maxWidth)
	{
		if (g.getFontMetrics().stringWidth(text) <= maxWidth)
		{
			return text;
		}
		String ellipsis = "…";
		StringBuilder builder = new StringBuilder(text);
		while (builder.length() > 1
			&& g.getFontMetrics().stringWidth(builder + ellipsis) > maxWidth)
		{
			builder.deleteCharAt(builder.length() - 1);
		}
		return builder.append(ellipsis).toString();
	}

	private static Color darken(Color c, float factor)
	{
		return new Color(
			Math.round(c.getRed() * factor),
			Math.round(c.getGreen() * factor),
			Math.round(c.getBlue() * factor));
	}

	private static Color brighten(Color c, float amount)
	{
		return new Color(
			Math.min(255, Math.round(c.getRed() + (255 - c.getRed()) * amount)),
			Math.min(255, Math.round(c.getGreen() + (255 - c.getGreen()) * amount)),
			Math.min(255, Math.round(c.getBlue() + (255 - c.getBlue()) * amount)));
	}

	// ---- mouse handling ----------------------------------------------------

	private MouseEvent handlePress(MouseEvent event)
	{
		if (!enabled || !isActive(snapshot))
		{
			return event;
		}
		Point point = event.getPoint();
		for (Clickable clickable : clickables)
		{
			if (clickable.bounds.contains(point))
			{
				clickable.onClick.run();
				return consume(event);
			}
		}
		// Consume any press that lands on the card surface so it never reaches
		// the world as a walk-here click.
		return isInsideSurface(event) ? consume(event) : event;
	}

	private void updateHover(Point point)
	{
		if (!enabled || !isActive(snapshot))
		{
			hoveredIndex = -1;
			return;
		}
		int found = -1;
		List<Clickable> current = clickables;
		for (int i = 0; i < current.size(); i++)
		{
			Rectangle bounds = current.get(i).bounds;
			// Card entries mirror offer order and come first; the reroll pill is
			// last, so only indices < offer count map to a card hover.
			if (bounds.contains(point) && i < offerCount())
			{
				found = i;
				break;
			}
		}
		hoveredIndex = found;
	}

	private int offerCount()
	{
		PanelSnapshot snap = snapshot;
		return snap == null || snap.getOffers() == null ? 0 : snap.getOffers().size();
	}

	private boolean isInsideSurface(MouseEvent event)
	{
		Rectangle current = surface;
		return current != null && current.contains(event.getPoint());
	}

	private static MouseEvent consume(MouseEvent event)
	{
		event.consume();
		return event;
	}

	// ---- value holders -----------------------------------------------------

	static final class Clickable
	{
		final Rectangle bounds;
		final Runnable onClick;

		Clickable(Rectangle bounds, Runnable onClick)
		{
			this.bounds = bounds;
			this.onClick = onClick;
		}
	}

	static final class Rendered
	{
		final List<Clickable> clickables;
		final Rectangle surface;

		Rendered(List<Clickable> clickables, Rectangle surface)
		{
			this.clickables = clickables;
			this.surface = surface;
		}
	}

	/**
	 * Fonts resolved once. Falls back to plain AWT fonts if the RuneLite font
	 * bundle is unavailable (e.g. a headless unit test), so rendering never
	 * throws on a missing font resource.
	 */
	static final class Fonts
	{
		final Font title;
		final Font name;
		final Font headerLabel;
		final Font small;

		private Fonts(Font base, Font bold)
		{
			this.title = bold.deriveFont(Font.BOLD, 16f);
			this.name = bold.deriveFont(Font.BOLD, 13f);
			this.headerLabel = bold.deriveFont(Font.BOLD, 11f);
			this.small = base.deriveFont(Font.PLAIN, 11f);
		}

		static Fonts resolve()
		{
			try
			{
				return new Fonts(FontManager.getRunescapeFont(), FontManager.getRunescapeBoldFont());
			}
			catch (Throwable t)
			{
				Font fallback = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
				return new Fonts(fallback, fallback);
			}
		}
	}
}
