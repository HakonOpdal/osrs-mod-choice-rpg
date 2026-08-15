package com.pathlocked;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.pathlocked.content.ContentRepository;
import com.pathlocked.draft.DraftOption;
import com.pathlocked.draft.DraftService;
import com.pathlocked.enforcement.RegionLockOverlay;
import com.pathlocked.enforcement.RegionStatus;
import com.pathlocked.points.PointsService;
import com.pathlocked.points.ThresholdCurve;
import com.pathlocked.ui.PanelSnapshot;
import com.pathlocked.ui.PathlockedPanel;
import com.pathlocked.ui.StatusOverlay;
import com.pathlocked.unlocks.ProfileManager;
import com.pathlocked.unlocks.ProfileState;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Pathlocked",
	description = "Choice-unlock gamemode: everything starts locked - earn points and draft which regions and monsters to unlock",
	tags = {"gamemode", "unlock", "draft", "restriction"}
)
public class PathlockedPlugin extends Plugin implements PathlockedPanel.Actions
{
	private static final int SAVE_INTERVAL_TICKS = 50;
	private static final String CHAT_PREFIX = "Pathlocked: ";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PathlockedConfig config;

	private ContentRepository content;
	private DraftService draftService;
	private PointsService pointsService;
	private ProfileManager profileManager;
	private ProfileState profile;
	private long profileAccountHash;

	private PathlockedPanel panel;
	private NavigationButton navButton;
	private RegionLockOverlay regionOverlay;
	private StatusOverlay statusOverlay;

	private boolean dirty;
	private int ticksSinceSave;
	private RegionStatus lastAnnouncedStatus = RegionStatus.UNLOCKED;

	private volatile RegionStatus currentRegionStatus = RegionStatus.UNLOCKED;
	private volatile String currentRegionName = "";
	private volatile boolean pendingDraft;

	@Inject
	private Gson gson;

	@Override
	protected void startUp()
	{
		content = ContentRepository.load(gson);
		draftService = new DraftService(content);
		pointsService = new PointsService();
		profileManager = new ProfileManager(new File(RuneLite.RUNELITE_DIR, "pathlocked"), gson);

		panel = new PathlockedPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Pathlocked")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
			.priority(5)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		regionOverlay = new RegionLockOverlay(client, this, config);
		statusOverlay = new StatusOverlay(this, config);
		overlayManager.add(regionOverlay);
		overlayManager.add(statusOverlay);

		refreshPanel();
	}

	@Override
	protected void shutDown()
	{
		// shutDown runs on the EDT while game events may still be mutating the
		// profile on the client thread; hand the final save to the client thread
		// so serialization never races the mutations.
		ProfileState profileToSave = profile;
		long accountHash = profileAccountHash;
		boolean needsSave = profileToSave != null && dirty;
		dirty = false;
		if (needsSave)
		{
			clientThread.invoke(() -> profileManager.save(accountHash, profileToSave));
		}
		overlayManager.remove(regionOverlay);
		overlayManager.remove(statusOverlay);
		clientToolbar.removeNavigation(navButton);
		profile = null;
		pendingDraft = false;
		currentRegionStatus = RegionStatus.UNLOCKED;
		lastAnnouncedStatus = RegionStatus.UNLOCKED;
	}

	@Provides
	PathlockedConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PathlockedConfig.class);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Note: LOGGED_IN also fires after every LOADING scene transition, so the
		// XP cache is only reset when actually leaving the world (login screen/hop).
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
			case HOPPING:
				saveProfile();
				profile = null;
				pointsService.reset();
				lastAnnouncedStatus = RegionStatus.UNLOCKED;
				refreshPanel();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (profile == null)
		{
			tryLoadProfile();
			if (profile == null)
			{
				return;
			}
		}

		trackCurrentRegion();

		if (draftService.maybeStartDraft(profile))
		{
			dirty = true;
			pendingDraft = true;
			announce("You have earned a choice! Open the Pathlocked side panel to draft your unlock.");
			refreshPanel();
		}

		if (dirty && ++ticksSinceSave >= SAVE_INTERVAL_TICKS)
		{
			saveProfile();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		// Always feed the points service: the login stat burst arrives before the
		// profile loads (first GameTick) and must still prime the XP cache.
		int xpDelta = pointsService.xpDelta(event.getSkill(), event.getXp());
		if (profile == null || xpDelta <= 0)
		{
			return;
		}
		if (currentRegionStatus == RegionStatus.LOCKED)
		{
			// Trespassing earns nothing, same as kills, or locked regions would
			// be a free skilling loophole in the point economy.
			return;
		}
		int buffered = profile.xpRemainder + xpDelta;
		long points = buffered / PointsService.XP_PER_POINT;
		profile.xpRemainder = buffered % PointsService.XP_PER_POINT;
		dirty = true;
		if (points > 0)
		{
			profile.totalPoints += points;
			refreshPanel();
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (profile == null)
		{
			return;
		}
		NPC npc = event.getNpc();
		String name = npc.getName() == null ? null : Text.removeTags(npc.getName());
		if (name == null || !content.isListedMonster(name))
		{
			// Unlisted NPCs (members content, data gaps) earn nothing: awarding
			// points for them would bypass the unlock economy entirely.
			return;
		}
		if (!profile.isMonsterUnlocked(name))
		{
			profile.illegalKills++;
			announce(name + " is locked - that kill earned no points.");
		}
		else if (currentRegionStatus == RegionStatus.LOCKED)
		{
			announce("No points while trespassing in a locked region.");
		}
		else
		{
			profile.totalPoints += pointsService.pointsForKill(npc.getCombatLevel());
		}
		dirty = true;
		refreshPanel();
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (profile == null || !config.enforceMonsters() || !"Attack".equals(event.getOption()))
		{
			return;
		}
		NPC npc = event.getMenuEntry().getNpc();
		if (npc != null && isMonsterBlocked(npc.getName()))
		{
			event.getMenuEntry().setDeprioritized(true);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (profile == null || !config.enforceMonsters() || !config.hardBlockAttack()
			|| !"Attack".equals(event.getMenuOption()))
		{
			return;
		}
		NPC npc = event.getMenuEntry().getNpc();
		if (npc != null && isMonsterBlocked(npc.getName()))
		{
			event.consume();
			announce(Text.removeTags(npc.getName()) + " is locked. Unlock it through a draft first.");
		}
	}

	@Override
	public void pickOption(int index, String expectedName)
	{
		clientThread.invokeLater(() ->
		{
			if (profile == null || profile.pendingDraft == null)
			{
				return;
			}
			// The click may act on a stale panel snapshot (e.g. queued behind a
			// reroll); only accept it if the option at this index is still the
			// one the player actually saw.
			List<DraftOption> offers = profile.pendingDraft.offers;
			if (index < 0 || index >= offers.size()
				|| !offers.get(index).getName().equals(expectedName))
			{
				announce("The draft changed - check the panel and pick again.");
				refreshPanel();
				return;
			}
			DraftOption picked = draftService.pick(profile, index);
			if (picked != null)
			{
				pendingDraft = false;
				dirty = true;
				announce("Unlocked: " + picked.getName() + "!");
				saveProfile();
				refreshPanel();
			}
		});
	}

	@Override
	public void rerollDraft()
	{
		clientThread.invokeLater(() ->
		{
			if (profile == null)
			{
				return;
			}
			if (draftService.reroll(profile))
			{
				dirty = true;
				announce("Draft rerolled.");
				saveProfile();
				refreshPanel();
			}
		});
	}

	public RegionStatus regionStatus(int regionId)
	{
		if (profile == null || content == null)
		{
			return RegionStatus.UNLOCKED;
		}
		if (!content.isKnownRegion(regionId))
		{
			return RegionStatus.UNCHARTED;
		}
		return profile.isRegionUnlocked(regionId) ? RegionStatus.UNLOCKED : RegionStatus.LOCKED;
	}

	public boolean isProfileLoaded()
	{
		return profile != null;
	}

	public boolean hasPendingDraft()
	{
		return pendingDraft;
	}

	public RegionStatus getCurrentRegionStatus()
	{
		return currentRegionStatus;
	}

	public String getCurrentRegionName()
	{
		return currentRegionName;
	}

	private void tryLoadProfile()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		long accountHash = client.getAccountHash();
		if (accountHash == -1)
		{
			return;
		}
		long seed = accountHash;
		String override = config.seedOverride().trim();
		if (!override.isEmpty())
		{
			try
			{
				seed = Long.parseLong(override);
			}
			catch (NumberFormatException e)
			{
				log.warn("Ignoring non-numeric seed override: {}", override);
			}
		}
		profile = profileManager.loadOrCreate(accountHash, content, seed);
		profileAccountHash = accountHash;
		pendingDraft = profile.pendingDraft != null;
		// Baselines must come from the client, not from the first StatChanged:
		// when the plugin is enabled mid-session the login burst already passed,
		// and first-event priming would swallow the next real XP gain per skill.
		for (Skill skill : Skill.values())
		{
			pointsService.prime(skill, client.getSkillExperience(skill));
		}
		if (profileManager.isCreatedNewProfile())
		{
			announce("Welcome to Pathlocked! The world starts locked - earn points to draft your unlocks.");
		}
		refreshPanel();
	}

	private void trackCurrentRegion()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		int regionId = player.getWorldLocation().getRegionID();
		RegionStatus status = regionStatus(regionId);
		currentRegionStatus = status;
		currentRegionName = content.isKnownRegion(regionId)
			? content.regionById(regionId).getName()
			: "Uncharted (" + regionId + ")";

		if (status == RegionStatus.LOCKED)
		{
			profile.violationTicks++;
			dirty = true;
		}
		if (status != lastAnnouncedStatus)
		{
			if (status == RegionStatus.LOCKED)
			{
				announce("You entered a locked region: " + currentRegionName + ".");
			}
			lastAnnouncedStatus = status;
		}
	}

	private void announce(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", CHAT_PREFIX + message, null);
	}

	private void saveProfile()
	{
		ticksSinceSave = 0;
		if (profile == null)
		{
			dirty = false;
			return;
		}
		// Keep dirty on a failed write so the next autosave interval retries
		// instead of silently dropping the session's progress.
		if (dirty && profileManager.save(profileAccountHash, profile))
		{
			dirty = false;
		}
	}

	private void refreshPanel()
	{
		if (panel == null)
		{
			return;
		}
		if (profile == null)
		{
			panel.refresh(PanelSnapshot.builder().loggedIn(false).build());
			return;
		}

		List<String> recentHistory = new ArrayList<>();
		List<ProfileState.ChoiceRecord> history = profile.history;
		for (int i = Math.max(0, history.size() - 8); i < history.size(); i++)
		{
			ProfileState.ChoiceRecord record = history.get(i);
			if (record.picked != null)
			{
				recentHistory.add((record.choiceIndex + 1) + ". " + record.picked.getName());
			}
		}

		panel.refresh(PanelSnapshot.builder()
			.loggedIn(true)
			.totalPoints(profile.totalPoints)
			.availablePoints(profile.availablePoints())
			.nextCost(ThresholdCurve.cost(profile.choiceIndex))
			.choiceIndex(profile.choiceIndex)
			.offers(profile.pendingDraft == null ? null : new ArrayList<>(profile.pendingDraft.offers))
			.rerollsLeft(profile.pendingDraft == null ? 0
				: DraftService.MAX_REROLLS - profile.pendingDraft.rerollsUsed)
			.regionsUnlocked(profile.unlockedRegions.size())
			.regionsTotal(content.getRegions().size())
			.monstersUnlocked(profile.unlockedMonsters.size())
			.monstersTotal(content.getMonsters().size())
			.recentHistory(recentHistory)
			.illegalKills(profile.illegalKills)
			.violationTicks(profile.violationTicks)
			.build());
	}

	private boolean isMonsterBlocked(String npcName)
	{
		if (npcName == null)
		{
			return false;
		}
		String cleanName = Text.removeTags(npcName);
		return content.isListedMonster(cleanName) && !profile.isMonsterUnlocked(cleanName);
	}
}
