package com.runemargin;

import com.google.inject.Provides;
import com.runemargin.alerts.AlertManager;
import com.runemargin.api.ItemRow;
import com.runemargin.api.RuneMarginApiClient;
import com.runemargin.api.SetComponentRow;
import com.runemargin.api.SetRow;
import com.runemargin.overlay.GeOverlay;
import com.runemargin.session.SessionTracker;
import com.runemargin.ui.GeSlot;
import com.runemargin.ui.RuneMarginPanel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Rune Margin",
	description = "Live GE margins, best-flip suggestions, set-combining and potion-decanting plays from runemargin.co.uk",
	tags = {"grand", "exchange", "ge", "flip", "flipping", "merch", "margin", "profit", "money", "decanting", "sets", "potions"}
)
public class RuneMarginPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private GeOverlay geOverlay;

	@Inject
	private RuneMarginPanel panel;

	@Inject
	private SessionTracker sessionTracker;

	@Inject
	private RuneMarginApiClient api;

	@Inject
	private RuneMarginConfig config;

	@Inject
	private AlertManager alertManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ScheduledExecutorService executor;

	private NavigationButton navButton;
	private ScheduledFuture<?> alertTask;

	// The item currently auto-focused from the GE offer editor (-1 = editor closed), and its
	// cached market row so we can re-assert the flip focus without re-fetching.
	private int autoFocusedItem = -1;
	private volatile ItemRow offerRow;

	// A cheap fingerprint of the last GE-slot snapshot pushed to the panel, so a per-tick
	// refresh only rebuilds the Slots tab when an offer actually changes.
	private String lastSlotsSig = "";

	// Per-GE-slot baselines so we book only the fills that happen this session and
	// don't double-count the cumulative values RuneLite replays at login.
	private final long[] slotQty = new long[8];
	private final long[] slotSpent = new long[8];
	private final int[] slotItem = new int[8];
	private final boolean[] slotSeen = new boolean[8];

	@Override
	protected void startUp()
	{
		panel.init();

		navButton = NavigationButton.builder()
			.tooltip("Rune Margin")
			.icon(loadIcon())
			.priority(7)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(geOverlay);
		loadSetRecipes();

		long period = Math.max(30, config.refreshSeconds());
		alertTask = executor.scheduleWithFixedDelay(alertManager::check, period, period, TimeUnit.SECONDS);
	}

	/**
	 * Fetch the set definitions once and hand the session tracker a
	 * {@code setId -> [componentIds]} map, so selling an assembled set is costed against the
	 * pieces you bought (see {@link SessionTracker}).
	 */
	private void loadSetRecipes()
	{
		api.sets(
			rows ->
			{
				Map<Integer, List<Integer>> recipes = new HashMap<>();
				if (rows != null)
				{
					for (SetRow row : rows)
					{
						if (row.components == null || row.components.isEmpty())
						{
							continue;
						}
						List<Integer> ids = new ArrayList<>();
						for (SetComponentRow comp : row.components)
						{
							ids.add(comp.itemId);
						}
						recipes.put(row.setId, ids);
					}
				}
				sessionTracker.setSetRecipes(recipes);
			},
			err -> { });
	}

	@Override
	protected void shutDown()
	{
		if (alertTask != null)
		{
			alertTask.cancel(true);
			alertTask = null;
		}
		overlayManager.remove(geOverlay);
		clientToolbar.removeNavigation(navButton);
		panel.teardown();
		navButton = null;
	}

	/**
	 * Book the player's real GE fills into the session tracker. RuneLite reports each
	 * offer's <em>cumulative</em> transacted quantity/gp, so we diff against a per-slot
	 * baseline to record just the new fill, then FIFO-match sells against buys.
	 */
	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		bookFill(event);
		// Always refresh the Slots tab from the full offer snapshot — this covers new fills
		// and the per-slot replay RuneLite fires at login.
		pushSlots();
	}

	private void bookFill(GrandExchangeOfferChanged event)
	{
		int slot = event.getSlot();
		if (slot < 0 || slot >= slotSeen.length)
		{
			return;
		}

		GrandExchangeOffer offer = event.getOffer();
		GrandExchangeOfferState state = offer.getState();
		if (state == GrandExchangeOfferState.EMPTY)
		{
			slotSeen[slot] = false;
			return;
		}

		// Bind persistence to the logged-in player before booking anything.
		ensureProfile();

		int itemId = offer.getItemId();
		long cumQty = offer.getQuantitySold();
		long cumSpent = offer.getSpent();
		boolean buy = state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.CANCELLED_BUY;

		// First sight of this slot/item (a fresh offer, or the login-time snapshot of an
		// already-running one): set the baseline so pre-session fills aren't counted.
		if (!slotSeen[slot] || slotItem[slot] != itemId)
		{
			slotSeen[slot] = true;
			slotItem[slot] = itemId;
			slotQty[slot] = cumQty;
			slotSpent[slot] = cumSpent;
			return;
		}

		long dQty = cumQty - slotQty[slot];
		long dSpent = cumSpent - slotSpent[slot];
		slotQty[slot] = cumQty;
		slotSpent[slot] = cumSpent;
		if (dQty <= 0)
		{
			return;
		}

		if (buy)
		{
			sessionTracker.recordBuy(itemId, dQty, dSpent);
		}
		else
		{
			sessionTracker.recordSell(itemId, dQty, dSpent);
		}
	}

	/** Re-sort the panel immediately when the user changes the sort order in settings. */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!RuneMarginConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("sortMode".equals(event.getKey()))
		{
			panel.reSort();
		}
		else if ("membership".equals(event.getKey()))
		{
			// P2P/F2P filter changed: re-fetch the lists with the new filter.
			panel.reloadLists();
		}
		else if ("accessToken".equals(event.getKey()))
		{
			// New/removed token: refresh both the tier banner and the (now differently
			// capped) flips.
			panel.onAccessTokenChanged();
		}
		else if ("profitTarget".equals(event.getKey()))
		{
			// New goal: re-render the Session target bar against the current profit.
			panel.refreshTarget();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			// Re-baseline on next login so the replayed snapshot isn't double-counted.
			Arrays.fill(slotSeen, false);
			// Clear the Slots tab: offers aren't valid while logged out.
			lastSlotsSig = "";
			panel.updateSlots(List.of());
		}
	}

	/**
	 * Keep the Slots tab live. Reading the offers each tick (and pushing only when they
	 * actually change, per {@link #pushSlots()}) means standing offers show up regardless of
	 * how the panel came to be — a fresh login, a new fill, or enabling the plugin
	 * mid-session with offers already running.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		pushSlots();
	}

	/**
	 * Auto-follow the item being set up in the GE offer editor. While an offer is being
	 * created ({@code GE_NEWOFFER_TYPE} non-zero), the item shown in the setup screen —
	 * whether reached by searching to buy or clicking an item to sell — lives in
	 * {@code TRADINGPOST_SEARCH} (the same var RuneLite's own GE plugin reads for the
	 * setup examine text). We fetch it and focus the margin overlay on it, so selling a
	 * held item re-points the analysis at that item rather than the last thing searched.
	 *
	 * <p>Every GE var change re-asserts the flip focus: the first sight of a new offer item
	 * fetches it from the API, but subsequent changes for the same item (adjusting price /
	 * quantity) just re-show the cached row. That's what lets the overlay return to "Your
	 * flip" after the player has clicked a panel item to peek at the neutral analysis —
	 * touching the offer again brings the flip view back, with no repeated network calls.
	 */
	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) == 0)
		{
			autoFocusedItem = -1; // editor closed; allow the next open to re-trigger.
			offerRow = null;
			geOverlay.setOfferBuy(null); // clear the price hint's buy/sell direction
			return;
		}

		int itemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
		if (itemId <= 0)
		{
			// Fall back to the search box var if the setup item isn't populated yet.
			itemId = client.getVarpValue(VarPlayerID.GE_LAST_SEARCHED);
		}
		if (itemId <= 0)
		{
			return;
		}
		if (itemId == autoFocusedItem)
		{
			// Same offer item — re-assert the flip focus from cache (no network) so the
			// overlay snaps back to "Your flip" once the player returns to their offer.
			if (offerRow != null)
			{
				geOverlay.setOfferItem(offerRow);
			}
			return;
		}
		autoFocusedItem = itemId;
		offerRow = null;
		api.item(itemId,
			row ->
			{
				if (row != null)
				{
					offerRow = row;
					geOverlay.setOfferItem(row);
				}
			},
			err -> { });
	}

	/**
	 * Learn whether the open offer setup is a buy or a sell. The game fires these script
	 * callbacks to build the setup's examine text (the same hook RuneLite's GE plugin uses),
	 * so they are the authoritative buy/sell signal. Feeds the overlay's read-only price hint.
	 */
	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		String name = event.getEventName();
		if ("geBuyExamineText".equals(name))
		{
			geOverlay.setOfferBuy(Boolean.TRUE);
		}
		else if ("geSellExamineText".equals(name))
		{
			geOverlay.setOfferBuy(Boolean.FALSE);
		}
	}

	/** Bind the session tracker and alert manager to the logged-in player. */
	private void ensureProfile()
	{
		if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null)
		{
			String rsn = client.getLocalPlayer().getName();
			sessionTracker.setProfile(rsn);
			alertManager.setProfile(rsn);
		}
	}

	/**
	 * Snapshot every non-empty GE slot and push it to the Slots tab — but only when it
	 * differs from the last push, so this can run every tick without churning the EDT.
	 * Client-thread only ({@code getGrandExchangeOffers} must be read on the game thread).
	 */
	private void pushSlots()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		List<GeSlot> slots = new ArrayList<>();
		StringBuilder sig = new StringBuilder();
		if (offers != null)
		{
			for (int i = 0; i < offers.length; i++)
			{
				GrandExchangeOffer offer = offers[i];
				if (offer == null)
				{
					continue;
				}
				GrandExchangeOfferState state = offer.getState();
				if (state == GrandExchangeOfferState.EMPTY || offer.getTotalQuantity() <= 0)
				{
					continue;
				}
				boolean buy = state == GrandExchangeOfferState.BUYING
					|| state == GrandExchangeOfferState.BOUGHT
					|| state == GrandExchangeOfferState.CANCELLED_BUY;
				GeSlot.Status status;
				if (state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD)
				{
					status = GeSlot.Status.FILLED;
				}
				else if (state == GrandExchangeOfferState.CANCELLED_BUY
					|| state == GrandExchangeOfferState.CANCELLED_SELL)
				{
					status = GeSlot.Status.CANCELLED;
				}
				else
				{
					status = GeSlot.Status.ACTIVE;
				}
				// Resolve the name here, on the client thread — ItemManager.getItemComposition
				// asserts the client thread, so it must never be called from the panel's EDT.
				slots.add(new GeSlot(i, offer.getItemId(), itemName(offer.getItemId()), buy,
					offer.getQuantitySold(), offer.getTotalQuantity(), offer.getPrice(), status));
				sig.append(i).append(':').append(offer.getItemId()).append(':')
					.append(offer.getQuantitySold()).append('/').append(offer.getTotalQuantity())
					.append('@').append(offer.getPrice()).append(':').append(status).append(';');
			}
		}
		String s = sig.toString();
		if (s.equals(lastSlotsSig))
		{
			return; // nothing changed since the last push
		}
		lastSlotsSig = s;
		panel.updateSlots(slots);
	}

	/** The item's name, resolved on the client thread. Falls back to "Item {id}". */
	private String itemName(int itemId)
	{
		try
		{
			return itemManager.getItemComposition(itemId).getName();
		}
		catch (RuntimeException e)
		{
			return "Item " + itemId;
		}
	}

	@Provides
	RuneMarginConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneMarginConfig.class);
	}

	/**
	 * The nav-button icon: the bundled brand mark, or — if the resource can't be loaded
	 * for any reason — a tiny generated square. {@code NavigationButton} requires a
	 * non-null icon, and a missing optional icon must never stop the plugin enabling
	 * ({@code ImageUtil.loadImageResource} throws rather than returning null).
	 */
	private BufferedImage loadIcon()
	{
		try
		{
			return ImageUtil.loadImageResource(RuneMarginPlugin.class, "icon.png");
		}
		catch (RuntimeException e)
		{
			log.warn("Rune Margin icon.png could not be loaded; using a fallback", e);
			BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			g.setColor(new Color(0xF0, 0xA8, 0x30)); // brand gold (frontend --gold)
			g.fillRect(0, 0, 16, 16);
			g.dispose();
			return img;
		}
	}
}
