package com.runemargin.ui;

import com.runemargin.session.Flip;
import com.runemargin.session.FlipPiece;
import com.runemargin.session.SessionSnapshot;
import com.runemargin.session.Trade;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * The History tab: a per-item trade log of this session's completed buys and sells,
 * so the player can see what they last paid / sold at and decide what price to chase.
 * One row per item (most recently traded first) showing its latest buy and sell.
 */
class TradeHistoryPanel extends JPanel
{
	private static final Color GAIN = ColorScheme.PROGRESS_COMPLETE_COLOR; // gp coming in / profit
	private static final Color LOSS = ColorScheme.PROGRESS_ERROR_COLOR;    // a real, realized loss
	private static final Color BOUGHT = Brand.PARCHMENT;                   // buy info — neutral, not a loss
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;

	private final ItemManager itemManager;
	private final JPanel list = new JPanel();

	TradeHistoryPanel(ItemManager itemManager)
	{
		this.itemManager = itemManager;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(10, 8, 8, 8));

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(ColorScheme.DARK_GRAY_COLOR);
		list.setAlignmentX(LEFT_ALIGNMENT);
		add(list);
	}

	void update(SessionSnapshot s)
	{
		list.removeAll();

		// recentTrades is newest-first; group by item preserving that order, so the
		// most recently traded items lead and each item keeps its latest fills.
		Map<Integer, ItemHistory> byItem = new LinkedHashMap<>();
		for (Trade t : s.recentTrades)
		{
			ItemHistory h = byItem.computeIfAbsent(t.itemId, k -> new ItemHistory(t.itemId, t.itemName));
			if (t.side == Trade.Side.BUY && h.lastBuy == null)
			{
				h.lastBuy = t;
			}
			else if (t.side == Trade.Side.SELL && h.lastSell == null)
			{
				h.lastSell = t;
			}
		}

		// Realized flips per item (matched buy→sell, after tax) — the only figures we
		// can honestly call profit. A sell with no entry here has no known cost basis.
		Map<Integer, FlipAgg> realized = new HashMap<>();
		for (Flip f : s.recentFlips)
		{
			realized.computeIfAbsent(f.itemId, k -> new FlipAgg()).add(f);
		}

		// Names of every piece a set flip consumed this session — so a piece's own row can
		// read "used in a set" rather than looking like it's still held.
		Set<String> setPieceNames = new HashSet<>();
		for (FlipAgg agg : realized.values())
		{
			if ("set".equals(agg.basis))
			{
				setPieceNames.addAll(agg.pieces.keySet());
			}
		}
		Set<Integer> held = s.heldItemIds != null ? s.heldItemIds : new HashSet<>();

		if (byItem.isEmpty())
		{
			JLabel empty = new JLabel("No trades yet this session.");
			empty.setForeground(MUTED);
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));
			empty.setAlignmentX(LEFT_ALIGNMENT);
			list.add(empty);
		}
		else
		{
			List<ItemHistory> items = new ArrayList<>(byItem.values());
			boolean first = true;
			for (ItemHistory h : items)
			{
				if (!first)
				{
					list.add(Box.createVerticalStrut(4));
				}
				list.add(fill(itemRow(h, realized.get(h.itemId), held, setPieceNames)));
				first = false;
			}
		}

		list.revalidate();
		list.repaint();
	}

	private JPanel itemRow(ItemHistory h, FlipAgg flip, Set<Integer> held, Set<String> setPieceNames)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 8));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 32));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		AsyncBufferedImage image = itemManager.getImage(h.itemId);
		image.addTo(icon);
		row.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(row.getBackground());

		JLabel name = new JLabel(h.itemName != null ? h.itemName : "Item " + h.itemId);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);

		boolean hasBasis = flip != null && flip.quantity > 0;
		text.add(name);
		text.add(buyLabel(h.lastBuy, flip));
		text.add(sellLabel(h.lastSell, hasBasis));
		text.add(profitLabel(h, flip, held, setPieceNames));

		// For a set flip, spell out the pieces it was assembled from and their cost, so the
		// set's cost basis isn't a mystery number disconnected from the pieces you bought.
		if (flip != null && !flip.pieces.isEmpty())
		{
			for (Map.Entry<String, long[]> e : flip.pieces.entrySet())
			{
				long qty = e.getValue()[0];
				long cost = e.getValue()[1];
				JLabel piece = small();
				String q = qty > 1 ? " ×" + Format.exact(qty) : "";
				piece.setText("· " + e.getKey() + q + " · " + Format.gp(cost));
				piece.setForeground(MUTED);
				text.add(piece);
			}
		}
		row.add(text, BorderLayout.CENTER);
		return row;
	}

	/** The buy line: this session's fill, else the cost basis implied by a matched flip
	 *  (a purchase from an earlier session), else nothing tracked. */
	private static JLabel buyLabel(Trade buy, FlipAgg flip)
	{
		JLabel l = small();
		if (buy != null)
		{
			l.setText("Bought " + Format.gp(buy.unitPrice) + " ea · x" + Format.exact(buy.quantity)
				+ " · " + Format.timeAgo(buy.timeMillis));
			l.setForeground(BOUGHT);
		}
		else if (flip != null && flip.quantity > 0)
		{
			// A flip with no direct buy of this item id: a set (bought as pieces), a decant
			// (bought as doses), or a same-item buy carried in from an earlier session.
			String note;
			if ("set".equals(flip.basis))
			{
				note = "from pieces";
			}
			else if ("decant".equals(flip.basis))
			{
				note = "from doses";
			}
			else
			{
				note = "earlier session";
			}
			l.setText("Bought ~" + Format.gp(flip.cost / flip.quantity) + " ea · " + note);
			l.setForeground(BOUGHT);
		}
		else
		{
			l.setText("Bought — no fills");
			l.setForeground(MUTED);
		}
		return l;
	}

	/** The sell line. Green only when a cost basis exists, so a bank sell (unknown cost)
	 *  never reads as profit. */
	private static JLabel sellLabel(Trade sell, boolean hasBasis)
	{
		JLabel l = small();
		if (sell == null)
		{
			l.setText("Sold — no fills");
			l.setForeground(MUTED);
			return l;
		}
		l.setText("Sold " + Format.gp(sell.unitPrice) + " ea · x" + Format.exact(sell.quantity)
			+ " · " + Format.timeAgo(sell.timeMillis));
		l.setForeground(hasBasis ? GAIN : MUTED);
		return l;
	}

	/** The bottom line: realized after-tax profit when matched, a "no cost basis" note for
	 *  bank stock, a "used in a set/decant" note for a buy consumed into another flip, or a
	 *  holding note for a buy still sitting in inventory. */
	private static JLabel profitLabel(ItemHistory h, FlipAgg flip, Set<Integer> held, Set<String> setPieceNames)
	{
		JLabel l = small();
		if (flip != null && flip.quantity > 0)
		{
			l.setText("Profit " + signed(flip.profit) + " · after tax");
			l.setForeground(flip.profit >= 0 ? GAIN : LOSS);
		}
		else if (h.lastSell != null)
		{
			l.setText("No cost basis · sold from stock");
			l.setForeground(MUTED);
		}
		else if (held.contains(h.itemId))
		{
			l.setText("Holding · not sold yet");
			l.setForeground(MUTED);
		}
		else
		{
			// Bought, never sold under its own id, and no longer held — it was consumed into
			// a set (a piece) or a decant (a dose).
			boolean inSet = h.itemName != null && setPieceNames.contains(h.itemName);
			l.setText("Used in a " + (inSet ? "set" : "decant"));
			l.setForeground(MUTED);
		}
		return l;
	}

	private static JLabel small()
	{
		JLabel l = new JLabel();
		l.setFont(FontManager.getRunescapeSmallFont());
		return l;
	}

	private static String signed(long v)
	{
		return (v > 0 ? "+" : "") + Format.gp(v);
	}

	/** Make a component fill the panel width under BoxLayout (Y) and left-align it. */
	private static <T extends JComponent> T fill(T c)
	{
		c.setAlignmentX(LEFT_ALIGNMENT);
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		// Zero minimum width so rows shrink to the panel width rather than overflowing.
		c.setMinimumSize(new Dimension(0, 0));
		return c;
	}

	/** The latest buy and sell we've seen for a single item this session. */
	private static final class ItemHistory
	{
		final int itemId;
		final String itemName;
		Trade lastBuy;
		Trade lastSell;

		ItemHistory(int itemId, String itemName)
		{
			this.itemId = itemId;
			this.itemName = itemName;
		}
	}

	/** This session's realized flips for one item, summed: matched quantity, cost basis,
	 *  after-tax profit, how it was costed, and (for sets) the pieces consumed. */
	private static final class FlipAgg
	{
		long quantity;
		long cost;
		long profit;
		String basis;
		/** Consumed set pieces, summed by name: {@code name -> {quantity, cost}}. */
		final Map<String, long[]> pieces = new LinkedHashMap<>();

		void add(Flip f)
		{
			quantity += f.quantity;
			cost += f.cost;
			profit += f.profit;
			if (f.basis != null)
			{
				basis = f.basis;
			}
			if (f.pieces != null)
			{
				for (FlipPiece p : f.pieces)
				{
					long[] agg = pieces.computeIfAbsent(p.name != null ? p.name : "?", k -> new long[2]);
					agg[0] += p.quantity;
					agg[1] += p.cost;
				}
			}
		}
	}
}
