package com.pathlocked;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.pathlocked.content.ContentRepository;
import com.pathlocked.content.ItemTagDef;
import com.pathlocked.content.MonsterDef;
import com.pathlocked.content.RegionDef;
import com.pathlocked.draft.DraftOption;
import com.pathlocked.draft.DraftService;
import com.pathlocked.enforcement.ItemLockOverlay;
import com.pathlocked.enforcement.RegionLockOverlay;
import com.pathlocked.enforcement.RegionStatus;
import com.pathlocked.points.PointsService;
import com.pathlocked.points.ThresholdCurve;
import com.pathlocked.ui.PanelSnapshot;
import com.pathlocked.ui.PathlockedPanel;
import com.pathlocked.ui.StatusOverlay;
import com.pathlocked.ui.UnlockEntry;
import com.pathlocked.unlocks.ProfileManager;
import com.pathlocked.unlocks.ProfileState;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
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

	@Inject
	private ItemManager itemManager;

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
	private ItemLockOverlay itemLockOverlay;

	private boolean dirty;
	private int ticksSinceSave;
	private RegionStatus lastAnnouncedStatus = RegionStatus.UNLOCKED;
	/**
	 * Skills already warned about this session, so locked-skill training nags
	 * once instead of on every XP drop.
	 */
	private final Set<Skill> announcedVoidSkills = EnumSet.noneOf(Skill.class);
	/**
	 * Canonical item id -> locked?, rebuilt lazily; item-name lookups run per
	 * menu entry and per rendered inventory slot, so they must not hit
	 * ItemComposition every time. Invalidated on any unlock or profile change.
	 */
	private final Map<Integer, Boolean> itemLockCache = new HashMap<>();

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
		itemLockOverlay = new ItemLockOverlay(this, config);
		overlayManager.add(regionOverlay);
		overlayManager.add(statusOverlay);
		overlayManager.add(itemLockOverlay);

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
			// Serialize on the client thread (mutations happen there), but wait
			// briefly so a client shutdown can't kill the queued write.
			CountDownLatch saved = new CountDownLatch(1);
			clientThread.invoke(() ->
			{
				try
				{
					profileManager.save(accountHash, profileToSave);
				}
				finally
				{
					saved.countDown();
				}
			});
			try
			{
				if (!saved.await(2, TimeUnit.SECONDS))
				{
					log.warn("Timed out waiting for the final Pathlocked profile save");
				}
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
		}
		overlayManager.remove(regionOverlay);
		overlayManager.remove(statusOverlay);
		overlayManager.remove(itemLockOverlay);
		clientToolbar.removeNavigation(navButton);
		profile = null;
		pendingDraft = false;
		itemLockCache.clear();
		announcedVoidSkills.clear();
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
				if (dirty)
				{
					// One retry on a failed final write; the profile must still be
					// dropped afterwards so state never leaks across accounts.
					saveProfile();
				}
				if (dirty)
				{
					log.error("Pathlocked profile could not be saved on logout; this session's progress may be lost");
					dirty = false;
				}
				profile = null;
				pointsService.reset();
				// NPC indexes and the tick counter don't survive a world change;
				// stale entries would misattribute kills on the next world.
				damagedNpcs.clear();
				recentKillCredits.clear();
				itemLockCache.clear();
				// announcedVoidSkills survives here on purpose: a world hop
				// reloads the same account, and the once-per-session warning
				// should not repeat. It clears on account change and shutdown.
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

		int tick = client.getTickCount();
		if (!recentKillCredits.isEmpty())
		{
			recentKillCredits.values().removeIf(creditedTick -> tick - creditedTick > 10);
		}
		if (!damagedNpcs.isEmpty())
		{
			// Short window: stale damage must not claim someone else's kill.
			// True ownership isn't client-derivable without loot, so recent own
			// damage is the accepted attribution heuristic.
			damagedNpcs.values().removeIf(damagedTick -> tick - damagedTick > 25);
		}

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
			// Catch-up for display-only counters (void XP) whose hot paths
			// deliberately skip per-event refreshes; the unlock tree skips
			// identical rebuilds, so this cadence is cheap.
			refreshPanel();
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
		Skill skill = event.getSkill();
		if (content.isListedSkill(skill.getName()) && !profile.isSkillUnlocked(skill.getName()))
		{
			// Void XP: gained in a locked skill, earns nothing, tracked so the
			// honor-mode violation stays visible. Also covers locked combat
			// skills (e.g. training Defence before drafting it). Skills outside
			// the draft pool (members skills) are uncharted: never enforced,
			// like unlisted NPCs/items — they fall through to the normal path.
			// No refreshPanel here: locked-skill training fires StatChanged
			// every few seconds and the counter is display-only; it catches up
			// on the next natural refresh.
			profile.voidXpBySkill.merge(skill.getName().toLowerCase(), (long) xpDelta, Long::sum);
			dirty = true;
			if (announcedVoidSkills.add(skill))
			{
				announce(skill.getName() + " is locked - that XP is void and earns no points.");
			}
			return;
		}
		if (PointsService.isCombatSkill(skill))
		{
			// Kills are credited by combat level; combat XP would double-dip.
			return;
		}
		if (liveRegionStatus() != RegionStatus.UNLOCKED)
		{
			// XP only counts on unlocked ground: LOCKED is trespassing, and
			// UNCHARTED (members areas, data gaps) would otherwise be a free
			// skilling loophole in the point economy.
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

	/**
	 * NPC index -> tick the kill was credited on; dedupes the death-event and
	 * loot-event credit paths (loot arrives on/after the death tick).
	 */
	private final Map<Integer, Integer> recentKillCredits = new HashMap<>();

	/**
	 * NPC index -> tick of the local player's last own hitsplat on it. Interaction
	 * alone can't attribute kills in multicombat; our own damage can.
	 */
	private final Map<Integer, Integer> damagedNpcs = new HashMap<>();

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (event.getActor() instanceof NPC && event.getHitsplat().isMine())
		{
			damagedNpcs.put(((NPC) event.getActor()).getIndex(), client.getTickCount());
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (profile == null || !(event.getActor() instanceof NPC))
		{
			return;
		}
		// Death-event credit path: covers listed monsters with empty drop tables
		// (Spider, Giant spider, ...), which never emit NpcLootReceived. Only
		// NPCs the player personally damaged count.
		NPC npc = (NPC) event.getActor();
		if (damagedNpcs.remove(npc.getIndex()) == null)
		{
			return;
		}
		recentKillCredits.put(npc.getIndex(), client.getTickCount());
		creditKill(npc);
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (profile == null)
		{
			return;
		}
		NPC npc = event.getNpc();
		if (recentKillCredits.remove(npc.getIndex()) != null)
		{
			return;
		}
		creditKill(npc);
	}

	private void creditKill(NPC npc)
	{
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
		else if (liveRegionStatus() == RegionStatus.LOCKED)
		{
			// Kills only reject LOCKED (trespass), not UNCHARTED: many listed
			// monsters live in dungeons/caves the region data doesn't cover, and
			// the monster unlock itself already gates the credit.
			announce("No points while trespassing in a locked region.");
		}
		else
		{
			profile.totalPoints += pointsService.pointsForKill(npc.getCombatLevel());
		}
		dirty = true;
		refreshPanel();
	}

	/**
	 * The item menu options that consume or equip an item — the actions an
	 * item-tag lock restricts. Examine, Drop, Use, banking etc. stay available
	 * (locked items are holdable, just not usable — the quarantine principle).
	 */
	private static final Set<String> ITEM_LOCK_OPTIONS = Set.of("Wield", "Wear", "Equip", "Eat", "Drink");

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (profile == null)
		{
			return;
		}
		if (config.enforceMonsters() && "Attack".equals(event.getOption()))
		{
			NPC npc = event.getMenuEntry().getNpc();
			if (npc != null && isMonsterBlocked(npc.getName()))
			{
				event.getMenuEntry().setDeprioritized(true);
			}
			return;
		}
		if (config.enforceItems() && ITEM_LOCK_OPTIONS.contains(event.getOption()))
		{
			int itemId = event.getMenuEntry().getItemId();
			if (itemId > 0 && isItemIdLocked(itemId))
			{
				event.getMenuEntry().setDeprioritized(true);
			}
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (profile == null)
		{
			return;
		}
		if (config.enforceMonsters() && config.hardBlockAttack()
			&& "Attack".equals(event.getMenuOption()))
		{
			NPC npc = event.getMenuEntry().getNpc();
			if (npc != null && isMonsterBlocked(npc.getName()))
			{
				event.consume();
				announce(Text.removeTags(npc.getName()) + " is locked. Unlock it through a draft first.");
			}
			return;
		}
		if (config.enforceItems() && config.hardBlockItemUse()
			&& ITEM_LOCK_OPTIONS.contains(event.getMenuOption()))
		{
			int itemId = event.getMenuEntry().getItemId();
			if (itemId > 0 && isItemIdLocked(itemId))
			{
				event.consume();
				String itemName = itemManager.getItemComposition(itemManager.canonicalize(itemId)).getName();
				announce(itemName + " is locked" + lockingTagHint(itemName) + ".");
			}
		}
	}

	/**
	 * " - draft 'Bronze tier' to use it", or empty when the tag lookup
	 * unexpectedly finds nothing.
	 */
	private String lockingTagHint(String itemName)
	{
		List<ItemTagDef> tags = content.tagsForItem(itemName);
		return tags.isEmpty() ? "" : " - draft '" + tags.get(0).getName() + "' to use it";
	}

	/**
	 * True when the (canonicalized) item is listed under at least one tag and
	 * none of its tags are unlocked. Unlisted items are uncharted: never
	 * enforced, like unlisted NPCs.
	 */
	public boolean isItemIdLocked(int itemId)
	{
		if (profile == null || content == null)
		{
			return false;
		}
		int canonicalId = itemManager.canonicalize(itemId);
		Boolean cached = itemLockCache.get(canonicalId);
		if (cached != null)
		{
			return cached;
		}
		String itemName = itemManager.getItemComposition(canonicalId).getName();
		List<ItemTagDef> tags = content.tagsForItem(itemName);
		boolean locked = !tags.isEmpty()
			&& tags.stream().noneMatch(tag -> profile.isTagUnlocked(tag.getName()));
		itemLockCache.put(canonicalId, locked);
		return locked;
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
				// A tag unlock changes item lock states; rebuild lazily.
				itemLockCache.clear();
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

	/**
	 * Status of the region the player is standing in right now. The cached
	 * currentRegionStatus is only refreshed on GameTick, which lags events
	 * fired on the same tick as a boundary crossing or teleport.
	 */
	private RegionStatus liveRegionStatus()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return currentRegionStatus;
		}
		return regionStatus(player.getWorldLocation().getRegionID());
	}

	public RegionStatus regionStatus(int regionId)
	{
		if (profile == null || content == null)
		{
			return RegionStatus.UNLOCKED;
		}
		if (!content.isKnownRegion(regionId))
		{
			// Nonstandard dungeons (Corsair Cove Dungeon, ...) carry an explicit
			// surface-owner mapping; consult it before the generic rule.
			Integer explicitOwner = content.surfaceOwnerOf(regionId);
			if (explicitOwner != null && content.isKnownRegion(explicitOwner))
			{
				return profile.isRegionUnlocked(explicitOwner) ? RegionStatus.UNLOCKED : RegionStatus.LOCKED;
			}
			// Underground map squares sit 6400 tiles north of their surface
			// square, i.e. region ry + 100; let dungeons (Dwarven Mine, Varrock
			// sewers, ...) inherit the surface square's lock status.
			int surfaceId = regionId - 100;
			if ((regionId & 0xFF) >= 100 && content.isKnownRegion(surfaceId))
			{
				return profile.isRegionUnlocked(surfaceId) ? RegionStatus.UNLOCKED : RegionStatus.LOCKED;
			}
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
		if (accountHash != profileAccountHash)
		{
			announcedVoidSkills.clear();
		}
		profile = profileManager.loadOrCreate(accountHash, content, seed);
		profileAccountHash = accountHash;
		pendingDraft = profile.pendingDraft != null;
		itemLockCache.clear();
		if (profileManager.isMigratedProfile())
		{
			// Keep the migrated state in the autosave/logout retry loop: if the
			// manager's own save failed, an unretried logout would re-run the
			// migration (and re-bank a threshold) on the next login.
			dirty = true;
		}
		// Baselines must come from the client, not from the first StatChanged:
		// when the plugin is enabled mid-session the login burst already passed,
		// and first-event priming would swallow the next real XP gain per skill.
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				// Synthetic total, not in the client's experience array:
				// querying it throws and would abort the profile load.
				continue;
			}
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
		currentRegionName = regionNameFor(regionId);

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

	private String regionNameFor(int regionId)
	{
		if (content.isKnownRegion(regionId))
		{
			return content.regionById(regionId).getName();
		}
		// Same surface normalization as regionStatus, so a locked dungeon warns
		// with its surface region's name instead of "Uncharted".
		Integer explicitOwner = content.surfaceOwnerOf(regionId);
		if (explicitOwner != null && content.isKnownRegion(explicitOwner))
		{
			return content.regionById(explicitOwner).getName() + " (underground)";
		}
		int surfaceId = regionId - 100;
		if ((regionId & 0xFF) >= 100 && content.isKnownRegion(surfaceId))
		{
			return content.regionById(surfaceId).getName() + " (underground)";
		}
		return "Uncharted (" + regionId + ")";
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
			.voidXp(profile.voidXpTotal())
			.unlockEntries(buildUnlockEntries())
			.recentHistory(recentHistory)
			.illegalKills(profile.illegalKills)
			.violationTicks(profile.violationTicks)
			.build());
	}

	private List<UnlockEntry> buildUnlockEntries()
	{
		List<UnlockEntry> entries = new ArrayList<>();
		for (RegionDef region : content.getRegions())
		{
			entries.add(new UnlockEntry("Regions", region.getName(),
				profile.isRegionUnlocked(region.id())));
		}
		for (MonsterDef monster : content.getMonsters())
		{
			entries.add(new UnlockEntry("Monsters", monster.getName(),
				profile.isMonsterUnlocked(monster.getName())));
		}
		for (ItemTagDef tag : content.getItemTags())
		{
			entries.add(new UnlockEntry("Items", tag.getName(),
				profile.isTagUnlocked(tag.getName())));
		}
		for (String skillName : content.getSkillNames())
		{
			entries.add(new UnlockEntry("Skills", skillName,
				profile.isSkillUnlocked(skillName)));
		}
		return entries;
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
