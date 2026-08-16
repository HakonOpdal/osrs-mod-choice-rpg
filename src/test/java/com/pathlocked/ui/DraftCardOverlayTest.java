package com.pathlocked.ui;

import com.pathlocked.draft.DraftCategory;
import com.pathlocked.draft.DraftOption;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Headless render + hit-test coverage for {@link DraftCardOverlay}. Uses a real
 * {@link BufferedImage} so no RuneLite {@code Client} needs mocking, and dumps
 * preview PNGs under {@code build/card-overlay-previews/} for eyeballing.
 */
public class DraftCardOverlayTest
{
	private static final DraftCardOverlay.Fonts FONTS = DraftCardOverlay.Fonts.resolve();

	private static final class RecordingActions implements PathlockedPanel.Actions
	{
		int pickIndex = -1;
		String pickName = null;
		int rerolls = 0;

		@Override
		public void pickOption(int index, String expectedName)
		{
			pickIndex = index;
			pickName = expectedName;
		}

		@Override
		public void rerollDraft()
		{
			rerolls++;
		}
	}

	private static DraftOption region(String name, int tier)
	{
		return new DraftOption(DraftCategory.REGION, name, 1234, tier, "Region · tier " + tier);
	}

	private static DraftOption monster(String name, int tier, int level)
	{
		return new DraftOption(DraftCategory.MONSTER, name, 0, tier, "Monster · level " + level);
	}

	private static PanelSnapshot snapshot(List<DraftOption> offers, int rerollsLeft, int choiceIndex)
	{
		return PanelSnapshot.builder()
			.loggedIn(true)
			.offers(offers)
			.rerollsLeft(rerollsLeft)
			.choiceIndex(choiceIndex)
			.build();
	}

	private static DraftCardOverlay.Rendered render(int w, int h, PanelSnapshot snap,
		RecordingActions actions, String pngName)
	{
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		DraftCardOverlay.Rendered rendered = DraftCardOverlay.paint(g, w, h, snap, FONTS, actions, -1);
		g.dispose();
		if (pngName != null)
		{
			try
			{
				File dir = new File("build/card-overlay-previews");
				//noinspection ResultOfMethodCallIgnored
				dir.mkdirs();
				ImageIO.write(image, "png", new File(dir, pngName));
			}
			catch (Exception ignored)
			{
				// Preview dump is best-effort; never fail the test on IO.
			}
		}
		return rendered;
	}

	@Test
	public void standardDraftLaysOutThreeCardsPlusReroll()
	{
		List<DraftOption> offers = Arrays.asList(
			region("Varrock", 1),
			region("Al Kharid", 2),
			region("Morytania Swamp", 4));
		RecordingActions actions = new RecordingActions();
		DraftCardOverlay.Rendered rendered = render(765, 503, snapshot(offers, 1, 0), actions,
			"standard-3-region.png");

		// 3 card regions + 1 reroll pill.
		assertEquals(4, rendered.clickables.size());
		assertNotNull(rendered.surface);
		for (DraftCardOverlay.Clickable clickable : rendered.clickables)
		{
			assertTrue("card inside canvas", new Rectangle(0, 0, 765, 503).contains(clickable.bounds));
		}
	}

	@Test
	public void freeDraftLaysOutSixMixedCards()
	{
		List<DraftOption> offers = new ArrayList<>(Arrays.asList(
			region("Varrock", 1),
			monster("Goblin", 1, 2),
			region("Wilderness", 6),
			monster("Abyssal demon", 5, 124),
			region("Fossil Island", 4),
			monster("Zulrah", 6, 725)));
		RecordingActions actions = new RecordingActions();
		DraftCardOverlay.Rendered rendered = render(1280, 720, snapshot(offers, 0, 4), actions,
			"free-6-mixed.png");

		// 6 cards, and no reroll pill because zero rerolls remain.
		assertEquals(6, rendered.clickables.size());
		assertEquals("6 cards should lay out in 3 columns", 3, DraftCardOverlay.columnsFor(6, 1280));
	}

	@Test
	public void clickingCardFiresPickWithMatchingIndexAndName()
	{
		List<DraftOption> offers = Arrays.asList(
			region("Varrock", 1),
			monster("Goblin", 1, 2),
			region("Karamja", 3));
		RecordingActions actions = new RecordingActions();
		DraftCardOverlay.Rendered rendered = render(765, 503, snapshot(offers, 2, 0), actions, null);

		DraftCardOverlay.Clickable secondCard = rendered.clickables.get(1);
		secondCard.onClick.run();
		assertEquals(1, actions.pickIndex);
		assertEquals("Goblin", actions.pickName);
	}

	@Test
	public void clickingRerollPillFiresReroll()
	{
		List<DraftOption> offers = Arrays.asList(region("Varrock", 1), region("Lumbridge", 1));
		RecordingActions actions = new RecordingActions();
		DraftCardOverlay.Rendered rendered = render(765, 503, snapshot(offers, 1, 0), actions, null);

		// Reroll pill is the last clickable when a reroll is available.
		DraftCardOverlay.Clickable reroll = rendered.clickables.get(rendered.clickables.size() - 1);
		reroll.onClick.run();
		assertEquals(1, actions.rerolls);
		assertEquals("reroll must not be mistaken for a pick", -1, actions.pickIndex);
	}

	@Test
	public void rendersWithoutThrowingAtSmallClientSize()
	{
		List<DraftOption> offers = Arrays.asList(
			region("Varrock", 2), monster("Goblin", 1, 2), region("Karamja", 3));
		DraftCardOverlay.Rendered rendered = render(520, 340, snapshot(offers, 1, 0),
			new RecordingActions(), "small-client.png");
		assertNotNull(rendered.surface);
		assertEquals(4, rendered.clickables.size());
	}

	@Test
	public void cardBoundsDoNotOverlap()
	{
		List<DraftOption> offers = Arrays.asList(
			region("Varrock", 1), region("Al Kharid", 2), region("Morytania", 4));
		DraftCardOverlay.Rendered rendered = render(765, 503, snapshot(offers, 1, 0),
			new RecordingActions(), null);
		List<DraftCardOverlay.Clickable> cards = rendered.clickables;
		for (int i = 0; i < 3; i++)
		{
			for (int j = i + 1; j < 3; j++)
			{
				assertTrue("cards " + i + " and " + j + " overlap",
					!cards.get(i).bounds.intersects(cards.get(j).bounds));
			}
		}
	}
}
