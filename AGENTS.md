# AGENTS.md

## Overview

Single-module Android app (`:app` only, see `settings.gradle`): live BTC/ETH/crypto prices.
Plain **Java** (no Kotlin), View XML + ViewBinding (no Compose). Package: `com.abraxel.cryptocurrency`.

Toolchain: Gradle 8.9 wrapper, AGP 8.7.1 (Groovy DSL), compileSdk/targetSdk 35, minSdk 21, Lombok annotation processor.

## Commands

- Build / verify: `./gradlew assembleDebug` (there are **no unit or instrumented tests** in the repo)
- Clean: `./gradlew clean`
- Requires Android SDK; `local.properties` holds the SDK path and must not be committed

## Architecture

- `MainActivity` — entry point; fetches ticker data via Volley from BTCTurk API and binds to the WebSocket service
- `websocket/WebSocketService` — bound foreground Service holding an OkHttp WebSocket to OKX (`wss://ws.okx.com:8443/ws/v5/public`) with ping/reconnect logic; callbacks delivered to `MainActivity`
- `MethodServer.java` — despite the name, it's a plain parser class that maps BTCTurk JSON into models; it extends `MainActivity` only to reuse `getImageResourceId`
- Endpoints/keys live in `constants/Constants.java` (BTCTurk REST URL, OKX WS URL, Firebase RTDB URL)

## Gotchas

- **Release builds depend on a machine-specific keystore**: `signingConfigs.release/debug` point to `/Users/xelil/project-envs/myAndroidKey.jks` (hardcoded path in `app/build.gradle`). Release/assembleRelease fails on machines without it; debug signing also uses it.
- R8/minify is on for release; `app/proguard-rules.pro` has explicit `-keep` rules for `MainActivity`, `MethodServer`, `CurrencyAdapter`, `CryptoCurrencies`. If you add classes whose names/members must survive obfuscation (e.g. new models or reflective access), add matching keep rules.
- Third-party libs come from JitPack (`MPAndroidChart`, `Stepper-Touch`) — repos are declared in root `build.gradle`'s `allprojects` block, not `dependencyResolutionManagement`.
- UI strings and code comments are largely **Turkish**; follow existing style.
- `Changelog.txt` at root is user-facing changelog; update it for notable changes.
