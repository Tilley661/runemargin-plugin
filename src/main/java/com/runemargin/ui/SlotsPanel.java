package com.runemargin.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
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
 * The Slots tab: a live view of the player's Grand Exchange offers — buy/sell, item, how far
 * each has filled (a progress bar) and the offer price. Fed immutable {@link GeSlot}
 * snapshots from the plugin via {@link #update(List)} on the EDT.
 */
class SlotsPanel extends JPanel
{
	private static final Color BUY = new Color(0x4A, 0x9B, 0xE0);  // blue — gp going out
	private static final Color SELL = Brand.GOLD;                  // gold — gp coming in
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;

	private final ItemManager itemManager;
	private final JPanel list = new JPanel();

	SlotsPanel(ItemManager itemManager)
	{
		this.itemManager = itemManager;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(10, 8, 8, 8));

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(ColorScheme.DARK_GRAY_COLOR);
		list.setAlignmentX(LEFT_ALIGNMENT);
		add(list);

		update(List.of());
	}

	/** Rebuild the list from the latest slot snapshot. Empty slots are dropped. */
	void update(List<GeSlot> slots)
	{
		list.removeAll();
		boolean any = false;
		if (slots != null)
		{
			for (GeSlot s : slots)
			{
				if (s == null)
				{
					continue;
				}
				if (any)
				{
					list.add(Box.createVerticalStrut(6));
				}
				list.add(fill(card(s)));
				any = true;
			}
		}
		if (!any)
		{
			JLabel empty = new JLabel("No active GE offers.");
			empty.setForeground(MUTED);
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));
			empty.setAlignmentX(LEFT_ALIGNMENT);
			list.add(empty);
		}
		list.revalidate();
		list.repaint();
	}

	private JPanel card(GeSlot s)
	{
		Color accent = s.buy ? BUY : SELL;

		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(Brand.card(6));

		// Header: direction on the left, offer status on the right.
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(card.getBackground());
		header.setAlignmentX(LEFT_ALIGNMENT);
		JLabel dir = new JLabel(s.buy ? "Buy" : "Sell");
		dir.setFont(FontManager.getRunescapeBoldFont());
		dir.setForeground(accent);
		JLabel state = new JLabel(statusText(s));
		state.setFont(FontManager.getRunescapeSmallFont());
		state.setForeground(statusColor(s));
		header.add(dir, BorderLayout.WEST);
		header.add(state, BorderLayout.EAST);

		// Body: icon + name / quantity.
		JPanel body = new JPanel(new BorderLayout(6, 0));
		body.setBackground(card.getBackground());
		body.setAlignmentX(LEFT_ALIGNMENT);
		body.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 32));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		AsyncBufferedImage image = itemManager.getImage(s.itemId);
		image.addTo(icon);
		body.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(card.getBackground());
		JLabel name = new JLabel(s.name);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);
		JLabel qty = new JLabel(Format.exact(s.sold) + " / " + Format.exact(s.total));
		qty.setFont(FontManager.getRunescapeSmallFont());
		qty.setForeground(MUTED);
		text.add(name);
		text.add(qty);
		body.add(text, BorderLayout.CENTER);

		// Progress bar — green when complete, gold while filling.
		Bar bar = new Bar(s.fraction(), s.complete() ? Brand.UP : Brand.GOLD);

		JLabel price = new JLabel(Format.exact(s.price) + " coins");
		price.setFont(FontManager.getRunescapeSmallFont());
		price.setForeground(MUTED);
		price.setAlignmentX(LEFT_ALIGNMENT);
		price.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));

		card.add(header);
		card.add(body);
		card.add(bar);
		card.add(price);
		return card;
	}

	private static String statusText(GeSlot s)
	{
		switch (s.status)
		{
			case FILLED:
				return "filled";
			case CANCELLED:
				return "cancelled";
			default:
				return s.buy ? "buying" : "selling";
		}
	}

	private static Color statusColor(GeSlot s)
	{
		switch (s.status)
		{
			case FILLED:
				return Brand.UP;
			case CANCELLED:
				return MUTED;
			default:
				return MUTED;
		}
	}

	/** Left-align a component and let it fill the panel width under the vertical BoxLayout. */
	private static <T extends JComponent> T fill(T c)
	{
		c.setAlignmentX(LEFT_ALIGNMENT);
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		c.setMinimumSize(new Dimension(0, 0));
		return c;
	}

	/** A slim rounded progress bar filled to {@code frac} in {@code fill}. */
	private static final class Bar extends JComponent
	{
		private final double frac;
		private final Color fill;

		Bar(double frac, Color fill)
		{
			this.frac = Math.max(0, Math.min(1, frac));
			this.fill = fill;
			setPreferredSize(new Dimension(0, 9));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 9));
			setAlignmentX(LEFT_ALIGNMENT);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			g2.setColor(ColorScheme.SCROLL_TRACK_COLOR);
			g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
			int fw = (int) Math.round((w - 2) * frac);
			if (fw > 0)
			{
				g2.setColor(fill);
				g2.fillRoundRect(1, 1, fw, h - 3, h, h);
			}
			g2.dispose();
		}
	}
}
