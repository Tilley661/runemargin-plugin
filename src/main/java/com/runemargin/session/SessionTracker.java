package com.runemargin.session;

import com.google.gson.Gson;
import com.runemargin.RuneMarginConfig;
import com.runemargin.calc.GeTax;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

/**
 * Tracks the player's realized flipping profit for the current session.
 *
 * <p>Fills are fed in as they complete ({@link #recordBuy}/{@link #recordSell}); a sell is
 * matched against earlier buys and profit is booked after GE tax (via {@link GeTax}, the
 * same source of truth as the website). Three cost-basis paths are tried, in order:
 *
 * <ul>
 *   <li><b>Direct</b> — FIFO against buy lots of the same item (a plain flip).</li>
 *   <li><b>Set</b> — selling an assembled set is costed against the buy lots of its
 *       component pieces (one of each per set), so "buy the pieces, sell the set" is one
 *       flip. Recipes come from {@link #setSetRecipes}.</li>
 *   <li><b>Decant</b> — potions are pooled by dose: buying a {@code (n)} potion adds
 *       {@code n} doses to its family, and selling a {@code (m)} potion consumes {@code m}
 *       doses. So "buy (3)s, decant, sell (4)s" is tracked against the doses you paid for.</li>
 * </ul>
 *
 * <p>Every path only books the quantity actually covered by tracked buys — if you sell two
 * sets but only bought pieces for one, the second (from your bank) has no known cost basis
 * and is left out of profit (though it still appears in the trade log).
 *
 * <p>State is persisted per RuneScape name through {@link ConfigManager}. The core is free
 * of RuneLite types so it can be unit-tested without a client; the plugin translates
 * {@code GrandExchangeOfferChanged} events into {@code recordBuy}/{@code recordSell} calls.
 */
@Slf4j
@Singleton
public class SessionTracker
{
	private static final int MAX_RECENT_FLIPS = 100;
	private static final int MAX_RECENT_TRADES = 200;
	private static final int MAX_PAST_SESSIONS = 30;
	private static final String KEY_PREFIX = "session_";

	// Matches a potion/dose name like "Prayer potion(4)" -> family "Prayer potion", dose 4.
	private static final Pattern DOSE_PATTERN = Pattern.compile("^(.*?)\\((\\d)\\)$");

	private final ConfigManager configManager;
	private final Gson gson;
	@Nullable
	private final ItemManager itemManager;

	private final CopyOnWriteArrayList<Consumer<SessionSnapshot>> listeners = new CopyOnWriteArrayList<>();

	// Reference data, not persisted: set recipes (set item id -> component item ids, one
	// entry per piece needed) and a name cache used to detect potions. Both are populated
	// by the plugin; tests inject them directly.
	private Map<Integer, List<Integer>> setRecipes = new HashMap<>();
	private final Map<Integer, String> knownNames = new HashMap<>();

	@Nullable
	private String profile;
	private State state = new State();

	@Inject
	public SessionTracker(ConfigManager configManager, Gson gson, ItemManager itemManager)
	{
		this.configManager = configManager;
		this.gson = gson;
		this.itemManager = itemManager;
	}

	/** Test constructor: no persistence, no name resolution. */
	public SessionTracker()
	{
		this(null, null, null);
	}

	/**
	 * Bind to a player. Loads that player's persisted session (if any). A no-op when
	 * the profile is unchanged.
	 */
	public synchronized void setProfile(@Nullable String rsn)
	{
		if (rsn == null || rsn.equals(profile))
		{
			return;
		}
		profile = rsn;
		state = load(rsn);
		if (state.sessionStartMillis == 0)
		{
			state.sessionStartMillis = System.currentTimeMillis();
		}
		notifyListeners();
	}

	/** Provide the set recipes (set item id -> component item ids) used to cost set sells. */
	public synchronized void setSetRecipes(@Nullable Map<Integer, List<Integer>> recipes)
	{
		setRecipes = recipes != null ? recipes : new HashMap<>();
	}

	/** Test/plugin seam: seed a known item name so potion detection works without a client. */
	synchronized void putItemName(int itemId, String name)
	{
		knownNames.put(itemId, name);
	}

	/** Record a completed buy fill of {@code qty} units for {@code spent} total gp. */
	public synchronized void recordBuy(int itemId, long qty, long spent)
	{
		if (qty <= 0)
		{
			return;
		}
		ensureStarted();
		PotionInfo pot = potionInfo(itemId);
		if (pot != null)
		{
			// Potions are pooled by dose so a decant (buy (3), sell (4)) still costs out.
			state.dosePool.computeIfAbsent(pot.family, k -> new ArrayList<>())
				.add(new DoseLot(pot.dose * qty, spent));
		}
		else
		{
			state.inventory.computeIfAbsent(itemId, k -> new ArrayList<>()).add(new Lot(qty, spent));
		}
		recordTrade(itemId, Trade.Side.BUY, qty, spent);
		persist();
		notifyListeners();
	}

	/**
	 * Record a completed sell fill of {@code qty} units for {@code grossSpent} total gp
	 * (the pre-tax sale value). The cost basis is resolved via the direct, set or decant
	 * path (see the class doc), and after-tax profit is booked for the matched quantity.
	 * Unmatched quantity (sold without a tracked basis) is ignored for profit.
	 */
	public synchronized void recordSell(int itemId, long qty, long grossSpent)
	{
		if (qty <= 0)
		{
			return;
		}
		ensureStarted();

		// GE tax is 2% of the per-item sale price (see GeTax), computed ourselves rather
		// than trusting the raw offer value, to stay consistent with the website.
		long salePrice = grossSpent / qty;
		long taxTotal = GeTax.geTax(salePrice) * qty;

		Match match;
		String basis;
		List<FlipPiece> pieces = null;
		PotionInfo pot = potionInfo(itemId);
		if (pot != null)
		{
			match = consumeDoses(pot.family, pot.dose, qty);
			basis = "decant";
		}
		else if (setRecipes.containsKey(itemId))
		{
			pieces = new ArrayList<>();
			match = consumeSet(itemId, qty, pieces);
			basis = "set";
		}
		else
		{
			match = consumeDirect(itemId, qty);
			basis = "direct";
		}

		if (match.qty > 0)
		{
			// Proceeds and tax apportioned to the matched quantity.
			long matchedGross = grossSpent * match.qty / qty;
			long matchedTax = taxTotal * match.qty / qty;
			long matchedNet = matchedGross - matchedTax;

			Flip flip = new Flip(itemId, resolveName(itemId), match.qty, match.cost, matchedNet,
				System.currentTimeMillis(), basis, pieces);

			state.flips.add(flip);
			if (state.flips.size() > MAX_RECENT_FLIPS)
			{
				state.flips.remove(0);
			}
			state.flipCount++;
			state.realizedProfit += flip.profit;
			state.netProceeds += matchedNet;
			state.costBasis += match.cost;
			state.taxPaid += matchedTax;
			state.bestProfit = state.bestProfit == null ? flip.profit : Math.max(state.bestProfit, flip.profit);
			state.worstProfit = state.worstProfit == null ? flip.profit : Math.min(state.worstProfit, flip.profit);
		}

		// The full sell is booked to trade history (the listing price the player achieved),
		// even the unmatched part — it still tells them what they sold at.
		recordTrade(itemId, Trade.Side.SELL, qty, grossSpent);
		persist();
		notifyListeners();
	}

	/** Direct FIFO match against buy lots of {@code itemId}. Mutates the lots consumed. */
	private Match consumeDirect(int itemId, long qty)
	{
		List<Lot> lots = state.inventory.get(itemId);
		long matchedQty = 0;
		long matchedCost = 0;
		while (lots != null && !lots.isEmpty() && matchedQty < qty)
		{
			Lot lot = lots.get(0);
			long take = Math.min(lot.qty, qty - matchedQty);
			long costForTake = lot.spent * take / lot.qty;
			lot.qty -= take;
			lot.spent -= costForTake;
			matchedQty += take;
			matchedCost += costForTake;
			if (lot.qty <= 0)
			{
				lots.remove(0);
			}
		}
		if (lots != null && lots.isEmpty())
		{
			state.inventory.remove(itemId);
		}
		return new Match(matchedQty, matchedCost);
	}

	/**
	 * Cost a set sell against its component buy lots: each set consumes one of every piece,
	 * so the number of sets we can cost is limited by the scarcest piece. Anything beyond
	 * that (assembled from bank stock) is left unmatched.
	 */
	private Match consumeSet(int setId, long qty, List<FlipPiece> outPieces)
	{
		List<Integer> comps = setRecipes.get(setId);
		if (comps == null || comps.isEmpty())
		{
			return new Match(0, 0);
		}
		Map<Integer, Long> need = new HashMap<>();
		for (int c : comps)
		{
			need.merge(c, 1L, Long::sum);
		}
		long coverable = qty;
		for (Map.Entry<Integer, Long> e : need.entrySet())
		{
			long avail = totalQty(state.inventory.get(e.getKey()));
			coverable = Math.min(coverable, avail / e.getValue());
		}
		if (coverable <= 0)
		{
			return new Match(0, 0);
		}
		long cost = 0;
		for (Map.Entry<Integer, Long> e : need.entrySet())
		{
			Match m = consumeDirect(e.getKey(), coverable * e.getValue());
			cost += m.cost;
			if (outPieces != null)
			{
				outPieces.add(new FlipPiece(resolveName(e.getKey()), m.qty, m.cost));
			}
		}
		return new Match(coverable, cost);
	}

	/**
	 * Cost a potion sell against the family's dose pool: selling {@code qty} {@code (dose)}
	 * potions needs {@code dose * qty} doses. Only whole potions covered by pooled doses are
	 * matched (the rest are basis-less bank stock).
	 */
	private Match consumeDoses(String family, int dose, long qty)
	{
		List<DoseLot> lots = state.dosePool.get(family);
		if (lots == null || lots.isEmpty())
		{
			return new Match(0, 0);
		}
		long available = 0;
		for (DoseLot lot : lots)
		{
			available += lot.doses;
		}
		long covered = Math.min(qty, available / dose);
		if (covered <= 0)
		{
			return new Match(0, 0);
		}
		long needDoses = covered * dose;
		long taken = 0;
		long cost = 0;
		while (!lots.isEmpty() && taken < needDoses)
		{
			DoseLot lot = lots.get(0);
			long take = Math.min(lot.doses, needDoses - taken);
			long costForTake = lot.cost * take / lot.doses;
			lot.doses -= take;
			lot.cost -= costForTake;
			taken += take;
			cost += costForTake;
			if (lot.doses <= 0)
			{
				lots.remove(0);
			}
		}
		if (lots.isEmpty())
		{
			state.dosePool.remove(family);
		}
		return new Match(covered, cost);
	}

	private static long totalQty(@Nullable List<Lot> lots)
	{
		if (lots == null)
		{
			return 0;
		}
		long q = 0;
		for (Lot lot : lots)
		{
			q += lot.qty;
		}
		return q;
	}

	/** Append a raw fill to the browsable trade history (most-recent kept, capped). */
	private void recordTrade(int itemId, Trade.Side side, long qty, long total)
	{
		state.trades.add(new Trade(itemId, resolveName(itemId), side, qty, total, System.currentTimeMillis()));
		if (state.trades.size() > MAX_RECENT_TRADES)
		{
			state.trades.remove(0);
		}
	}

	/**
	 * Start a fresh session for the current profile. The outgoing session, if it had any
	 * activity, is archived to {@link State#pastSessions}.
	 *
	 * <p>Open buy lots and the dose pool are <b>carried forward</b>: they are the cost basis
	 * of items you still hold, just as valid after a reset. Only the realized-P&amp;L
	 * counters and the trade log start fresh.
	 */
	public synchronized void reset()
	{
		List<SessionSummary> past = state.pastSessions != null ? state.pastSessions : new ArrayList<>();
		if (state.flipCount > 0 || !state.trades.isEmpty())
		{
			long end = System.currentTimeMillis();
			past.add(0, new SessionSummary(state.sessionStartMillis, end, state.realizedProfit,
				state.flipCount, state.costBasis, state.taxPaid));
			while (past.size() > MAX_PAST_SESSIONS)
			{
				past.remove(past.size() - 1);
			}
		}
		Map<Integer, List<Lot>> heldLots = state.inventory;   // still-held cost basis survives
		Map<String, List<DoseLot>> heldDoses = state.dosePool; // as do pooled potion doses
		state = new State();
		state.pastSessions = past;
		state.inventory = heldLots;
		state.dosePool = heldDoses;
		state.sessionStartMillis = System.currentTimeMillis();
		persist();
		notifyListeners();
	}

	/**
	 * Average unit cost of {@code itemId} still held this session, or {@code null} if none
	 * is tracked. Handles potions (from the dose pool) and sets (the summed cost of one of
	 * each held component), so the overlay can show the real buy price on a sell offer.
	 */
	public synchronized Long avgBuyPrice(int itemId)
	{
		PotionInfo pot = potionInfo(itemId);
		if (pot != null)
		{
			List<DoseLot> lots = state.dosePool.get(pot.family);
			if (lots == null || lots.isEmpty())
			{
				return null;
			}
			long doses = 0;
			long cost = 0;
			for (DoseLot lot : lots)
			{
				doses += lot.doses;
				cost += lot.cost;
			}
			return doses > 0 ? (cost * pot.dose) / doses : null;
		}
		if (setRecipes.containsKey(itemId))
		{
			long total = 0;
			for (int c : setRecipes.get(itemId))
			{
				Long piece = avgBuyPrice(c);
				if (piece == null)
				{
					return null; // can't cost the set without every piece
				}
				total += piece;
			}
			return total;
		}
		List<Lot> lots = state.inventory.get(itemId);
		if (lots == null || lots.isEmpty())
		{
			return null;
		}
		long qty = 0;
		long spent = 0;
		for (Lot lot : lots)
		{
			qty += lot.qty;
			spent += lot.spent;
		}
		return qty > 0 ? spent / qty : null;
	}

	public synchronized SessionSnapshot snapshot()
	{
		long openInvested = 0;
		Set<Integer> heldItemIds = new HashSet<>();
		for (Map.Entry<Integer, List<Lot>> e : state.inventory.entrySet())
		{
			long qty = 0;
			for (Lot lot : e.getValue())
			{
				openInvested += lot.spent;
				qty += lot.qty;
			}
			if (qty > 0)
			{
				heldItemIds.add(e.getKey());
			}
		}
		// Potions are pooled by dose family; mark every known potion item of a family that
		// still has doses as "held", so its History row reads as holding, not consumed.
		Set<String> familiesWithDoses = new HashSet<>();
		for (Map.Entry<String, List<DoseLot>> e : state.dosePool.entrySet())
		{
			long doses = 0;
			for (DoseLot lot : e.getValue())
			{
				openInvested += lot.cost;
				doses += lot.doses;
			}
			if (doses > 0)
			{
				familiesWithDoses.add(e.getKey());
			}
		}
		if (!familiesWithDoses.isEmpty())
		{
			for (Integer id : new ArrayList<>(knownNames.keySet()))
			{
				PotionInfo p = potionInfo(id);
				if (p != null && familiesWithDoses.contains(p.family))
				{
					heldItemIds.add(id);
				}
			}
		}

		List<Flip> recent = new ArrayList<>(state.flips);
		Collections.reverse(recent);

		List<Trade> trades = new ArrayList<>(state.trades);
		Collections.reverse(trades);

		// pastSessions is already newest-first (reset() prepends).
		List<SessionSummary> past = new ArrayList<>(state.pastSessions);

		return new SessionSnapshot(state.realizedProfit, state.flipCount, state.taxPaid, state.netProceeds,
			state.costBasis, state.bestProfit, state.worstProfit, state.sessionStartMillis, openInvested, recent,
			trades, past, heldItemIds);
	}

	public void addListener(Consumer<SessionSnapshot> listener)
	{
		listeners.add(listener);
	}

	public void removeListener(Consumer<SessionSnapshot> listener)
	{
		listeners.remove(listener);
	}

	private void ensureStarted()
	{
		if (state.sessionStartMillis == 0)
		{
			state.sessionStartMillis = System.currentTimeMillis();
		}
	}

	private void notifyListeners()
	{
		if (listeners.isEmpty())
		{
			return;
		}
		SessionSnapshot snap = snapshot();
		for (Consumer<SessionSnapshot> listener : listeners)
		{
			listener.accept(snap);
		}
	}

	/** Potion family + dose for an item whose name looks like "Foo(n)" (n = 1..4), else null. */
	@Nullable
	private PotionInfo potionInfo(int itemId)
	{
		String name = resolveName(itemId);
		if (name == null)
		{
			return null;
		}
		Matcher m = DOSE_PATTERN.matcher(name.trim());
		if (!m.matches())
		{
			return null;
		}
		int dose = m.group(2).charAt(0) - '0';
		if (dose < 1 || dose > 4)
		{
			return null; // real potion doses are 1-4; excludes charge/jewellery "(n)" items
		}
		String family = m.group(1).trim();
		return family.isEmpty() ? null : new PotionInfo(family, dose);
	}

	@Nullable
	private String resolveName(int itemId)
	{
		String known = knownNames.get(itemId);
		if (known != null)
		{
			return known;
		}
		if (itemManager == null)
		{
			return null;
		}
		try
		{
			String name = itemManager.getItemComposition(itemId).getName();
			knownNames.put(itemId, name);
			return name;
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private void persist()
	{
		if (configManager == null || gson == null || profile == null)
		{
			return;
		}
		try
		{
			configManager.setConfiguration(RuneMarginConfig.GROUP, KEY_PREFIX + profile, gson.toJson(state));
		}
		catch (RuntimeException e)
		{
			log.debug("Failed to persist session state", e);
		}
	}

	private State load(String rsn)
	{
		if (configManager == null || gson == null)
		{
			return new State();
		}
		try
		{
			String json = configManager.getConfiguration(RuneMarginConfig.GROUP, KEY_PREFIX + rsn);
			if (json != null && !json.isEmpty())
			{
				State loaded = gson.fromJson(json, State.class);
				if (loaded != null)
				{
					loaded.normalize();
					return loaded;
				}
			}
		}
		catch (RuntimeException e)
		{
			log.debug("Failed to load session state for {}", rsn, e);
		}
		return new State();
	}

	/** A remaining buy lot: {@code qty} units bought for {@code spent} total gp. */
	private static final class Lot
	{
		long qty;
		long spent;

		@SuppressWarnings("unused") // Gson
		Lot()
		{
		}

		Lot(long qty, long spent)
		{
			this.qty = qty;
			this.spent = spent;
		}
	}

	/** A remaining pool of potion doses: {@code doses} doses bought for {@code cost} gp. */
	private static final class DoseLot
	{
		long doses;
		long cost;

		@SuppressWarnings("unused") // Gson
		DoseLot()
		{
		}

		DoseLot(long doses, long cost)
		{
			this.doses = doses;
			this.cost = cost;
		}
	}

	/** A potion's family (name without the dose suffix) and its dose count. */
	private static final class PotionInfo
	{
		final String family;
		final int dose;

		PotionInfo(String family, int dose)
		{
			this.family = family;
			this.dose = dose;
		}
	}

	/** The outcome of a cost-basis match: units covered and their total cost. */
	private static final class Match
	{
		final long qty;
		final long cost;

		Match(long qty, long cost)
		{
			this.qty = qty;
			this.cost = cost;
		}
	}

	/** The full persisted session state. */
	private static final class State
	{
		Map<Integer, List<Lot>> inventory = new HashMap<>();
		Map<String, List<DoseLot>> dosePool = new HashMap<>();
		List<Flip> flips = new ArrayList<>();
		List<Trade> trades = new ArrayList<>();
		List<SessionSummary> pastSessions = new ArrayList<>();
		long realizedProfit;
		long netProceeds;
		long costBasis;
		long taxPaid;
		int flipCount;
		Long bestProfit;
		Long worstProfit;
		long sessionStartMillis;

		/** Guard against nulls after a partial/legacy deserialization. */
		void normalize()
		{
			if (inventory == null)
			{
				inventory = new HashMap<>();
			}
			if (dosePool == null)
			{
				dosePool = new HashMap<>();
			}
			if (flips == null)
			{
				flips = new ArrayList<>();
			}
			if (trades == null)
			{
				trades = new ArrayList<>();
			}
			if (pastSessions == null)
			{
				pastSessions = new ArrayList<>();
			}
		}
	}
}
