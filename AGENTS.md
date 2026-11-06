# AGENTS.md

## Overview

**Coinvane** — single-module Android app (`:app`): live prices for actively traded Binance USDT pairs (~500) shown in
the user's currency (25) and language (11), period-based gainers/losers, charts, price alerts, portfolio, home screen
widget. v8 UI: one `MainActivity` with a fixed bottom navigation (Home, Markets, Explore, Portfolio, Profile).
Plain **Java** (no Kotlin), View XML + ViewBinding (no Compose), Material 3.
Package / applicationId stays `com.abraxel.cryptocurrency` (existing Play listing) — do not rename it.

Toolchain: Gradle 9.8 wrapper, AGP 9.5 (Groovy DSL, `plugins {}` + `dependencyResolutionManagement` in `settings.gradle`),
compileSdk/targetSdk 37, minSdk 26, Java 17.

## Commands

- Unit tests: `./gradlew testDebugUnitTest` (JVM only; real `org.json` is on the test classpath)
- Lint: `./gradlew lintDebug` (errors fail the build) · Build: `./gradlew assembleDebug` · Release: `./gradlew bundleRelease`
- CI: `.github/workflows/android.yml` runs tests, lint, debug + release builds
- `local.properties` holds the SDK path and must not be committed

## Architecture

- **All prices are USD (USDT) internally.** Conversion + locale formatting happens only in `data/Money`
  (`Money.current(context)` = selected currency + cached FX rate; falls back to USD if the rate is unknown).
- `data/Binance` — REST (OkHttp) to `data-api.binance.vision` + JSON parsers (unit-tested). No API key.
  Tickers whose `closeTime` is >6 h old are delisted pairs with frozen data and are dropped in `parseCoins`.
  Tracks `x-mbx-used-weight-1m` / 429 `Retry-After`; optional loads (periods, sparklines) call `canSpend()` first.
- `data/Period` — 5m, 15m, 1h, 4h, 24h, 7d (lists) + 30d (coin detail only; Binance `windowSize` max is 7d).
  Non-24h changes come from `/api/v3/ticker?windowSize=` in 100-symbol batches (~200 weight each, ~1450 per period),
  cached per `ttlMs`; a window that covers < 50% of the period (newly listed coin) yields NaN, never a fake %.
- `data/Market` — process singleton behind the five tabs: 24h list (REST every 60 s), period windows on demand,
  ONE `PriceSocket` whose streams = what the visible tab asked for (+ alert symbols). Started/stopped by
  `MainActivity.onStart/onStop`. Listeners get `PRICES` (keep row order) vs `DATA` (may re-sort).
- `data/CoinFilter` — pure list logic (type, period change, column sort, min volume, movement counts, volume surge).
  Unknown change (NaN) never enters a period ranking. `direction()` uses the 2-decimal display threshold.
- `data/Sparks` — sparkline cache from real klines, only for visible rows.
- `data/PriceSocket` — one WebSocket per screen to `data-stream.binance.vision/stream`; `setStreams()` diffs
  SUBSCRIBE/UNSUBSCRIBE. Opened in `onStart`, closed in `onStop` (no background service).
- UI: `ui/TabFragment` (tabs are shown/hidden; only the visible one listens), `ui/CoinAdapter` + `item_coin` is the
  shared row (Home, Markets, Explore). Lists re-sort only when the user isn't scrolling (and at most every 5 s on
  live ticks) — rows must not jump under the finger. `home/`, `markets/`, `explore/`, `profile/` packages.
- `data/Fx` — USD→fiat rates from open.er-api.com, cached in prefs, refreshed when older than 6 h.
- `data/Store` — favorites, alerts (cached in memory), portfolio holdings in SharedPreferences. Migrates v6 data
  (`BTCTRY` symbols, TRY alert targets) on read.
- `data/Settings` — currency, theme (default dark since v8), dynamic colors, remembered UI choices (`ui_*` keys). Language is stored by AppCompat (`setApplicationLocales`,
  `autoStoreLocales` service in the manifest; `generateLocaleConfig` for Android 13+ system settings).
- `alerts/Alerts.check()` fires alerts from live ticks; `PriceAlertWorker` (WorkManager, 15 min) when the app is closed.
- `portfolio/` — `Holding`, `Totals`, `Allocation` (pure, tested), `PortfolioFragment`, `HoldingDialog` (shared with
  coin detail), `CoinPicker`. No performance history is stored, so there is no "Performance" view.
- `alerts/AlertsActivity` — list of active alerts (bell on Home, row in Profile). Not a notification center.
- `widget/PriceWidget` — AppWidgetProvider + `UpdateWorker`; refreshed when favorites change.
- `chart/CandleChart` — MPAndroidChart `CombinedChart`: candles/line + volume bars, index-based X axis.

## Gotchas

- Strings: English is the default (`values/`); 10 translations in `values-<lang>`. Add every new string to all 11
  files. Keep brand names (Coinvane, coin names) untranslated. Arabic is RTL — use start/end, never left/right.
- `res/resources.properties` (`unqualifiedResLocale=en`) is required by `generateLocaleConfig`.
- Release signing reads `keystore.properties` at the repo root (see `keystore.properties.example`); never commit keys.
- Crashlytics is applied only when `app/google-services.json` exists (gitignored).
- R8 + resource shrinking are on for release; no reflection is used, so no custom keep rules.
- Widget layouts are RemoteViews: no AppCompat attributes (`app:tint` etc.), no theme attrs — use plain colors.
- Edge-to-edge is enforced (targetSdk ≥ 35): screens pad their root with `Ui.padForSystemBars`. `MainActivity` handles
  insets itself and hides the bottom navigation while the keyboard is open.
- Number inputs use `Ui.decimalInput()`: plain `numberDecimal` silently drops "," (Turkish "0,05" became "005").
- Never show fabricated data: no sector/news/listing-date sections (no source), no dominance/market cap metric.
- Icons are 24dp stroke vectors generated with one stroke width (1.8); keep new icons in the same style.
- Code comments are **Turkish**; `Changelog.txt` (user-facing) and `fastlane/metadata` (store listing) need updates
  for notable changes. Brand assets (SVG sources) live in `design/`.
