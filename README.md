# Kripto Para

Binance'teki tüm TRY paritelerini (300+) canlı izleyen, gerçek mum grafikleri ve fiyat alarmları sunan Android uygulaması.

## Özellikler
- **Canlı fiyatlar** — Binance WebSocket akışı, liste saniyede bir güncellenir; fiyat değişimi renkle vurgulanır
- **Sekmeler** — Hacim · Favoriler · Yükselenler · Düşenler, isim/sembol araması
- **Mum grafiği** — gerçek OHLC verisi, 15dk / 1s / 4s / 1G / 1H aralıkları, son mum canlı güncellenir
- **Fiyat alarmları** — uygulama kapalıyken de çalışır (WorkManager, ~15 dk)
- **Favoriler**, açık/koyu tema, Android 17 (API 37) hedefi, edge-to-edge

## Veri kaynakları (ücretsiz, API anahtarı gerekmez)
| Amaç | Uç nokta |
| --- | --- |
| 24s özet, mumlar, fiyat | `https://data-api.binance.vision/api/v3/` |
| Canlı akış | `wss://data-stream.binance.vision/stream` |
| Coin logoları | CoinCap CDN, yedek olarak spothq/cryptocurrency-icons (jsDelivr) |

## Geliştirme
Gereksinimler: JDK 17+, Android SDK (platform 37).

```bash
./gradlew testDebugUnitTest   # birim testleri
./gradlew assembleDebug       # debug APK
./gradlew bundleRelease       # Play Store için AAB
```

### Release imzalama
`keystore.properties.example` dosyasını `keystore.properties` olarak kopyalayıp doldurun. Bu dosya ve `*.jks`
git'e girmez. Dosya yoksa `bundleRelease` imzasız AAB üretir.
