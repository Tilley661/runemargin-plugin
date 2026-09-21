package com.runemargin.overlay;

import com.runemargin.RuneMarginConfig;
import com.runemargin.api.DecantRow;
import com.runemargin.api.ItemAnalysis;
import com.runemargin.api.ItemRow;
import com.runemargin.api.LivePriceCache;
import com.runemargin.api.SetComponentRow;
import com.runemargin.api.SetRow;
import com.runemargin.calc.GeTax;
import com.runemargin.session.SessionTracker;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The single Rune Margin in-game box, drawn over the Grand Exchange. It combines two
 * sections under one branded header:
 *
 * <ul>
 *   <li><b>Focus</b> — whatever the player last clicked in the panel (or the offer editor
 *       auto-followed). Three flavours, set via {@link #setItem}, {@link #setSet} and
 *       {@link #setDecant}: an item's buy/sell/margin/verdict, a set's buy-the-pieces
 *       breakdown, or a decant play's dose-to-buy plan.</li>
 *   <li><b>Your offers</b> — live per-slot margin-at-fill, profit and a time-to-fill
 *       estimate for the player's own active offers.</li>
 * </ul>
 *
 * <p>Read-only: it never touches offers. Only renders while the Grand Exchange is open,
 * and each section respects its own config toggle. Market data comes from
 * {@link LivePriceCache} (async, throttled); offer state is read straight off the
 * {@link Client}.
 */
@Singleton
public class GeOverlay extends Overlay
{
	// Grand Exchange interface group id. Using the raw id keeps this independent of
	// RuneLite constant renames across game revisions.
	private static final int GE_INTERFACE_GROUP_ID = 465;

	private final Client client;
	private final RuneMarginConfig config;
	private final LivePriceCache prices;
	private final ItemManager itemManager;
	private final SessionTracker sessionTracker;
	private final PanelComponent panel = new PanelComponent();

	/** Which flavour of focus the box is currently showing. */
	private enum Mode
	{
		ITEM,
		SET,
		DECANT
	}

	private volatile Mode mode;
	private volatile ItemRow item;
	private volatile SetRow set;
	private volatile DecantRow decant;

	// True only when the current item focus came from the GE offer editor: that's the one
	// place we treat a held item as an in-progress flip and price it off your real buy.
	// A plain panel click always shows the neutral market analysis.
	private volatile boolean flipFocus;

	// While a GE offer is being set up: TRUE for a buy, FALSE for a sell, null when closed.
	// Drives the read-only "type this price" hint (the plugin never sets the price itself).
	private volatile Boolean offerBuy;

	@Inject
	GeOverlay(Client client, RuneMarginConfig config, LivePriceCache prices, ItemManager itemManager,
		SessionTracker sessionTracker)
	{
		this.client = client;
		this.config = config;
		this.prices = prices;
		this.itemManager = itemManager;
		this.sessionTracker = sessionTracker;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	/** Focus an item's market analysis; shows until replaced. Null clears the focus. */
	public void setItem(ItemRow row)
	{
		setItem(row, false);
	}

	/**
	 * Focus an item as an in-progress flip (from the GE offer editor): if we hold it, the
	 * analysis is priced off your real buy from history rather than the market's suggested
	 * buy. Falls back to the neutral analysis when we don't hold it.
	 */
	public void setOfferItem(ItemRow row)
	{
		setItem(row, true);
	}

	private void setItem(ItemRow row, boolean flip)
	{
		this.item = row;
		this.mode = row == null ? null : Mode.ITEM;
		this.flipFocus = flip;
	}

	/**
	 * Set the direction of the offer currently being set up (TRUE buy / FALSE sell), or null
	 * when the editor closes. Used only to show the read-only recommended-price hint.
	 */
	public void setOfferBuy(Boolean buy)
	{
		this.offerBuy = buy;
	}

	/** Focus a set play — the pieces to buy and the combine/break profit. */
	public void setSet(SetRow row)
	{
		this.set = row;
		this.mode = row == null ? null : Mode.SET;
		this.flipFocus = false;
	}

	/** Focus a decant play — what dose to buy and the per-(4) profit. */
	public void setDecant(DecantRow row)
	{
		this.decant = row;
		this.mode = row == null ? null : Mode.DECANT;
		this.flipFocus = false;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!geOpen())
		{
			return null;
		}

		boolean showFocus = config.showGeOverlay() && focusPresent();
		GrandExchangeOffer[] offers = config.showGeOffers() ? client.getGrandExchangeOffers() : null;
		boolean showOffers = hasActiveOffer(offers);
		if (!showFocus && !showOffers)
		{
			return null;
		}

		panel.getChildren().clear();
		OverlayTheme.style(panel);

		// Subtle brand header the whole box hangs off.
		panel.getChildren().add(TitleComponent.builder()
			.text("RUNE MARGIN")
			.color(OverlayTheme.BRAND)
			.build());

		if (showFocus)
		{
			appendFocus();
		}
		if (showOffers)
		{
			if (showFocus)
			{
				panel.getChildren().add(spacer());
			}
			appendOffers(offers);
		}

		return panel.render(graphics);
	}

	/** True when a focus (item/set/decant) is set and its data is present. */
	private boolean focusPresent()
	{
		if (mode == null)
		{
			return false;
		}
		switch (mode)
		{
			case ITEM:
				return item != null;
			case SET:
				return set != null;
			case DECANT:
				return decant != null;
			default:
				return false;
		}
	}

	/** Render whichever focus flavour is active. */
	private void appendFocus()
	{
		switch (mode)
		{
			case ITEM:
				appendItem(item);
				break;
			case SET:
				appendSet(set);
				break;
			case DECANT:
				appendDecant(decant);
				break;
			default:
				break;
		}
	}

	// --- Analysing section ------------------------------------------------------------

	private void appendItem(ItemRow row)
	{
		// If we're holding this item (bought it this session, or an earlier one whose lot
		// still stands), it's a flip in progress — show the profit against our real buy
		// price from history rather than the market's suggested buy. This is what the
		// player actually stands to make when they sell what they own.
		Long heldCost = sessionTracker.avgBuyPrice(row.id);
		boolean held = flipFocus && heldCost != null;

		caption(held ? "Your flip" : "Analysis");
		panel.getChildren().add(TitleComponent.builder()
			.text(row.name == null ? ("Item " + row.id) : row.name)
			.color(OverlayTheme.TEXT)
			.build());
		panel.getChildren().add(TitleComponent.builder()
			.text(verdictLabel(row.analysis))
			.color(verdictColor(row.analysis))
			.build());

		// Read-only "type this price" hint while an offer is being set up: the buy (low)
		// price on a buy, the sell (high) price on a sell. We never set the price ourselves.
		if (flipFocus && offerBuy != null && config.showPriceHint())
		{
			Integer hint = offerBuy ? row.low : row.high;
			if (hint != null)
			{
				panel.getChildren().add(TitleComponent.builder()
					.text((offerBuy ? "Type buy: " : "Type sell: ") + gp(hint))
					.color(OverlayTheme.BRAND)
					.build());
			}
		}
		if (held)
		{
			int cost = heldCost.intValue();
			Integer sell = row.high; // current instant-sell price
			Integer margin = sell == null ? null : (int) (sell - GeTax.geTax(sell) - cost);
			Double roi = (margin != null && cost > 0) ? margin * 100.0 / cost : null;
			stat("Your buy", gp(cost), OverlayTheme.TEXT);
			stat("Sell", gp(sell), OverlayTheme.TEXT);
			stat("Margin", signedGp(margin), profitColor(margin));
			stat("ROI", pct(roi), profitColor(margin));
		}
		else
		{
			stat("Buy", gp(row.low), OverlayTheme.TEXT);
			stat("Sell", gp(row.high), OverlayTheme.TEXT);
			stat("Margin", gp(row.margin), profitColor(row.margin));
			stat("ROI", pct(row.roiPct), profitColor(row.margin));
		}
		stat("Limit", gp(row.geLimit), OverlayTheme.TEXT);

		String reason = reasonText(row);
		if (reason != null)
		{
			panel.getChildren().add(LineComponent.builder()
				.left(reason)
				.leftColor(OverlayTheme.MUTED)
				.build());
		}
	}

	/** Short "should I flip this?" badge from Rune Margin's precomputed verdict. */
	private static String verdictLabel(ItemAnalysis a)
	{
		if (a == null || a.verdict == null)
		{
			return "NO DATA";
		}
		switch (a.verdict)
		{
			case "flip":
				if ("high".equals(a.confidence))
				{
					return "GOOD FLIP";
				}
				return "medium".equals(a.confidence) ? "OK FLIP" : "RISKY FLIP";
			case "alch":
				return "HIGH ALCH";
			default:
				return "SKIP";
		}
	}

	private static Color verdictColor(ItemAnalysis a)
	{
		if (a == null || a.verdict == null)
		{
			return OverlayTheme.MUTED;
		}
		switch (a.verdict)
		{
			case "flip":
				if ("high".equals(a.confidence))
				{
					return OverlayTheme.GAIN;
				}
				return "medium".equals(a.confidence) ? OverlayTheme.WARN : OverlayTheme.RISKY;
			case "alch":
				return OverlayTheme.INFO;
			default:
				return OverlayTheme.LOSS;
		}
	}

	/** A one-line reason for the badge: the analysis headline, truncated. */
	private static String reasonText(ItemRow row)
	{
		if (row.analysis == null || row.analysis.headline == null)
		{
			return null;
		}
		String h = row.analysis.headline;
		return h.length() <= 34 ? h : h.substring(0, 33) + "…";
	}

	// --- Set section ------------------------------------------------------------------

	private void appendSet(SetRow row)
	{
		boolean combine = !"break".equalsIgnoreCase(row.bestDirection);
		caption(combine ? "Set · combine" : "Set · break");
		panel.getChildren().add(TitleComponent.builder()
			.text(row.setName == null ? ("Set " + row.setId) : row.setName)
			.color(OverlayTheme.TEXT)
			.build());
		panel.getChildren().add(TitleComponent.builder()
			.text(combine ? "BUY PIECES · SELL SET" : "BUY SET · SELL PIECES")
			.color(profitColor(row.bestProfit))
			.build());

		if (combine)
		{
			// The play the player asked for: which pieces to buy, and at what price.
			appendComponents(row.components, true);
			stat("Total buy", gp(row.setBuy), OverlayTheme.TEXT);
			stat("Sell set", gp(row.setSell), OverlayTheme.GAIN);
		}
		else
		{
			stat("Buy set", gp(row.setBuy), OverlayTheme.TEXT);
			appendComponents(row.components, false);
		}
		stat("Profit", signedGp(row.bestProfit), profitColor(row.bestProfit));
		stat("ROI", pct(combine ? row.combineRoi : row.breakRoi), profitColor(row.bestProfit));
	}

	/** List a set's pieces, one per line, at their buy (combine) or sell (break) price. */
	private void appendComponents(List<SetComponentRow> comps, boolean buySide)
	{
		if (comps == null || comps.isEmpty())
		{
			panel.getChildren().add(LineComponent.builder()
				.left("(pieces unavailable)")
				.leftColor(OverlayTheme.MUTED)
				.build());
			return;
		}
		for (SetComponentRow c : comps)
		{
			Integer price = buySide ? c.buy : c.sell;
			panel.getChildren().add(LineComponent.builder()
				.left("· " + truncate(c.name == null ? ("Item " + c.itemId) : c.name, 16))
				.leftColor(OverlayTheme.MUTED)
				.right(gp(price))
				.rightColor(OverlayTheme.TEXT)
				.build());
		}
	}

	// --- Decant section ---------------------------------------------------------------

	private void appendDecant(DecantRow row)
	{
		caption("Decant");
		panel.getChildren().add(TitleComponent.builder()
			.text((row.baseName == null ? ("Item " + row.id4) : row.baseName) + " (4)")
			.color(OverlayTheme.TEXT)
			.build());
		panel.getChildren().add(TitleComponent.builder()
			.text("BUY (" + row.bestSourceDose + ") · DECANT (4)")
			.color(profitColor(row.profitPer4))
			.build());
		stat("Cost /4", gp(row.costPer4), OverlayTheme.TEXT);
		stat("Sell (4)", gp(row.sell4), OverlayTheme.GAIN);
		stat("Profit", signedGp(row.profitPer4), profitColor(row.profitPer4));
		stat("ROI", pct(row.roiPct), profitColor(row.profitPer4));
	}

	// --- Your offers section ----------------------------------------------------------

	private void appendOffers(GrandExchangeOffer[] offers)
	{
		caption("Your offers");
		boolean first = true;
		for (GrandExchangeOffer offer : offers)
		{
			if (isEmpty(offer))
			{
				continue;
			}
			if (!first)
			{
				panel.getChildren().add(spacer());
			}
			appendOffer(offer);
			first = false;
		}
	}

	private void appendOffer(GrandExchangeOffer offer)
	{
		GrandExchangeOfferState state = offer.getState();
		boolean buy = state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.CANCELLED_BUY;

		int itemId = offer.getItemId();
		int yourPrice = offer.getPrice();
		int total = offer.getTotalQuantity();
		int sold = offer.getQuantitySold();
		int remaining = Math.max(0, total - sold);
		ItemRow row = prices.get(itemId);

		// On a sell, prefer the real cost basis from this session's buy lots over the
		// market rebuy price — it gives the true realized margin.
		Long bought = buy ? null : sessionTracker.avgBuyPrice(itemId);
		Integer perItem;
		Integer basis;
		if (!buy && bought != null)
		{
			perItem = (int) (yourPrice - GeTax.geTax(yourPrice) - bought);
			basis = bought.intValue();
		}
		else
		{
			perItem = perItemProfit(buy, yourPrice, row);
			basis = buy ? yourPrice : (row != null && row.low != null ? row.low : null);
		}
		Double roi = (perItem != null && basis != null && basis > 0) ? perItem * 100.0 / basis : null;

		panel.getChildren().add(LineComponent.builder()
			.left(truncate(itemName(itemId), 18))
			.leftColor(OverlayTheme.TEXT)
			.right(sold + "/" + total)
			.rightColor(OverlayTheme.MUTED)
			.build());
		// Your standing offer price. For a buy the margin belongs to the suggested
		// resale line below, so it isn't repeated here.
		panel.getChildren().add(LineComponent.builder()
			.left((buy ? "Buy @" : "Sell @") + gp(yourPrice))
			.right(buy ? "" : (perItem == null ? "—" : signed(perItem) + " ea"))
			.rightColor(profitColor(perItem))
			.build());
		if (buy)
		{
			// What to list the resale at once it's bought: row.high is the current
			// instant-buy price (a sell listed there fills quickly), and perItem is the
			// after-tax margin at that price. Guides the player straight to a profit.
			Integer target = row != null ? row.high : null;
			panel.getChildren().add(LineComponent.builder()
				.left("Sell @" + gp(target))
				.leftColor(OverlayTheme.GAIN)
				.right(perItem == null ? "—" : signed(perItem) + " ea")
				.rightColor(profitColor(perItem))
				.build());
		}
		else if (bought != null)
		{
			panel.getChildren().add(LineComponent.builder()
				.left("Bought @" + gp(bought))
				.leftColor(OverlayTheme.MUTED)
				.build());
		}
		panel.getChildren().add(LineComponent.builder()
			.left(fillLabel(remaining, row))
			.leftColor(OverlayTheme.MUTED)
			.right(roi == null ? "" : String.format("%.1f%%", roi))
			.rightColor(profitColor(perItem))
			.build());
	}

	/** After-tax margin per item at fill from market prices, or null if not loaded yet. */
	private static Integer perItemProfit(boolean buy, int yourPrice, ItemRow row)
	{
		if (row == null)
		{
			return null;
		}
		if (buy && row.high != null)
		{
			// You buy at yourPrice; resell at the current instant-buy price (row.high).
			return (int) (row.high - GeTax.geTax(row.high) - yourPrice);
		}
		if (!buy && row.low != null)
		{
			// You sell at yourPrice (net of tax); rebuy at the current instant-sell price.
			return (int) (yourPrice - GeTax.geTax(yourPrice) - row.low);
		}
		return null;
	}

	/**
	 * The fill-time line. The ETA assumes you capture <em>all</em> market volume and
	 * ignores how competitive your price is, so it is a best case: the real fill takes
	 * <em>at least</em> this long. We surface it as a lower bound ("7.2h+") rather than a
	 * precise figure so it never over-promises.
	 */
	private static String fillLabel(int remaining, ItemRow row)
	{
		if (remaining <= 0)
		{
			return "filled";
		}
		String eta = fillEta(remaining, row);
		if (eta == null)
		{
			return "fill time —";
		}
		// "<1m" already reads as a bound; everything else gets a trailing "+".
		return "fills in " + (eta.startsWith("<") ? eta : eta + "+");
	}

	/** Optimistic fill ETA (remaining ÷ whole-market volume/hour), or null if the volume
	 *  is unknown. A lower bound — see {@link #fillLabel}. */
	private static String fillEta(int remaining, ItemRow row)
	{
		Double perHour = volPerHour(row);
		if (perHour == null || perHour <= 0)
		{
			return null;
		}
		double hours = remaining / perHour;
		if (hours < 1.0 / 60)
		{
			return "<1m";
		}
		if (hours < 1)
		{
			return Math.round(hours * 60) + "m";
		}
		if (hours < 24)
		{
			return trim(hours) + "h";
		}
		return trim(hours / 24) + "d";
	}

	private static Double volPerHour(ItemRow row)
	{
		if (row == null)
		{
			return null;
		}
		if (row.stats != null && row.stats.vol24h != null && row.stats.vol24h > 0)
		{
			return row.stats.vol24h / 24.0;
		}
		if (row.analysis != null && row.analysis.flip != null
			&& row.analysis.flip.vol4h != null && row.analysis.flip.vol4h > 0)
		{
			return row.analysis.flip.vol4h / 4.0;
		}
		return null;
	}

	// --- Shared helpers ---------------------------------------------------------------

	private boolean geOpen()
	{
		return client.getWidget(GE_INTERFACE_GROUP_ID, 0) != null;
	}

	private static boolean isEmpty(GrandExchangeOffer offer)
	{
		return offer == null
			|| offer.getState() == GrandExchangeOfferState.EMPTY
			|| offer.getTotalQuantity() <= 0;
	}

	private static boolean hasActiveOffer(GrandExchangeOffer[] offers)
	{
		if (offers == null)
		{
			return false;
		}
		for (GrandExchangeOffer offer : offers)
		{
			if (!isEmpty(offer))
			{
				return true;
			}
		}
		return false;
	}

	/** A small muted section subtitle naming what the box is doing. */
	private void caption(String text)
	{
		panel.getChildren().add(LineComponent.builder()
			.left(text)
			.leftColor(OverlayTheme.CAPTION)
			.build());
	}

	private void stat(String left, String right, Color rightColor)
	{
		panel.getChildren().add(LineComponent.builder()
			.left(left)
			.leftColor(OverlayTheme.MUTED)
			.right(right)
			.rightColor(rightColor)
			.build());
	}

	private static LineComponent spacer()
	{
		return LineComponent.builder().left(" ").build();
	}

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

	private static Color profitColor(Integer v)
	{
		if (v == null || v == 0)
		{
			return OverlayTheme.MUTED;
		}
		return v > 0 ? OverlayTheme.GAIN : OverlayTheme.LOSS;
	}

	private static String signed(int v)
	{
		return (v > 0 ? "+" : "") + gp(v);
	}

	/** Signed gp for a nullable value ("—" when null), e.g. a set/decant profit figure. */
	private static String signedGp(Integer v)
	{
		return v == null ? "—" : signed(v);
	}

	private static String gp(Number value)
	{
		return value == null ? "—" : String.format("%,d", value.longValue());
	}

	private static String pct(Double value)
	{
		return value == null ? "—" : String.format("%.1f%%", value);
	}

	private static String trim(double d)
	{
		String s = String.format("%.1f", d);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	private static String truncate(String s, int max)
	{
		return s.length() <= max ? s : s.substring(0, max - 1) + "…";
	}
}
