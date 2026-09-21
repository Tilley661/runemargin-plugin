package com.runemargin.ui;

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.border.Border;

/**
 * The Rune Margin brand palette, mirrored from the website's theme
 * (frontend/src/theme/global.css). Keep these in sync with the CSS custom
 * properties so the plugin and the site read as the same product.
 */
final class Brand
{
	private Brand()
	{
	}

	static final Color GOLD = new Color(0xF0, 0xA8, 0x30);        // --gold
	static final Color GOLD_BRIGHT = new Color(0xFF, 0xC2, 0x4B); // --gold-bright (hover)
	static final Color GOLD_DIM = new Color(0xB0, 0x7D, 0x28);    // --gold-dim
	static final Color BORDER_GOLD = new Color(0x4A, 0x3A, 0x16); // --border-gold (subtle frame)
	static final Color PARCHMENT = new Color(0xE8, 0xDC, 0xC0);   // --parchment (headings/text)
	static final Color TEXT_MUTED = new Color(0x9A, 0x90, 0x7C);  // --text-muted
	static final Color UP = new Color(0x3F, 0xBF, 0x6F);          // --up (gains)
	static final Color DOWN = new Color(0xE0, 0x52, 0x4A);        // --down (losses)
	static final Color DISCORD = new Color(0x58, 0x65, 0xF2);     // Discord blurple

	// A subtly embossed gold frame: a warmer highlight on the top/left edges and a
	// deeper tone on the bottom/right, so cards catch the light like the site's
	// gold-edged panels without shouting.
	private static final Color FRAME_HI = new Color(0x6B, 0x53, 0x22); // top-left highlight
	private static final Color FRAME_LO = new Color(0x33, 0x28, 0x11); // bottom-right shadow

	/** A subtly embossed gold frame (brighter top-left, deeper bottom-right). */
	static Border frame()
	{
		return BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 1, 0, 0, FRAME_HI),
			BorderFactory.createMatteBorder(0, 0, 1, 1, FRAME_LO));
	}

	/** {@link #frame()} plus uniform inner padding, for content rows / cards. */
	static Border card(int pad)
	{
		return BorderFactory.createCompoundBorder(frame(),
			BorderFactory.createEmptyBorder(pad, pad, pad, pad));
	}
}
