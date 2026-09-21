package com.runemargin.ui;

import com.runemargin.api.DecantRow;
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
 * One decanting play: the (4) icon, name, the cheapest source dose, and the per-(4)
 * after-tax profit / ROI / Rune Margin score. Clicking focuses the in-game overlay on
 * the play (via {@code onClick}), which spells out what dose to buy.
 */
class DecantRowPanel extends JPanel
{
	DecantRowPanel(DecantRow row, ItemManager itemManager, Consumer<DecantRow> onClick)
	{
		setLayout(new BorderLayout(6, 0));
		setBorder(Brand.card(5));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 32));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		AsyncBufferedImage image = itemManager.getImage(row.id4);
		image.addTo(icon);
		add(icon, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(getBackground());

		JLabel name = new JLabel((row.baseName == null ? "Item " + row.id4 : row.baseName) + " (4)");
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);

		JLabel figures = new JLabel("buy (" + row.bestSourceDose + ") · +" + Format.gp(row.profitPer4)
			+ " ea · " + Format.pct(row.roiPct));
		figures.setFont(FontManager.getRunescapeSmallFont());
		figures.setForeground(profitColor(row.profitPer4));

		JLabel sub = new JLabel("~" + Format.gp(row.realisticProfitTotal) + "/day");
		sub.setFont(FontManager.getRunescapeSmallFont());
		sub.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		text.add(name);
		text.add(figures);
		text.add(sub);
		add(text, BorderLayout.CENTER);

		// Rune Margin score pill, matching the flip rows.
		JComponent pillWrap = new JPanel(new GridBagLayout());
		pillWrap.setOpaque(false);
		pillWrap.add(new ScorePill(row.rmScore, null));
		add(pillWrap, BorderLayout.EAST);

		// Allow the row to shrink to the panel width so the EAST pill isn't clipped
		// (see FlipRowPanel).
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

	private static Color profitColor(Integer v)
	{
		if (v == null)
		{
			return ColorScheme.LIGHT_GRAY_COLOR;
		}
		return v >= 0 ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR;
	}
}
