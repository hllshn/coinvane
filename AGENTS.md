# AGENTS.md

## Overview

Single-module Android app (`:app`): live prices for every Binance TRY pair, candlestick charts, price alerts.
Plain **Java** (no Kotlin), View XML + ViewBinding (no Compose), Material 3. Package: `com.abraxel.cryptocurrency`.

Toolchain: Gradle 9.8 wrapper, AGP 9.5 (Groovy DSL, `plugins {}` + `dependencyResolutionManagement` in `settings.gradle`),
compileSdk/targetSdk 37, minSdk 24, Java 17.

## Commands

- Unit tests: `./gradlew testDebugUnitTest` (JVM only; real `org.json` is on the test classpath)
- Build / verify: `./gradlew assembleDebug`, release bundle: `./gradlew bundleRelease`
- `local.properties` holds the SDK path and must not be committed

## Architecture

- `data/Binance` — REST calls (OkHttp) to `data-api.binance.vision` + JSON parsers (unit-tested). No API key.
- `data/PriceSocket` — one OkHttp WebSocket to `data-stream.binance.vision/stream`, `SUBSCRIBE`s to streams,
  reconnects with backoff. Each screen owns one: opened in `onStart`, closed in `onStop` (no background service).
- `data/Coin`, `Candle`, `Format` (Turkish number formatting), `CoinFilter` (search + tabs)
- `MainActivity` + `CoinAdapter` (ListAdapter/DiffUtil, list re-rendered at most once per second)
- `CoinDetailActivity` + `chart/CandleChart` (MPAndroidChart, index-based X axis, live last candle)
- `alerts/` — `Store` (favorites + alerts in SharedPreferences), `PriceAlertWorker` (WorkManager, 15 min)

## Gotchas

- Release signing reads `keystore.properties` at the repo root (see `keystore.properties.example`); never commit keys.
  Without it `bundleRelease` produces an unsigned AAB.
- R8 + resource shrinking are on for release. The app uses no reflection/Serializable, so no custom keep rules;
  add some if you introduce reflection.
- MPAndroidChart comes from JitPack (declared in `settings.gradle`).
- Edge-to-edge is enforced (targetSdk ≥ 35): screens pad their root with `Ui.padForSystemBars`.
- UI strings and code comments are **Turkish**; keep strings in `res/values/strings.xml`.
- `Changelog.txt` at root is user-facing changelog; update it for notable changes.
