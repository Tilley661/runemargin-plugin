package com.runemargin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(RuneMarginConfig.GROUP)
public interface RuneMarginConfig extends Config
{
	String GROUP = "runemargin";
	// The API base URL is intentionally hard-coded (not a config item) so it can be
	// updated in lockstep with the plugin rather than left to users to get right.
	String DEFAULT_API_BASE_URL = "https://api.runemargin.co.uk";

	// Data-disclosure section required by the Plugin Hub for plugins that talk to a
	// third-party server (see the wiki "Information about the Plugin Hub"). Placed first
	// so the explanation of what leaves the client is the top thing users see.
	@ConfigSection(
		name = "Data & privacy",
		description = "Rune Margin fetches live prices, best-flip suggestions and item "
			+ "analysis from the runemargin.co.uk API (api.runemargin.co.uk). WHAT IS "
			+ "SENT to that server: the item ids you view, your best-flips/search filters, and — only "
			+ "if you paste one below — your access token. WHAT IS NOT SENT: your Grand "
			+ "Exchange offers, profit/loss and RuneScape name never leave your client "
			+ "(the session tracker is entirely local). All in-game data is read-only; "
			+ "the plugin never sends input or automates offers.",
		position = 0
	)
	String dataSection = "dataSection";

	@ConfigSection(
		name = "Display & lists",
		description = "What the in-game overlay and side-panel lists show.",
		position = 1
	)
	String displaySection = "displaySection";

	@ConfigItem(
		keyName = "accessToken",
		name = "API access token",
		description = "Optional personal access token from your runemargin.co.uk account "
			+ "(Account → Plugin access token). Sent to runemargin.co.uk to unlock your "
			+ "plan's flips in-game. Usable from up to 5 networks per week, so a changing "
			+ "home IP keeps working.",
		secret = true,
		section = dataSection,
		position = 1
	)
	default String accessToken()
	{
		return "";
	}

	@ConfigItem(
		keyName = "showGeOverlay",
		name = "Grand Exchange overlay",
		description = "Show a live buy/sell/margin overlay for the selected item on the Grand Exchange.",
		section = displaySection,
		position = 2
	)
	default boolean showGeOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGeOffers",
		name = "Show 'Your offers'",
		description = "Show the 'Your offers' section of the Grand Exchange overlay — live "
			+ "margin, profit-at-fill and time-to-fill for your active offers. Turn off to "
			+ "hide the offer info while keeping the rest of the overlay.",
		section = displaySection,
		position = 3
	)
	default boolean showGeOffers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "membership",
		name = "Show items",
		description = "Whether the Flips and Search lists show members (P2P), free-to-play "
			+ "(F2P) or both.",
		section = displaySection,
		position = 5
	)
	default Membership membership()
	{
		return Membership.P2P;
	}

	@ConfigItem(
		keyName = "sortMode",
		name = "Sort list by",
		description = "How the Flips and Search lists are ordered. The server ranks flips "
			+ "by volume-aware realistic profit; this re-sorts the shown rows.",
		section = displaySection,
		position = 6
	)
	default SortMode sortMode()
	{
		return SortMode.REALISTIC_PROFIT;
	}

	@ConfigItem(
		keyName = "minVolume",
		name = "Best-flips min volume",
		description = "Minimum recent traded volume for items shown in the best-flips list.",
		section = displaySection,
		position = 7
	)
	default int minVolume()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "refreshSeconds",
		name = "Refresh interval (s)",
		description = "How often the best-flips list refreshes while the panel is open.",
		section = displaySection,
		position = 8
	)
	default int refreshSeconds()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "profitTarget",
		name = "Session profit target (gp)",
		description = "Your gp goal for the session. A progress bar in the Session summary "
			+ "fills as you profit and celebrates at 100%. Set 0 to hide it.",
		section = displaySection,
		position = 9
	)
	default int profitTarget()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "priceHint",
		name = "Offer price hint",
		description = "While setting up a Grand Exchange offer, show the recommended price to "
			+ "type — the buy (low) price on a buy, the sell (high) price on a sell. Read-only: "
			+ "the plugin never sets the price for you.",
		section = displaySection,
		position = 10
	)
	default boolean showPriceHint()
	{
		return true;
	}
}
