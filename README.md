# Coinvane

<img src="fastlane/metadata/android/en-US/images/icon.png" width="96" align="right" alt="Coinvane"/>

Read the market's direction. Coinvane, Binance'teki 700+ coinin canlı fiyatlarını kullanıcının kendi para biriminde
ve dilinde gösteren, gerçek grafikler, fiyat alarmları, portföy ve ana ekran widget'ı sunan Android uygulamasıdır.

## Özellikler
- **Canlı fiyatlar** — yalnızca ekrandaki coinler için WebSocket aboneliği (mobil veri dostu); liste saniyede bir yenilenir
- **25 para birimi** (USD, EUR, TRY, GBP, JPY, KRW, BRL, INR…) — ilk açılışta cihaz bölgesine göre seçilir
- **11 dil** — İngilizce, Türkçe, Almanca, Fransızca, İspanyolca, Portekizce (BR), Rusça, Arapça (RTL), Japonca, Korece, Çince;
  uygulama içinden ya da Android 13+ sistem ayarlarından seçilebilir
- **Grafik** — mum veya çizgi + hacim çubukları, 15dk / 1s / 4s / 1G / 1H, son mum canlı
- **Fiyat alarmları** — uygulama açıkken anında, kapalıyken ~15 dakikada bir (WorkManager)
- **Portföy** — adet + ortalama alış fiyatı → toplam bakiye, 24s değişim, kâr/zarar
- **Widget** — favori coinler (yoksa BTC, ETH, SOL, BNB)
- Favoriler, Yükselenler/Düşenler, arama, açık/koyu tema, Material You renkleri

## Veri kaynakları (ücretsiz, API anahtarı gerekmez)
| Amaç | Uç nokta |
| --- | --- |
| 24s özet, mumlar | `https://data-api.binance.vision/api/v3/` (USDT pariteleri) |
| Canlı akış | `wss://data-stream.binance.vision/stream` |
| Döviz kurları (USD bazlı, günlük) | `https://open.er-api.com/v6/latest/USD` — kaynak gösterimi Ayarlar'da |
| Coin logoları | CoinCap CDN, yedek olarak spothq/cryptocurrency-icons (jsDelivr) |

## Geliştirme
Gereksinimler: JDK 17+, Android SDK (platform 37).

```bash
./gradlew testDebugUnitTest   # birim testleri
./gradlew lintDebug           # lint
./gradlew assembleDebug       # debug APK
./gradlew bundleRelease       # Play Store için AAB
```
GitHub Actions (`.github/workflows/android.yml`) her push/PR'da test, lint ve derlemeyi çalıştırır.

### Release imzalama
`keystore.properties.example` dosyasını `keystore.properties` olarak kopyalayıp doldurun. Bu dosya ve `*.jks`
git'e girmez. Dosya yoksa `bundleRelease` imzasız AAB üretir.

### Crashlytics
Firebase projesinden indirilen `google-services.json` dosyasını `app/` altına koyun; Crashlytics kendiliğinden
devreye girer. Dosya yoksa Firebase hiç derlemeye girmez. (`google-services.json` git'e girmez.)

### Mağaza
- Play Store metinleri 11 dilde `fastlane/metadata/android/` altında (fastlane `supply` formatı)
- Logo ve mağaza görsellerinin kaynakları `design/` altında (SVG)
