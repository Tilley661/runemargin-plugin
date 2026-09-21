package com.runemargin.ui;

import com.runemargin.session.Flip;
import com.runemargin.session.SessionSnapshot;
import com.runemargin.session.SessionSummary;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * The Session tab: a prominent realized-profit "hero", supporting stats, and a
 * scrollable list of recent realized flips. All updates arrive via
 * {@link #update(SessionSnapshot)} on the EDT.
 */
class SessionPanel extends JPanel
{
	private static final Color ACCENT = Brand.GOLD; // Rune Margin brand gold
	private static final Color GAIN = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color LOSS = ColorScheme.PROGRESS_ERROR_COLOR;
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("d MMM, HH:mm", Locale.ENGLISH);

	private final ItemManager itemManager;
	private final JPanel flipList = new JPanel();
	private final JPanel pastList = new JPanel();

	private final JLabel heroProfit = new JLabel("0");
	private final JLabel roiValue = subStat();
	private final JLabel perHourValue = subStat();
	private final JLabel flipsValue = subStat();

	private final JLabel taxValue = statValue();
	private final JLabel bestValue = statValue();
	private final JLabel worstValue = statValue();
	private final JLabel investedValue = statValue();

	SessionPanel(ItemManager itemManager, Runnable onReset)
	{
		this.itemManager = itemManager;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(10, 8, 8, 8));

		add(fill(buildHero()));
		add(Box.createVerticalStrut(8));
		add(fill(buildStats()));
		add(Box.createVerticalStrut(8));
		add(fill(buildResetButton(onReset)));
		add(Box.createVerticalStrut(10));
		add(fill(sectionHeader("Recent flips")));
		add(Box.createVerticalStrut(4));

		flipList.setLayout(new BoxLayout(flipList, BoxLayout.Y_AXIS));
		flipList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		flipList.setAlignmentX(LEFT_ALIGNMENT);
		add(flipList);

		add(Box.createVerticalStrut(10));
		add(fill(sectionHeader("Past sessions")));
		add(Box.createVerticalStrut(4));
		pastList.setLayout(new BoxLayout(pastList, BoxLayout.Y_AXIS));
		pastList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		pastList.setAlignmentX(LEFT_ALIGNMENT);
		add(pastList);
	}

	private JPanel buildHero()
	{
		JPanel card = card(new BorderLayout(0, 2));

		JLabel caption = new JLabel("SESSION PROFIT");
		caption.setForeground(MUTED);
		caption.setFont(FontManager.getRunescapeSmallFont());

		heroProfit.setForeground(Color.WHITE);
		heroProfit.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 22f));

		JPanel subRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		subRow.setBackground(card.getBackground());
		subRow.add(roiValue);
		subRow.add(separator());
		subRow.add(perHourValue);
		subRow.add(separator());
		subRow.add(flipsValue);

		card.add(caption, BorderLayout.NORTH);
		card.add(heroProfit, BorderLayout.CENTER);
		card.add(subRow, BorderLayout.SOUTH);
		return card;
	}

	private JPanel buildStats()
	{
		JPanel card = card(new GridLayout(0, 2, 8, 6));
		card.add(statCaption("Tax paid"));
		card.add(taxValue);
		card.add(statCaption("Best flip"));
		card.add(bestValue);
		card.add(statCaption("Worst flip"));
		card.add(worstValue);
		card.add(statCaption("Invested"));
		card.add(investedValue);
		return card;
	}

	private JButton buildResetButton(Runnable onReset)
	{
		JButton reset = new JButton("New session");
		reset.setHorizontalAlignment(SwingConstants.CENTER);
		reset.setFocusPainted(false);
		reset.setFont(FontManager.getRunescapeSmallFont());
		reset.setForeground(Color.WHITE);
		reset.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		reset.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
		reset.addActionListener(e -> onReset.run());
		return reset;
	}

	void update(SessionSnapshot s)
	{
		heroProfit.setText(signed(s.realizedProfit));
		heroProfit.setForeground(profitColor(s.realizedProfit));

		roiValue.setText("ROI " + Format.pct(s.roiPct()));
		perHourValue.setText(Format.gp(perHour(s)) + "/hr");
		flipsValue.setText(s.flipCount + (s.flipCount == 1 ? " flip" : " flips"));

		taxValue.setText(Format.gp(s.taxPaid));
		setSignedStat(bestValue, s.bestProfit);
		setSignedStat(worstValue, s.worstProfit);
		investedValue.setText(Format.gp(s.openInvested));

		flipList.removeAll();
		if (s.recentFlips.isEmpty())
		{
			JLabel empty = new JLabel("No completed flips yet.");
			empty.setForeground(MUTED);
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));
			empty.setAlignmentX(LEFT_ALIGNMENT);
			flipList.add(empty);
		}
		else
		{
			boolean first = true;
			for (Flip flip : s.recentFlips)
			{
				if (!first)
				{
					flipList.add(Box.createVerticalStrut(4));
				}
				flipList.add(fill(flipRow(flip)));
				first = false;
			}
		}
		flipList.revalidate();
		flipList.repaint();

		pastList.removeAll();
		if (s.pastSessions.isEmpty())
		{
			JLabel empty = new JLabel("No past sessions yet.");
			empty.setForeground(MUTED);
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));
			empty.setAlignmentX(LEFT_ALIGNMENT);
			pastList.add(empty);
		}
		else
		{
			boolean first = true;
			for (SessionSummary summary : s.pastSessions)
			{
				if (!first)
				{
					pastList.add(Box.createVerticalStrut(4));
				}
				pastList.add(fill(pastRow(summary)));
				first = false;
			}
		}
		pastList.revalidate();
		pastList.repaint();
	}

	private JPanel pastRow(SessionSummary s)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 8));

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(row.getBackground());

		JLabel when = new JLabel(DATE_FORMAT.format(new Date(s.endMillis)));
		when.setFont(FontManager.getRunescapeSmallFont());
		when.setForeground(Color.WHITE);

		String flips = s.flipCount + (s.flipCount == 1 ? " flip" : " flips");
		JLabel detail = new JLabel(flips + " · ROI " + Format.pct(s.roiPct())
			+ " · " + duration(s.startMillis, s.endMillis));
		detail.setFont(FontManager.getRunescapeSmallFont());
		detail.setForeground(MUTED);

		text.add(when);
		text.add(detail);
		row.add(text, BorderLayout.CENTER);

		JLabel profit = new JLabel(signed(s.profit));
		profit.setFont(FontManager.getRunescapeSmallFont());
		profit.setForeground(profitColor(s.profit));
		row.add(profit, BorderLayout.EAST);
		return row;
	}

	private static String duration(long startMillis, long endMillis)
	{
		long mins = Math.max(0, (endMillis - startMillis) / 60000);
		if (mins < 60)
		{
			return mins + "m";
		}
		return (mins / 60) + "h " + (mins % 60) + "m";
	}

	private JPanel flipRow(Flip flip)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 8));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 32));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		AsyncBufferedImage image = itemManager.getImage(flip.itemId, (int) Math.max(1, flip.quantity), flip.quantity > 1);
		image.addTo(icon);
		row.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(row.getBackground());

		JLabel name = new JLabel(flip.itemName != null ? flip.itemName : "Item " + flip.itemId);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);

		JLabel detail = new JLabel("x" + Format.exact(flip.quantity) + " · " + Format.gp(flip.netProceeds) + " in");
		detail.setFont(FontManager.getRunescapeSmallFont());
		detail.setForeground(MUTED);

		text.add(name);
		text.add(detail);
		row.add(text, BorderLayout.CENTER);

		JLabel profit = new JLabel(signed(flip.profit));
		profit.setFont(FontManager.getRunescapeSmallFont());
		profit.setForeground(profitColor(flip.profit));
		row.add(profit, BorderLayout.EAST);
		return row;
	}

	private void setSignedStat(JLabel label, Long value)
	{
		label.setText(value == null ? "—" : signed(value));
		label.setForeground(value == null ? Color.WHITE : profitColor(value));
	}

	private static long perHour(SessionSnapshot s)
	{
		long elapsed = Math.max(1L, System.currentTimeMillis() - s.sessionStartMillis);
		return Math.round(s.realizedProfit / (elapsed / 3_600_000.0));
	}

	private static String signed(long v)
	{
		return (v > 0 ? "+" : "") + Format.gp(v);
	}

	private static Color profitColor(long v)
	{
		if (v == 0)
		{
			return MUTED;
		}
		return v > 0 ? GAIN : LOSS;
	}

	// --- small builders / layout helpers ---------------------------------------

	private static JPanel card(java.awt.LayoutManager layout)
	{
		JPanel p = new JPanel(layout);
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
		return p;
	}

	private static JLabel sectionHeader(String text)
	{
		JLabel l = new JLabel(text);
		l.setForeground(ACCENT);
		l.setFont(FontManager.getRunescapeSmallFont());
		return l;
	}

	private static JLabel separator()
	{
		JLabel l = new JLabel("   ·   ");
		l.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		l.setFont(FontManager.getRunescapeSmallFont());
		return l;
	}

	private static JLabel subStat()
	{
		JLabel l = new JLabel("—");
		l.setForeground(MUTED);
		l.setFont(FontManager.getRunescapeSmallFont());
		return l;
	}

	private static JLabel statCaption(String text)
	{
		JLabel l = new JLabel(text);
		l.setForeground(MUTED);
		l.setFont(FontManager.getRunescapeSmallFont());
		return l;
	}

	private static JLabel statValue()
	{
		JLabel l = new JLabel("0");
		l.setForeground(Color.WHITE);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setHorizontalAlignment(SwingConstants.RIGHT);
		return l;
	}

	/** Make a component fill the panel width under BoxLayout (Y) and left-align it. */
	private static <T extends JComponent> T fill(T c)
	{
		c.setAlignmentX(LEFT_ALIGNMENT);
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		// Zero minimum width so the row shrinks to the panel width (BoxLayout otherwise
		// honours the row's minimum — the full CENTER text — and clips the EAST figure).
		c.setMinimumSize(new Dimension(0, 0));
		return c;
	}
}
