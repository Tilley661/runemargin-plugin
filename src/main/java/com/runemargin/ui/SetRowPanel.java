package com.runemargin.ui;

import com.runemargin.api.SetRow;
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
 * One item-set play: the set icon, name, the better direction (combine/break) with its
 * after-tax profit / ROI, and the Rune Margin score. Clicking focuses the in-game
 * overlay on the set (via {@code onClick}), which lists the pieces to buy.
 */
class SetRowPanel extends JPanel
{
	SetRowPanel(SetRow row, ItemManager itemManager, Consumer<SetRow> onClick)
	{
		setLayout(new BorderLayout(6, 0));
		setBorder(Brand.card(5));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 32));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		AsyncBufferedImage image = itemManager.getImage(row.setId);
		image.addTo(icon);
		add(icon, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(getBackground());

		JLabel name = new JLabel(row.setName == null ? "Set " + row.setId : row.setName);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);

		boolean combine = !"break".equalsIgnoreCase(row.bestDirection);
		Double roi = combine ? row.combineRoi : row.breakRoi;
		JLabel figures = new JLabel(direction(row.bestDirection) + " · +" + Format.gp(row.bestProfit)
			+ " · " + Format.pct(roi));
		figures.setFont(FontManager.getRunescapeSmallFont());
		figures.setForeground(profitColor(row.bestProfit));

		JLabel sub = new JLabel(row.pieceCount + " pieces · ~" + Format.gp(row.realisticProfit) + "/day");
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

	private static String direction(String dir)
	{
		return "break".equalsIgnoreCase(dir) ? "BREAK" : "COMBINE";
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
