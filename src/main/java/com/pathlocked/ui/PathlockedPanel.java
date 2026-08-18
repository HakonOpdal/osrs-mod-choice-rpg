package com.pathlocked.ui;

import com.pathlocked.draft.DraftOption;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
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
	private final JTree unlockTree = new JTree();
	private final DefaultMutableTreeNode unlockRoot = new DefaultMutableTreeNode("Unlocks");
	private List<UnlockEntry> lastTreeEntries;

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

		unlockTree.setModel(new DefaultTreeModel(unlockRoot));
		unlockTree.setRootVisible(false);
		unlockTree.setShowsRootHandles(true);
		unlockTree.setBackground(ColorScheme.DARK_GRAY_COLOR);
		unlockTree.setCellRenderer(new UnlockTreeRenderer());

		JPanel treeTab = new JPanel(new BorderLayout());
		treeTab.setBackground(ColorScheme.DARK_GRAY_COLOR);
		treeTab.add(unlockTree, BorderLayout.NORTH);

		JTabbedPane tabs = new JTabbedPane();
		tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tabs.addTab("Run", column);
		tabs.addTab("Unlocks", treeTab);

		add(tabs, BorderLayout.NORTH);
	}

	/**
	 * Category nodes as "Regions 5/83"; leaves green when owned, grey when
	 * locked. Kept free of icons — the color is the state.
	 */
	private static class UnlockTreeRenderer extends DefaultTreeCellRenderer
	{
		private static final Color UNLOCKED = new Color(122, 176, 134);
		private static final Color LOCKED = new Color(130, 130, 130);

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
			boolean expanded, boolean leaf, int row, boolean hasFocus)
		{
			super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
			setIcon(null);
			setBackgroundNonSelectionColor(ColorScheme.DARK_GRAY_COLOR);
			Object payload = ((DefaultMutableTreeNode) value).getUserObject();
			if (payload instanceof UnlockEntry)
			{
				UnlockEntry entry = (UnlockEntry) payload;
				setText(entry.getName());
				setForeground(entry.isUnlocked() ? UNLOCKED : LOCKED);
			}
			return this;
		}
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
			rebuildUnlockTree(null);
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

		unlocksLabel.setText(buildUnlocksSummary(snapshot));

		historyArea.setText(snapshot.getRecentHistory() == null ? ""
			: String.join("\n", snapshot.getRecentHistory()));

		rebuildUnlockTree(snapshot.getUnlockEntries());

		revalidateAll();
	}

	/**
	 * Per-section "owned / total" lines derived from the same entry list the
	 * unlock tree renders, so the two can never disagree.
	 */
	private static String buildUnlocksSummary(PanelSnapshot snapshot)
	{
		StringBuilder summary = new StringBuilder("<html>");
		if (snapshot.getUnlockEntries() != null)
		{
			Map<String, int[]> counts = new LinkedHashMap<>();
			for (UnlockEntry entry : snapshot.getUnlockEntries())
			{
				int[] ownedAndTotal = counts.computeIfAbsent(entry.getSection(), section -> new int[2]);
				ownedAndTotal[1]++;
				if (entry.isUnlocked())
				{
					ownedAndTotal[0]++;
				}
			}
			for (Map.Entry<String, int[]> section : counts.entrySet())
			{
				summary.append(String.format("%s: %d / %d unlocked<br>",
					section.getKey(), section.getValue()[0], section.getValue()[1]));
			}
		}
		summary.append(String.format("Void XP (locked skills): %,d<br>", snapshot.getVoidXp()));
		summary.append(String.format("Illegal kills: %d · Ticks in locked regions: %d</html>",
			snapshot.getIllegalKills(), snapshot.getViolationTicks()));
		return summary.toString();
	}

	/**
	 * A category node that keeps its stable section name separate from the
	 * displayed "Regions 5 / 83" label, so expansion state survives refreshes
	 * where the counters change.
	 */
	private static class SectionNode extends DefaultMutableTreeNode
	{
		private final String section;

		SectionNode(String section)
		{
			super(section);
			this.section = section;
		}
	}

	private void rebuildUnlockTree(List<UnlockEntry> entries)
	{
		// Skip identical rebuilds: XP-driven refreshes are frequent and the
		// tear-down/reload is the panel's most expensive operation.
		if (Objects.equals(entries, lastTreeEntries))
		{
			return;
		}
		lastTreeEntries = entries;
		// Remember which sections are open so a refresh doesn't collapse the tree
		// under the player's cursor.
		Set<String> expandedSections = new HashSet<>();
		for (int i = 0; i < unlockRoot.getChildCount(); i++)
		{
			SectionNode section = (SectionNode) unlockRoot.getChildAt(i);
			if (unlockTree.isExpanded(new TreePath(section.getPath())))
			{
				expandedSections.add(section.section);
			}
		}

		unlockRoot.removeAllChildren();
		if (entries != null)
		{
			Map<String, SectionNode> sections = new LinkedHashMap<>();
			Map<String, Integer> owned = new LinkedHashMap<>();
			for (UnlockEntry entry : entries)
			{
				SectionNode section = sections.computeIfAbsent(entry.getSection(),
					name ->
					{
						SectionNode node = new SectionNode(name);
						unlockRoot.add(node);
						return node;
					});
				section.add(new DefaultMutableTreeNode(entry));
				if (entry.isUnlocked())
				{
					owned.merge(entry.getSection(), 1, Integer::sum);
				}
			}
			for (SectionNode section : sections.values())
			{
				section.setUserObject(section.section + " "
					+ owned.getOrDefault(section.section, 0) + " / " + section.getChildCount());
			}
		}
		((DefaultTreeModel) unlockTree.getModel()).reload();
		for (int i = 0; i < unlockRoot.getChildCount(); i++)
		{
			SectionNode section = (SectionNode) unlockRoot.getChildAt(i);
			if (expandedSections.contains(section.section))
			{
				unlockTree.expandPath(new TreePath(section.getPath()));
			}
		}
	}

	private void revalidateAll()
	{
		draftSection.revalidate();
		draftSection.repaint();
		revalidate();
		repaint();
	}
}
