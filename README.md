# Rune Margin — RuneLite plugin

An in-game companion for [runemargin.co.uk](https://runemargin.co.uk), an OSRS
Grand Exchange flipping tool. The plugin is a thin **client** of Rune Margin's
public JSON API — no game automation, just information overlays.

## Features

- **Best-flips panel** — a sidebar list of the top flips right now (after-tax
  margin, ROI, GE limit) from `GET /analytics/best-flips`.
- **Item search** — look up any item's live buy/sell/margin from `GET /items`.
- **GE margin overlay** — shows Buy / Sell / Margin (post-tax) / ROI / Limit for
  the item you're viewing, drawn over the Grand Exchange.
- **Set trading** — combine/break arbitrage per GE item set, from
  `GET /analytics/sets`.
- **Potion decanting** — best decant-to-(4) plays per potion family, from
  `GET /analytics/decanting`.
- **Watchlist, session tracker & offer slots** — a local profit/loss session log,
  trade history, live GE offer slots, and your starred items synced with a token.

The plugin talks to Rune Margin's public JSON API. No login is required — a
signed-out caller gets the free tier value cap. Pasting a
personal access token (Account → Plugin access token on runemargin.co.uk) unlocks
your plan's data in-game. Your Grand Exchange offers, profit/loss and RuneScape
name never leave the client; the session tracker is entirely local.

## Project layout

```
src/main/java/com/runemargin/
  RuneMarginPlugin.java     entry point + wiring
  RuneMarginConfig.java     settings (API base URL, overlay toggle, min volume, refresh)
  api/                      OkHttp+Gson client and DTOs (mirror the website's api.ts)
  calc/                     GeTax — port of the website's getax.ts
  ui/                       PluginPanel (Flips / Search / Calculator / Session / History / Slots tabs)
  overlay/                  GeOverlay
src/test/java/com/runemargin/
  GeTaxTest.java                       proves the GE-tax math port is faithful
  RuneMarginPluginTest.java            bootstrap main that launches a dev client
```

## Getting started

1. Install **JDK 11** and IntelliJ IDEA (Community is fine).
2. Open this folder in IntelliJ and let it import the Gradle project
   (this also generates the Gradle wrapper).
3. Run `RuneMarginPluginTest.main` to launch the RuneLite dev client with the
   plugin sideloaded. Open the Rune Margin panel from the sidebar.

Run the unit tests with `gradle test` (or the Gradle tool window in IntelliJ).

## Notes

- The Grand Exchange overlay auto-follows the item being set up in the offer editor
  (and the item you click in the panel), reading only read-only client vars — it
  never sets prices or automates offers.
- ToS-clean by design: information only, no input automation. All in-game data is
  read-only.

## License

Copyright (c) 2026 Rune Margin.

Released under the **BSD 2-Clause License** — see [LICENSE](LICENSE). This is the
license used across the RuneLite ecosystem and required by the Plugin Hub.
