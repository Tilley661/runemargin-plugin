package com.runemargin.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A lightweight titled section whose body can be collapsed by clicking the header.
 * Used for the always-on Session summary and Top-flip readouts above the tabs.
 */
class CollapsibleSection extends JPanel
{
	private static final Color ACCENT = Brand.GOLD;

	private final JLabel arrow = new JLabel("▾"); // ▾
	private final Component body;
	private boolean expanded = true;

	CollapsibleSection(String title, Component body)
	{
		this.body = body;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		// A subtly embossed gold frame, matching the site's gold-edged cards.
		setBorder(Brand.frame());

		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		arrow.setForeground(ACCENT);
		arrow.setFont(FontManager.getRunescapeSmallFont());

		JLabel titleLabel = new JLabel(title.toUpperCase());
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setFont(FontManager.getRunescapeSmallFont());

		header.add(arrow, BorderLayout.WEST);
		header.add(titleLabel, BorderLayout.CENTER);
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				toggle();
			}
		});

		add(header, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);
	}

	private void toggle()
	{
		expanded = !expanded;
		body.setVisible(expanded);
		arrow.setText(expanded ? "▾" : "▸"); // ▾ / ▸
		revalidate();
		repaint();
	}
}
