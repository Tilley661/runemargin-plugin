package com.runemargin.ui;

import com.runemargin.RuneMarginConfig;
import com.runemargin.SortMode;
import com.runemargin.api.DecantRow;
import com.runemargin.api.ItemRow;
import com.runemargin.api.RuneMarginApiClient;
import com.runemargin.api.SetRow;
import com.runemargin.api.TierStatus;
import com.runemargin.overlay.GeOverlay;
import com.runemargin.session.SessionSnapshot;
import com.runemargin.session.SessionTracker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * The Rune Margin sidebar: the best-flips list, item search, the analysis tools
 * (Watch / Decant / Sets) and the live Session / History / Slots views. Network calls
 * are async; every Swing mutation happens on the EDT.
 */
@Singleton
public class RuneMarginPanel extends PluginPanel
{
	private static final Color GAIN = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color LOSS = ColorScheme.PROGRESS_ERROR_COLOR;
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	private static final Color ACCENT = Brand.GOLD; // Rune Margin brand gold
	private static final Color GOLD = Brand.GOLD;
	private static final Color DISCORD = Brand.DISCORD;
	private static final String SITE_URL = "https://runemargin.co.uk";
	private static final String DISCORD_URL = "https://discord.gg/T56VWxchxZ";

	private final RuneMarginApiClient api;
	private final ItemManager itemManager;
	private final RuneMarginConfig config;
	private final GeOverlay overlay;
	private final SessionTracker sessionTracker;

	private final JPanel flipsList = listContainer();
	private final JLabel flipsStatus = new JLabel("Loading best flips…");
	private final JPanel searchList = listContainer();
	private final JLabel searchStatus = new JLabel("Search for an item.");
	private final JPanel watchList = listContainer();
	private final JLabel watchStatus = new JLabel("Loading watchlist…");
	private final JPanel decantList = listContainer();
	private final JLabel decantStatus = new JLabel("Loading decant plays…");
	private final JPanel setsList = listContainer();
	private final JLabel setsStatus = new JLabel("Loading item sets…");
	private final JTextField searchField = new JTextField();
	private SessionPanel sessionPanel;
	private TradeHistoryPanel historyPanel;
	private SlotsPanel slotsPanel;
	private final ConfettiLayer confettiLayer = new ConfettiLayer();
	private Consumer<SessionSnapshot> sessionListener;
	private Timer refreshTimer;

	// A small subscription-status banner pinned above the tabs.
	private final JPanel tierBanner = new JPanel(new BorderLayout());

	// The most recent rows for each list, kept so a sort-order change can re-render
	// instantly without re-fetching.
	private List<ItemRow> lastFlips = List.of();
	private List<ItemRow> lastSearch = List.of();
	private List<ItemRow> lastWatch = List.of();

	// Always-on summary rows above the tabs.
	private final JLabel summaryProfit = new JLabel("0");
	private final JLabel summaryMeta = new JLabel("No flips yet");
	private final JLabel targetLabel = new JLabel();
	private final TargetProgressBar targetBar = new TargetProgressBar();
	private final JPanel topFlipBody = new JPanel(new BorderLayout());
	private final JLabel topFlipPlaceholder = new JLabel("Loading…");

	@Inject
	RuneMarginPanel(RuneMarginApiClient api, ItemManager itemManager, RuneMarginConfig config,
		GeOverlay overlay, SessionTracker sessionTracker)
	{
		super(false);
		this.api = api;
		this.itemManager = itemManager;
		this.config = config;
		this.overlay = overlay;
		this.sessionTracker = sessionTracker;
	}

	/** Build the UI and kick off the first load. Called from the plugin's startUp. */
	public void init()
	{
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		// PluginPanel's no-wrap constructor skips this, so the root shows the Look-and-Feel's
		// light default through our padding — paint it dark to match the rest of the client.
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		sessionPanel = new SessionPanel(itemManager, sessionTracker::reset);
		historyPanel = new TradeHistoryPanel(itemManager);
		slotsPanel = new SlotsPanel(itemManager);

		JTabbedPane tabs = new JTabbedPane();
		tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
		// Small font + minimal insets so all four tabs sit on one row in the narrow panel.
		tabs.setFont(FontManager.getRunescapeSmallFont());
		tabs.putClientProperty("JTabbedPane.tabAreaInsets", new Insets(0, 0, 0, 0));
		Component watchTab = buildListTab(watchStatus, watchList);
		Component decantTab = buildListTab(decantStatus, decantList);
		Component setsTab = buildListTab(setsStatus, setsList);
		tabs.addTab("Flips", buildFlipsTab());
		tabs.addTab("Search", buildSearchTab());
		tabs.addTab("Watch", watchTab);
		tabs.addTab("Decant", decantTab);
		tabs.addTab("Sets", setsTab);
		tabs.addTab("Session", wrapScroll(sessionPanel));
		tabs.addTab("History", wrapScroll(historyPanel));
		tabs.addTab("Slots", wrapScroll(slotsPanel));

		// Lazy-load the tool tabs the first time (and each time) they're opened.
		tabs.addChangeListener(e ->
		{
			Component sel = tabs.getSelectedComponent();
			if (sel == watchTab)
			{
				loadWatchlist();
			}
			else if (sel == decantTab)
			{
				loadDecant();
			}
			else if (sel == setsTab)
			{
				loadSets();
			}
		});

		// The subscription banner sits above the always-on header.
		tierBanner.setVisible(false);
		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		top.add(tierBanner, BorderLayout.NORTH);
		top.add(buildHeader(), BorderLayout.CENTER);

		// All the real content lives in one panel; a transparent, click-through confetti
		// layer floats over the whole of it so a target celebration rains across everything.
		final JPanel content = new JPanel(new BorderLayout());
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.add(top, BorderLayout.NORTH);
		content.add(tabs, BorderLayout.CENTER);
		content.add(buildFooter(), BorderLayout.SOUTH);

		JLayeredPane layers = new JLayeredPane()
		{
			@Override
			public void doLayout()
			{
				for (Component c : getComponents())
				{
					c.setBounds(0, 0, getWidth(), getHeight());
				}
			}

			@Override
			public Dimension getPreferredSize()
			{
				return content.getPreferredSize();
			}
		};
		layers.add(content, JLayeredPane.DEFAULT_LAYER);
		layers.add(confettiLayer, JLayeredPane.PALETTE_LAYER);
		add(layers, BorderLayout.CENTER);

		// Live-refresh the Session summary + tab as fills come in (tracker fires off the EDT).
		sessionListener = snap -> SwingUtilities.invokeLater(() ->
		{
			sessionPanel.update(snap);
			historyPanel.update(snap);
			updateSummary(snap);
		});
		sessionTracker.addListener(sessionListener);
		SessionSnapshot initial = sessionTracker.snapshot();
		sessionPanel.update(initial);
		historyPanel.update(initial);
		updateSummary(initial);

		loadFlips();
		loadTier();

		int period = Math.max(15, config.refreshSeconds()) * 1000;
		refreshTimer = new Timer(period, e -> loadFlips());
		refreshTimer.start();
	}

	/** Stop the refresh timer. Called from the plugin's shutDown. */
	public void teardown()
	{
		if (refreshTimer != null)
		{
			refreshTimer.stop();
			refreshTimer = null;
		}
		if (sessionListener != null)
		{
			sessionTracker.removeListener(sessionListener);
			sessionListener = null;
		}
	}

	/** The always-on collapsible summary + top-flip readouts shown above the tabs. */
	private Component buildHeader()
	{
		JPanel summary = new JPanel();
		summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
		summary.setBackground(ColorScheme.DARK_GRAY_COLOR);
		summary.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
		summaryProfit.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
		summaryProfit.setForeground(Color.WHITE);
		summaryProfit.setAlignmentX(LEFT_ALIGNMENT);
		summaryMeta.setFont(FontManager.getRunescapeSmallFont());
		summaryMeta.setForeground(MUTED);
		summaryMeta.setAlignmentX(LEFT_ALIGNMENT);
		summary.add(summaryProfit);
		summary.add(summaryMeta);

		// Optional profit-target bar (hidden until a target is set in settings).
		targetLabel.setFont(FontManager.getRunescapeSmallFont());
		targetLabel.setForeground(MUTED);
		targetLabel.setAlignmentX(LEFT_ALIGNMENT);
		targetLabel.setBorder(BorderFactory.createEmptyBorder(7, 0, 3, 0));
		targetLabel.setVisible(false);
		targetBar.setVisible(false);
		summary.add(targetLabel);
		summary.add(targetBar);

		topFlipPlaceholder.setFont(FontManager.getRunescapeSmallFont());
		topFlipPlaceholder.setForeground(MUTED);
		topFlipPlaceholder.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		topFlipBody.setBackground(ColorScheme.DARK_GRAY_COLOR);
		topFlipBody.add(topFlipPlaceholder, BorderLayout.CENTER);

		JPanel header = new JPanel(new BorderLayout(0, 6));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
		header.add(new CollapsibleSection("Session", summary), BorderLayout.NORTH);
		header.add(new CollapsibleSection("Top flip right now", topFlipBody), BorderLayout.CENTER);
		return header;
	}

	/** A slim footer pinned to the bottom: links to the website and the community Discord. */
	private Component buildFooter()
	{
		JPanel links = new JPanel();
		links.setLayout(new BoxLayout(links, BoxLayout.X_AXIS));
		links.setBackground(ColorScheme.DARK_GRAY_COLOR);
		links.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(8, 2, 2, 2)));
		links.add(linkLabel("runemargin.co.uk", SITE_URL, ACCENT));
		links.add(javax.swing.Box.createHorizontalGlue());
		links.add(linkLabel("Discord", DISCORD_URL, DISCORD));
		return links;
	}

	/** An underlined, hand-cursor label that opens {@code url} in the browser when clicked. */
	private static JLabel linkLabel(String text, String url, Color color)
	{
		JLabel label = new JLabel("<html><u>" + text + "</u></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(url);
			}
		});
		return label;
	}

	private void updateSummary(SessionSnapshot s)
	{
		summaryProfit.setText(signed(s.realizedProfit));
		summaryProfit.setForeground(profitColor(s.realizedProfit));
		String flips = s.flipCount + (s.flipCount == 1 ? " flip" : " flips");
		summaryMeta.setText(flips + " · ROI " + Format.pct(s.roiPct()));

		long target = config.profitTarget();
		boolean showTarget = target > 0;
		targetLabel.setVisible(showTarget);
		targetBar.setVisible(showTarget);
		if (showTarget)
		{
			targetLabel.setText("Target · " + Format.gp(s.realizedProfit) + " / " + Format.gp(target));
			if (targetBar.setProgress(s.realizedProfit, target))
			{
				confettiLayer.burst(); // just crossed 100% — celebrate across the whole panel
			}
		}
	}

	/** Re-apply the target bar when the goal changes in settings, using the latest session. */
	public void refreshTarget()
	{
		SwingUtilities.invokeLater(() -> updateSummary(sessionTracker.snapshot()));
	}

	/** Push the current GE slot snapshot to the Slots tab (called from the plugin, off-EDT). */
	public void updateSlots(List<GeSlot> slots)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (slotsPanel != null)
			{
				slotsPanel.update(slots);
			}
		});
	}

	private void updateTopFlip(List<ItemRow> rows)
	{
		topFlipBody.removeAll();
		if (rows == null || rows.isEmpty())
		{
			topFlipPlaceholder.setText("No flips available.");
			topFlipBody.add(topFlipPlaceholder, BorderLayout.CENTER);
		}
		else
		{
			topFlipBody.add(new FlipRowPanel(rows.get(0), itemManager, this::onSelect), BorderLayout.CENTER);
		}
		topFlipBody.revalidate();
		topFlipBody.repaint();
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

	private Component buildFlipsTab()
	{
		return buildListTab(flipsStatus, flipsList);
	}

	/** A status label over a scrolling list — the shared shape of the list tabs. */
	private Component buildListTab(JLabel status, JPanel list)
	{
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		styleStatus(status);
		status.setBorder(BorderFactory.createEmptyBorder(4, 2, 6, 2));
		wrap.add(status, BorderLayout.NORTH);
		wrap.add(wrapScroll(list), BorderLayout.CENTER);
		return wrap;
	}

	/** Give a list's "N items" status line a readable, on-brand light colour. */
	private static void styleStatus(JLabel status)
	{
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(Brand.PARCHMENT);
	}

	private Component buildSearchTab()
	{
		JPanel wrap = new JPanel(new BorderLayout(0, 4));
		wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel bar = new JPanel(new BorderLayout(4, 0));
		bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		searchField.addActionListener(e -> runSearch());
		JButton go = new JButton("Go");
		go.setHorizontalAlignment(SwingConstants.CENTER);
		go.addActionListener(e -> runSearch());
		bar.add(searchField, BorderLayout.CENTER);
		bar.add(go, BorderLayout.EAST);

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		top.add(bar, BorderLayout.NORTH);
		styleStatus(searchStatus);
		searchStatus.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
		top.add(searchStatus, BorderLayout.SOUTH);

		wrap.add(top, BorderLayout.NORTH);
		wrap.add(wrapScroll(searchList), BorderLayout.CENTER);
		return wrap;
	}

	private void loadFlips()
	{
		flipsStatus.setText("Loading best flips…");
		api.bestFlips(config.minVolume(), 50, config.membership().members(),
			rows -> SwingUtilities.invokeLater(() ->
			{
				lastFlips = rows != null ? rows : List.of();
				List<ItemRow> sorted = sortRows(lastFlips);
				renderRows(flipsList, flipsStatus, sorted);
				updateTopFlip(sorted);
			}),
			err -> SwingUtilities.invokeLater(() ->
			{
				flipsStatus.setText("Rune Margin unavailable.");
				updateTopFlip(null);
			}));
	}

	private void runSearch()
	{
		String query = searchField.getText().trim();
		if (query.isEmpty())
		{
			return;
		}
		searchStatus.setText("Searching \"" + query + "\"…");
		api.search(query, 25, config.membership().members(),
			rows -> SwingUtilities.invokeLater(() ->
			{
				lastSearch = rows != null ? rows : List.of();
				renderRows(searchList, searchStatus, sortRows(lastSearch));
			}),
			err -> SwingUtilities.invokeLater(() -> searchStatus.setText("Search failed.")));
	}

	private void loadWatchlist()
	{
		if (config.accessToken() == null || config.accessToken().trim().isEmpty())
		{
			// No token → nothing to sync. Point the user at how to set it up.
			renderWatchEmpty("Add your access token in settings to sync your watchlist.",
				"Get a token on runemargin.co.uk →", SITE_URL + "/account");
			return;
		}
		watchStatus.setText("Loading watchlist…");
		api.watchlist(
			rows -> SwingUtilities.invokeLater(() ->
			{
				lastWatch = rows != null ? rows : List.of();
				if (lastWatch.isEmpty())
				{
					renderWatchEmpty("No starred items yet.",
						"Star items on runemargin.co.uk →", SITE_URL + "/market");
				}
				else
				{
					renderRows(watchList, watchStatus, sortRows(lastWatch));
				}
			}),
			err -> SwingUtilities.invokeLater(() -> watchStatus.setText("Rune Margin unavailable.")));
	}

	/** Empty / no-token watch state: a status line plus a clickable link to the website. */
	private void renderWatchEmpty(String message, String ctaText, String url)
	{
		watchList.removeAll();
		watchStatus.setText(message);

		JLabel cta = new JLabel("<html><u>" + ctaText + "</u></html>");
		cta.setFont(FontManager.getRunescapeSmallFont());
		cta.setForeground(ACCENT);
		cta.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		cta.setBorder(BorderFactory.createEmptyBorder(8, 2, 4, 2));
		cta.setAlignmentX(LEFT_ALIGNMENT);
		cta.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(url);
			}
		});

		watchList.add(cta);
		watchList.revalidate();
		watchList.repaint();
	}

	private void loadDecant()
	{
		decantStatus.setText("Loading decant plays…");
		api.decanting(
			rows -> SwingUtilities.invokeLater(() -> renderDecant(filterByMembership(rows, r -> r.members))),
			err -> SwingUtilities.invokeLater(() -> decantStatus.setText("Rune Margin unavailable.")));
	}

	private void loadSets()
	{
		setsStatus.setText("Loading item sets…");
		api.sets(
			rows -> SwingUtilities.invokeLater(() -> renderSets(filterByMembership(rows, r -> r.members))),
			err -> SwingUtilities.invokeLater(() -> setsStatus.setText("Rune Margin unavailable.")));
	}

	/**
	 * Apply the configured P2P/F2P filter to a list of rows, the same way the Flips and
	 * Search lists do — except the sets/decant endpoints don't take a {@code members}
	 * query param, so we filter client-side on each row's own members flag. "Both" (null)
	 * keeps everything.
	 */
	private <T> List<T> filterByMembership(List<T> rows, Predicate<T> isMembers)
	{
		Boolean want = config.membership().members();
		if (want == null || rows == null)
		{
			return rows;
		}
		List<T> out = new ArrayList<>();
		for (T row : rows)
		{
			if (isMembers.test(row) == want)
			{
				out.add(row);
			}
		}
		return out;
	}

	private void renderDecant(List<DecantRow> rows)
	{
		decantList.removeAll();
		if (rows == null || rows.isEmpty())
		{
			decantStatus.setText("No decant plays.");
		}
		else
		{
			decantStatus.setText(rows.size() + " plays");
			boolean first = true;
			for (DecantRow row : rows)
			{
				if (!first)
				{
					decantList.add(spacer());
				}
				decantList.add(new DecantRowPanel(row, itemManager, this::onSelectDecant));
				first = false;
			}
		}
		decantList.revalidate();
		decantList.repaint();
	}

	private void renderSets(List<SetRow> rows)
	{
		setsList.removeAll();
		if (rows == null || rows.isEmpty())
		{
			setsStatus.setText("No item sets.");
		}
		else
		{
			setsStatus.setText(rows.size() + " sets");
			boolean first = true;
			for (SetRow row : rows)
			{
				if (!first)
				{
					setsList.add(spacer());
				}
				setsList.add(new SetRowPanel(row, itemManager, this::onSelectSet));
				first = false;
			}
		}
		setsList.revalidate();
		setsList.repaint();
	}

	private void renderRows(JPanel list, JLabel status, List<ItemRow> rows)
	{
		list.removeAll();
		if (rows == null || rows.isEmpty())
		{
			status.setText("No results.");
		}
		else
		{
			status.setText(rows.size() + " items");
			Consumer<ItemRow> onClick = this::onSelect;
			boolean first = true;
			for (ItemRow row : rows)
			{
				if (!first)
				{
					list.add(spacer());
				}
				list.add(new FlipRowPanel(row, itemManager, onClick));
				first = false;
			}
		}
		list.revalidate();
		list.repaint();
	}

	/**
	 * React to an access-token change: the caller's tier (and therefore the profit cap
	 * on the flips they can see) may have changed, so refresh both.
	 */
	public void onAccessTokenChanged()
	{
		SwingUtilities.invokeLater(() ->
		{
			loadFlips();
			loadTier();
			loadWatchlist();
		});
	}

	/** Re-fetch the lists with the current membership (P2P/F2P) filter applied. */
	public void reloadLists()
	{
		SwingUtilities.invokeLater(() ->
		{
			loadFlips();
			if (!searchField.getText().trim().isEmpty())
			{
				runSearch();
			}
			// Sets and Decant filter client-side, so re-fetch them too — otherwise a
			// membership change wouldn't re-apply while one of those tabs is open.
			loadSets();
			loadDecant();
		});
	}

	private void loadTier()
	{
		api.tier(
			status -> SwingUtilities.invokeLater(() -> updateTierBanner(status)),
			err -> SwingUtilities.invokeLater(() -> updateTierBanner(null)));
	}

	private static final Color BANNER_BG = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color BANNER_BG_HOVER = ColorScheme.DARK_GRAY_HOVER_COLOR;

	/**
	 * Render the subscription banner: a quiet confirmation for Pro/Supporter, or an
	 * upgrade nudge (with the profit cap that's currently hiding the biggest flips)
	 * for everyone below. Clickable banners spell out that they open the website and
	 * show a link-styled, underlined call-to-action.
	 */
	private void updateTierBanner(TierStatus status)
	{
		tierBanner.removeAll();
		clearMouseListeners(tierBanner);
		if (status == null)
		{
			// Couldn't resolve tier (offline / API down): don't show a misleading banner.
			tierBanner.setVisible(false);
			tierBanner.revalidate();
			tierBanner.repaint();
			return;
		}

		String tier = status.effectiveTier == null ? "anon" : status.effectiveTier;
		boolean unlimited = "pro".equals(tier) || "supporter".equals(tier);
		boolean tokenSet = config.accessToken() != null && !config.accessToken().trim().isEmpty();

		final String statusText;
		final String ctaText; // null == not clickable
		final Color accent;
		final String openUrl;
		if (unlimited)
		{
			statusText = ("supporter".equals(tier) ? "♛ Supporter — all flips unlocked"
				: "★ Pro — all flips unlocked");
			ctaText = null;
			accent = Brand.GOLD_BRIGHT;
			openUrl = null;
		}
		else if (tokenSet && !status.authenticated)
		{
			// A token is configured but the server didn't accept it (wrong token, or
			// used from a different IP than it was locked to).
			statusText = "Access token not active";
			ctaText = "Check it on runemargin.co.uk →";
			accent = LOSS;
			openUrl = SITE_URL + "/account";
		}
		else
		{
			String cap = status.cap == null ? null : Format.gp(status.cap);
			statusText = cap == null ? tierLabel(tier)
				: tierLabel(tier) + " — flips over " + cap + " hidden";
			ctaText = "Upgrade to Pro on runemargin.co.uk →";
			accent = GOLD;
			openUrl = SITE_URL + "/pricing";
		}

		JLabel label = new JLabel(bannerHtml(statusText, ctaText, accent));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));

		tierBanner.setBackground(BANNER_BG);
		tierBanner.add(label, BorderLayout.CENTER);
		if (openUrl != null)
		{
			tierBanner.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			tierBanner.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					LinkBrowser.browse(openUrl);
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					tierBanner.setBackground(BANNER_BG_HOVER);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					tierBanner.setBackground(BANNER_BG);
				}
			});
		}
		else
		{
			tierBanner.setCursor(Cursor.getDefaultCursor());
		}
		tierBanner.setVisible(true);
		tierBanner.revalidate();
		tierBanner.repaint();
	}

	/**
	 * Two-line banner markup: the status line, and (when clickable) an underlined,
	 * link-coloured call-to-action naming the website. A fixed width makes the label
	 * wrap in the narrow panel instead of truncating with an ellipsis.
	 */
	private static String bannerHtml(String statusText, String ctaText, Color accent)
	{
		String hex = toHex(accent);
		StringBuilder sb = new StringBuilder("<html><div style='width:168px'>");
		sb.append("<font color='").append(hex).append("'>").append(statusText).append("</font>");
		if (ctaText != null)
		{
			sb.append("<br><font color='").append(hex).append("'><u>")
				.append(ctaText).append("</u></font>");
		}
		return sb.append("</div></html>").toString();
	}

	private static String toHex(Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	private static void clearMouseListeners(JPanel panel)
	{
		for (MouseListener existing : panel.getMouseListeners())
		{
			panel.removeMouseListener(existing);
		}
	}

	private static String tierLabel(String tier)
	{
		switch (tier)
		{
			case "free":
				return "Free plan";
			case "basic":
				return "Basic plan";
			default:
				return "Free view"; // anon / unknown
		}
	}

	/**
	 * Re-apply the configured sort to the already-fetched rows and re-render, without
	 * a network round-trip. Called when the user changes the sort in settings.
	 */
	public void reSort()
	{
		SwingUtilities.invokeLater(() ->
		{
			List<ItemRow> sortedFlips = sortRows(lastFlips);
			renderRows(flipsList, flipsStatus, sortedFlips);
			updateTopFlip(sortedFlips);
			renderRows(searchList, searchStatus, sortRows(lastSearch));
			if (!lastWatch.isEmpty())
			{
				renderRows(watchList, watchStatus, sortRows(lastWatch));
			}
		});
	}

	/** A copy of {@code rows} ordered by the configured {@link SortMode} (highest first). */
	private List<ItemRow> sortRows(List<ItemRow> rows)
	{
		if (rows == null || rows.isEmpty())
		{
			return List.of();
		}
		SortMode mode = config.sortMode();
		List<ItemRow> sorted = new ArrayList<>(rows);
		// Highest value first; rows missing the metric sink to the bottom.
		sorted.sort(Comparator.comparing(
			row -> sortKey(row, mode),
			Comparator.nullsLast(Comparator.reverseOrder())));
		return sorted;
	}

	/** The numeric sort key for a row under {@code mode}, or null if unavailable. */
	private static Double sortKey(ItemRow row, SortMode mode)
	{
		switch (mode)
		{
			case RM_SCORE:
				return row.stats == null || row.stats.flipScore == null ? null : row.stats.flipScore.doubleValue();
			case MARGIN:
				return row.margin == null ? null : row.margin.doubleValue();
			case ROI:
				return row.roiPct;
			case POTENTIAL_PROFIT:
				return row.potentialProfit == null ? null : row.potentialProfit.doubleValue();
			case VOLUME_24H:
				return row.stats == null || row.stats.vol24h == null ? null : row.stats.vol24h.doubleValue();
			case REALISTIC_PROFIT:
			default:
				if (row.stats != null && row.stats.realisticProfit != null)
				{
					return row.stats.realisticProfit.doubleValue();
				}
				// Fall back to potential profit so the default order is still sensible
				// when the volume-aware figure is absent.
				return row.potentialProfit == null ? null : row.potentialProfit.doubleValue();
		}
	}

	private void onSelect(ItemRow row)
	{
		overlay.setItem(row);
	}

	/** Focus the in-game overlay on a set play (its buy-the-pieces breakdown). */
	private void onSelectSet(SetRow row)
	{
		overlay.setSet(row);
	}

	/** Focus the in-game overlay on a decant play (what dose to buy). */
	private void onSelectDecant(DecantRow row)
	{
		overlay.setDecant(row);
	}

	private static JPanel listContainer()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(ColorScheme.DARK_GRAY_COLOR);
		return p;
	}

	private static Component spacer()
	{
		JPanel s = new JPanel();
		s.setBackground(ColorScheme.DARK_GRAY_COLOR);
		s.setPreferredSize(new Dimension(0, 4));
		s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
		return s;
	}

	private static JScrollPane wrapScroll(Component view)
	{
		// North-align the list so rows stack from the top rather than centring, and
		// track the viewport width so rows never exceed the visible area — otherwise the
		// viewport sizes to the content's preferred width and clips the right edge (the
		// score pill) since horizontal scrolling is disabled.
		JPanel north = new WidthTrackingPanel(new BorderLayout());
		north.setBackground(ColorScheme.DARK_GRAY_COLOR);
		north.add(view, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(north);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		// Kill the default white viewport that shows through below north-aligned content.
		scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		return scroll;
	}

	/**
	 * A scroll view that always matches the viewport's width (so rows are constrained to
	 * the visible area and wide content is truncated, not clipped off-screen) while still
	 * scrolling vertically to its own preferred height.
	 */
	private static final class WidthTrackingPanel extends JPanel implements Scrollable
	{
		WidthTrackingPanel(java.awt.LayoutManager layout)
		{
			super(layout);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
		{
			return visibleRect.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}
}
