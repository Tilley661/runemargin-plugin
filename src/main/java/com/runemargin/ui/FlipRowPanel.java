package com.runemargin.ui;

import com.runemargin.api.ItemRow;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
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
 * One clickable item row: icon, name, after-tax margin / ROI / GE limit and the
 * Rune Margin verdict headline. Clicking notifies {@code onClick} (used to focus
 * the item for the GE overlay and prefill the calculator).
 */
class FlipRowPanel extends JPanel
{
	FlipRowPanel(ItemRow row, ItemManager itemManager, Consumer<ItemRow> onClick)
	{
		setLayout(new BorderLayout(6, 0));
		// An embossed gold frame gives the list the "carded" look of the site.
		setBorder(Brand.card(5));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Icon.
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 32));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		AsyncBufferedImage image = itemManager.getImage(row.id);
		image.addTo(icon);
		add(icon, BorderLayout.WEST);

		// Text column.
		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(getBackground());

		JLabel name = new JLabel(row.name == null ? ("Item " + row.id) : row.name);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);

		JLabel figures = new JLabel(figuresText(row));
		figures.setFont(FontManager.getRunescapeSmallFont());
		figures.setForeground(marginColor(row.margin));

		JLabel headline = new JLabel(headlineText(row));
		headline.setFont(FontManager.getRunescapeSmallFont());
		headline.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		text.add(name);
		text.add(figures);
		text.add(headline);
		add(text, BorderLayout.CENTER);

		// Rune Margin score pill (colour-banded), matching the website's ScoreBadge.
		Integer score = row.stats == null ? null : row.stats.flipScore;
		JComponent pillWrap = new JPanel(new GridBagLayout()); // centres the pill vertically
		pillWrap.setOpaque(false);
		pillWrap.add(new ScorePill(score, row.stats == null ? null : row.stats.scoreReasons));
		add(pillWrap, BorderLayout.EAST);

		// Let the row shrink to the panel width: without this its minimum width is the
		// sum of child minimums (the long CENTER label reports its full text), so the
		// BoxLayout list lays it out oversized and the EAST score pill is clipped.
		setMinimumSize(new Dimension(0, 0));

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.accept(row);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
				text.setBackground(getBackground());
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(ColorScheme.DARKER_GRAY_COLOR);
				text.setBackground(getBackground());
			}
		});
	}

	private static String figuresText(ItemRow row)
	{
		return "+" + Format.gp(row.margin) + " ea · " + Format.pct(row.roiPct)
			+ " · lim " + Format.gp(row.geLimit);
	}

	private static String headlineText(ItemRow row)
	{
		if (row.analysis != null && row.analysis.headline != null)
		{
			return row.analysis.headline;
		}
		return "Buy " + Format.gp(row.low) + " · Sell " + Format.gp(row.high);
	}

	private static Color marginColor(Integer margin)
	{
		if (margin == null)
		{
			return ColorScheme.LIGHT_GRAY_COLOR;
		}
		return margin >= 0 ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR;
	}
}
