package com.pathlocked.ui;

import com.pathlocked.draft.DraftOption;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class PathlockedPanel extends PluginPanel
{
	public interface Actions
	{
		/**
		 * @param expectedName the option name the player saw on the card, used
		 * to reject clicks that raced a reroll
		 */
		void pickOption(int index, String expectedName);

		void rerollDraft();
	}

	private final Actions actions;

	private final JLabel statusLabel = new JLabel();
	private final JLabel pointsLabel = new JLabel();
	private final JProgressBar progressBar = new JProgressBar(0, 100);
	private final JPanel draftSection = new JPanel();
	private final JLabel unlocksLabel = new JLabel();
	private final JTextArea historyArea = new JTextArea();

	public PathlockedPanel(Actions actions)
	{
		// wrap=true: PluginPanel provides the scroll pane, so FREE drafts
		// (up to 6 cards) and long history never clip off-screen.
		super(true);
		this.actions = actions;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Pathlocked");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		column.add(title);
		column.add(Box.createVerticalStrut(8));

		statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		column.add(statusLabel);
		column.add(Box.createVerticalStrut(8));

		pointsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		column.add(pointsLabel);
		column.add(Box.createVerticalStrut(4));

		progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		progressBar.setStringPainted(true);
		progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		column.add(progressBar);
		column.add(Box.createVerticalStrut(10));

		draftSection.setLayout(new BoxLayout(draftSection, BoxLayout.Y_AXIS));
		draftSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
		draftSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		column.add(draftSection);
		column.add(Box.createVerticalStrut(10));

		unlocksLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		column.add(unlocksLabel);
		column.add(Box.createVerticalStrut(10));

		JLabel historyTitle = new JLabel("Recent choices");
		historyTitle.setFont(historyTitle.getFont().deriveFont(Font.BOLD));
		historyTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		column.add(historyTitle);

		historyArea.setEditable(false);
		historyArea.setLineWrap(true);
		historyArea.setWrapStyleWord(true);
		historyArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		historyArea.setAlignmentX(Component.LEFT_ALIGNMENT);
		column.add(historyArea);

		add(column, BorderLayout.NORTH);
	}

	public void refresh(PanelSnapshot snapshot)
	{
		SwingUtilities.invokeLater(() -> apply(snapshot));
	}

	private void apply(PanelSnapshot snapshot)
	{
		if (!snapshot.isLoggedIn())
		{
			statusLabel.setText("Log in to start your run.");
			pointsLabel.setText(" ");
			progressBar.setValue(0);
			progressBar.setString(" ");
			draftSection.removeAll();
			unlocksLabel.setText(" ");
			historyArea.setText("");
			revalidateAll();
			return;
		}

		statusLabel.setText("Choice " + (snapshot.getChoiceIndex() + 1));
		pointsLabel.setText(String.format("Points: %,d available (%,d earned)",
			snapshot.getAvailablePoints(), snapshot.getTotalPoints()));

		long cost = Math.max(1, snapshot.getNextCost());
		int percent = (int) Math.min(100, snapshot.getAvailablePoints() * 100 / cost);
		progressBar.setValue(percent);
		progressBar.setString(String.format("%,d / %,d to next choice",
			Math.min(snapshot.getAvailablePoints(), cost), cost));

		draftSection.removeAll();
		if (snapshot.getOffers() != null)
		{
			JLabel draftTitle = new JLabel("Pick your unlock:");
			draftTitle.setFont(draftTitle.getFont().deriveFont(Font.BOLD));
			draftTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
			draftSection.add(draftTitle);
			draftSection.add(Box.createVerticalStrut(5));

			for (int i = 0; i < snapshot.getOffers().size(); i++)
			{
				DraftOption option = snapshot.getOffers().get(i);
				JButton card = new JButton("<html><b>" + option.getName() + "</b><br>"
					+ option.getDetail() + "</html>");
				card.setAlignmentX(Component.LEFT_ALIGNMENT);
				card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
				final int index = i;
				final String expectedName = option.getName();
				card.addActionListener(e -> actions.pickOption(index, expectedName));
				draftSection.add(card);
				draftSection.add(Box.createVerticalStrut(5));
			}

			JButton rerollButton = new JButton(snapshot.getRerollsLeft() > 0
				? "Reroll (" + snapshot.getRerollsLeft() + " left)"
				: "No rerolls left");
			rerollButton.setEnabled(snapshot.getRerollsLeft() > 0);
			rerollButton.setAlignmentX(Component.LEFT_ALIGNMENT);
			rerollButton.addActionListener(e -> actions.rerollDraft());
			draftSection.add(rerollButton);
		}

		unlocksLabel.setText(String.format("<html>Regions: %d / %d unlocked<br>Monsters: %d / %d unlocked<br>Illegal kills: %d · Ticks in locked regions: %d</html>",
			snapshot.getRegionsUnlocked(), snapshot.getRegionsTotal(),
			snapshot.getMonstersUnlocked(), snapshot.getMonstersTotal(),
			snapshot.getIllegalKills(), snapshot.getViolationTicks()));

		historyArea.setText(snapshot.getRecentHistory() == null ? ""
			: String.join("\n", snapshot.getRecentHistory()));

		revalidateAll();
	}

	private void revalidateAll()
	{
		draftSection.revalidate();
		draftSection.repaint();
		revalidate();
		repaint();
	}
}
