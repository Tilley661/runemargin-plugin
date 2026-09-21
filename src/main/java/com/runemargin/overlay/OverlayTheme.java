package com.runemargin.overlay;

import java.awt.Color;
import java.awt.Dimension;
import net.runelite.client.ui.overlay.components.PanelComponent;

/**
 * Shared palette and panel styling for the Rune Margin overlay, so the box reads as one
 * deliberate, on-brand unit rather than default RuneLite text. Colours match the website
 * / side-panel {@code ScorePill} bands.
 */
final class OverlayTheme
{
	private OverlayTheme()
	{
	}

	/** Rune Margin gold — the brand accent (see frontend --gold). */
	static final Color BRAND = new Color(0xF0, 0xA8, 0x30);
	static final Color GAIN = new Color(0x3F, 0xBF, 0x6F);   // frontend --up (positive figures)
	static final Color LOSS = new Color(0xE0, 0x52, 0x4A);   // frontend --down
	static final Color WARN = new Color(0xF0, 0xB1, 0x32);   // amber
	static final Color RISKY = new Color(0xE0, 0x88, 0x3A);  // orange
	static final Color INFO = new Color(0x4A, 0x9B, 0xE0);   // blue (alch)
	static final Color TEXT = new Color(0xE6, 0xE6, 0xE6);
	static final Color MUTED = new Color(0xA8, 0xA8, 0xA8);
	static final Color CAPTION = new Color(0x86, 0x8C, 0x94); // section subtitles

	// A darker, less transparent panel than the RuneLite default so the branded box
	// stands out cleanly against a busy Grand Exchange scene.
	private static final Color BG = new Color(0x11, 0x14, 0x1A, 0xDC);

	// A fixed width stops the box resizing as figures change — the main "polish" win.
	static final int WIDTH = 162;

	static void style(PanelComponent panel)
	{
		panel.setBackgroundColor(BG);
		panel.setPreferredSize(new Dimension(WIDTH, 0));
	}
}
