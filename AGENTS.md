# AGENTS.md

## Overview

**Coinvane** — single-module Android app (`:app`): live prices for Binance USDT pairs (700+ coins) shown in the user's
currency (25) and language (11), charts, price alerts, portfolio, home screen widget.
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
- `data/PriceSocket` — one WebSocket per screen to `data-stream.binance.vision/stream`; `setStreams()` diffs
  SUBSCRIBE/UNSUBSCRIBE. Main list subscribes only to visible rows (+ symbols with alerts). Opened in `onStart`,
  closed in `onStop` (no background service).
- `data/Fx` — USD→fiat rates from open.er-api.com, cached in prefs, refreshed when older than 6 h.
- `data/Store` — favorites, alerts (cached in memory), portfolio holdings in SharedPreferences. Migrates v6 data
  (`BTCTRY` symbols, TRY alert targets) on read.
- `data/Settings` — currency, theme, dynamic colors. Language is stored by AppCompat (`setApplicationLocales`,
  `autoStoreLocales` service in the manifest; `generateLocaleConfig` for Android 13+ system settings).
- `alerts/Alerts.check()` fires alerts from live ticks; `PriceAlertWorker` (WorkManager, 15 min) when the app is closed.
- `portfolio/` — `Holding`, `Totals` (pure, tested), `PortfolioActivity`.
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
- Edge-to-edge is enforced (targetSdk ≥ 35): screens pad their root with `Ui.padForSystemBars`.
- Code comments are **Turkish**; `Changelog.txt` (user-facing) and `fastlane/metadata` (store listing) need updates
  for notable changes. Brand assets (SVG sources) live in `design/`.
