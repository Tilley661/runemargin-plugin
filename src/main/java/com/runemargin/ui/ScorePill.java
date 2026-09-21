package com.runemargin.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The 0–100 Rune Margin score as a colour-banded pill, matching the website's
 * {@code ScoreBadge}: ≥80 green, ≥60 lime, ≥40 amber, else red; hover shows the
 * score reasons. Painted with a translucent rounded background over the row.
 */
class ScorePill extends JLabel
{
	// Floor width so a 1–3 digit score always has room and the row's EAST slot can
	// never collapse to zero (which would hide the pill entirely).
	private static final int MIN_WIDTH = 26;

	private final Color pillBg;

	ScorePill(Integer score, List<String> reasons)
	{
		super(score == null ? "—" : score.toString(), SwingConstants.CENTER);
		setFont(FontManager.getRunescapeSmallFont().deriveFont(java.awt.Font.BOLD));
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(1, 7, 1, 7));

		if (score == null)
		{
			setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			pillBg = null;
			return;
		}

		Band band = bandFor(score);
		setForeground(band.fg);
		pillBg = band.bg;
		if (reasons != null && !reasons.isEmpty())
		{
			setToolTipText(String.join(" • ", reasons));
		}
	}

	@Override
	public Dimension getPreferredSize()
	{
		Dimension d = super.getPreferredSize();
		d.width = Math.max(d.width, MIN_WIDTH);
		return d;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		if (pillBg != null)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(pillBg);
			g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
			g2.dispose();
		}
		super.paintComponent(g);
	}

	private static Band bandFor(int score)
	{
		if (score >= 80)
		{
			return new Band(new Color(0x3F, 0xBF, 0x6F), new Color(63, 191, 111, 110));
		}
		if (score >= 60)
		{
			return new Band(new Color(0xA7, 0xC8, 0x5A), new Color(160, 200, 90, 100));
		}
		if (score >= 40)
		{
			return new Band(new Color(0xF0, 0xB1, 0x32), new Color(240, 177, 50, 100));
		}
		return new Band(new Color(0xE0, 0x52, 0x4A), new Color(224, 82, 74, 100));
	}

	private static final class Band
	{
		final Color fg;
		final Color bg;

		Band(Color fg, Color bg)
		{
			this.fg = fg;
			this.bg = bg;
		}
	}
}
