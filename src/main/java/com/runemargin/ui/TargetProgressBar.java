package com.runemargin.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The Session profit-target bar: a grey progress fill inside a gold border while you climb
 * toward the goal, flipping to a glowing gold fill once you hit 100%. Cosmetic only — the
 * confetti celebration is handled at the panel level by {@link ConfettiLayer}.
 */
class TargetProgressBar extends JComponent
{
	private static final int HEIGHT = 20;
	private static final Color TRACK = ColorScheme.SCROLL_TRACK_COLOR;
	private static final Color PROGRESS = new Color(0x8A, 0x8A, 0x8A);

	private double frac;
	private boolean done;

	TargetProgressBar()
	{
		setPreferredSize(new Dimension(0, HEIGHT));
		setMinimumSize(new Dimension(0, HEIGHT));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
		setAlignmentX(LEFT_ALIGNMENT);
	}

	/**
	 * Update the fill for {@code current}/{@code target} gp. Returns {@code true} the first
	 * time an update crosses into "reached", so the caller can fire the confetti once.
	 */
	boolean setProgress(long current, long target)
	{
		boolean wasDone = done;
		double f = target > 0 ? (double) current / target : 0;
		done = f >= 1.0;
		frac = Math.max(0, Math.min(f, 1.0));
		repaint();
		return done && !wasDone;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth();
		int h = getHeight();
		int arc = h;

		// Track.
		g2.setColor(TRACK);
		g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

		// Fill — grey while climbing, gold once the goal is reached.
		int fillW = (int) Math.round((w - 2) * frac);
		if (fillW > 0)
		{
			g2.setColor(done ? Brand.GOLD_BRIGHT : PROGRESS);
			g2.fillRoundRect(1, 1, fillW, h - 3, arc, arc);
		}

		// Gold border, always — brighter and thicker once reached (a subtle "glow").
		g2.setStroke(new BasicStroke(done ? 2f : 1.4f));
		g2.setColor(done ? Brand.GOLD_BRIGHT : Brand.GOLD);
		g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);

		// Centred caption.
		String text = done ? "Target reached!" : Math.round(frac * 100) + "%";
		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();
		int tx = (w - fm.stringWidth(text)) / 2;
		int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
		g2.setColor(done ? new Color(0x20, 0x18, 0x08) : Color.WHITE);
		g2.drawString(text, tx, ty);
		g2.dispose();
	}
}
