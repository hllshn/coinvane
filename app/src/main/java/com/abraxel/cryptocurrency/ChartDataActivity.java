package com.abraxel.cryptocurrency;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.IBinder;
import android.os.Handler;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import com.abraxel.cryptocurrency.constants.Constants;
import com.abraxel.cryptocurrency.formatter.CandleMarkerView;
import com.abraxel.cryptocurrency.formatter.LineChartXAxisFormatter;
import com.abraxel.cryptocurrency.model.ChartData;
import com.abraxel.cryptocurrency.model.CryptoCurrencies;
import com.abraxel.cryptocurrency.websocket.WebSocketService;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.DefaultRetryPolicy;
import com.github.mikephil.charting.charts.CandleStickChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ChartDataActivity extends AppCompatActivity implements WebSocketService.WebSocketCallback {
    private final Logger logger = Logger.getLogger(ChartDataActivity.class.getName());

    private CandleStickChart candleStickChart;
    private final List<CandleEntry> candleEntries = new ArrayList<>();
    protected static List<ChartData> chartDataList = new ArrayList<>();
    
    // WebSocket ile ilgili değişkenler
    private WebSocketService webSocketService;
    private boolean webSocketBound = false;
    private CryptoCurrencies cryptoData;
    private String currentPair;
    
    // Detay sayfası için TextView'lar
    private TextView coinNameText, priceText, highLowText, volumeText, percentText;

    // Son mum verisi
    private CandleEntry lastCandle;
    private long lastCandleTime = 0;
    private static final long CANDLE_INTERVAL = 60 * 1000; // 1 dakikalık mum

    // X ekseni formatlayıcısı (mum zaman damgaları listesiyle çalışır)
    private LineChartXAxisFormatter xAxisFormatter;

    // Her mumun epoch ms zaman damgası (index bazlı grafik için)
    private final List<Long> candleTimes = new ArrayList<>();

    // Sahte (simüle) verinin her tick'te yeniden üretilmesini önler
    private boolean fallbackUsed = false;
    
    private static final long UI_UPDATE_THROTTLE = 500; // 500ms
    private long lastUiUpdateTime = 0;
    private static final long CHART_UPDATE_THROTTLE = 500; // Grafik güncellemeleri için throttle
    private long lastChartUpdateTime = 0;
    private float lastAppliedPrice = Float.NaN; // Değişmeyen fiyat tick'lerini atlamak için
    // İlk çizimden sonra tekrarlanan animasyonları önler
    private boolean chartDrawn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chart_data);
        
        // Kripto para verisini al
        cryptoData = (CryptoCurrencies) getIntent().getSerializableExtra("crypto_data");
        if (cryptoData != null) {
            currentPair = cryptoData.getPair();
        }
        
        // TextView referanslarını al
        coinNameText = findViewById(R.id.remind_coin_name);
        priceText = findViewById(R.id.price_text);
        highLowText = findViewById(R.id.high_low_text);
        volumeText = findViewById(R.id.volume_text);
        percentText = findViewById(R.id.percent_text);
        
        // Kripto para verilerini göster
        updateCryptoDetails();
        
        // WebSocket servisini bağla
        bindWebSocketService();
        
        // Grafik referansını al
        candleStickChart = findViewById(R.id.line_chart);
        setupCandleStickChart();
        
        // Tarihsel grafik verilerini API'den al
        getHistoricalCandleData();
        
        // ActionBar'ı güvenli bir şekilde ayarla
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                clearChartData();
                finish();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }
    
    /**
     * CandleStick grafiğinin genel özelliklerini ve yapılandırmasını ayarlar
     */
    private void setupCandleStickChart() {
        // Grafiğin genel görünüm ayarları
        candleStickChart.setBackgroundColor(Color.rgb(240, 240, 240)); // Açık gri arka plan
        candleStickChart.setDrawGridBackground(false);
        candleStickChart.setBorderColor(Color.rgb(200, 200, 200));
        
        // Dokunma hareketlerini etkinleştir
        candleStickChart.setTouchEnabled(true);
        candleStickChart.setDragEnabled(true);
        candleStickChart.setScaleEnabled(true);
        candleStickChart.setPinchZoom(true);

        // Kompakt görünüm için kenar boşluklarını küçült
        candleStickChart.setMinOffset(4f);
        candleStickChart.setViewPortOffsets(28f, 24f, 8f, 24f);
        
        // Vurgulama ayarları
        candleStickChart.setHighlightPerDragEnabled(true);
        candleStickChart.setHighlightPerTapEnabled(true);
        
        // Animasyon ekle
        candleStickChart.animateX(700);
        
        // Açıklama ayarla
        Description description = new Description();
        description.setText(cryptoData != null ? cryptoData.getCoinName() + " · 6s" : "Kripto Para Grafiği");
        description.setTextSize(10f);
        description.setTextColor(Color.rgb(120, 120, 120));
        description.setTextAlign(Paint.Align.RIGHT);
        candleStickChart.setDescription(description);
        
        // X ekseni ayarları
        XAxis xAxis = candleStickChart.getXAxis();
        xAxis.setValueFormatter(xAxisFormatter = new LineChartXAxisFormatter(candleTimes));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.rgb(90, 90, 90));
        xAxis.setTextSize(9f);
        xAxis.setLabelCount(4, true); // 4 etiket göster
        xAxis.setGranularity(1f); // Minimum çizgi aralığı
        xAxis.setDrawAxisLine(true);
        xAxis.setAxisLineColor(Color.rgb(160, 160, 160));
        
        // Sol Y ekseni ayarları
        YAxis leftAxis = candleStickChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.rgb(230, 230, 230));
        leftAxis.setDrawAxisLine(false);
        leftAxis.setTextColor(Color.rgb(90, 90, 90));
        leftAxis.setTextSize(9f);
        leftAxis.setLabelCount(5, true);
        leftAxis.setSpaceTop(8f); // Üst boşluk %8
        leftAxis.setSpaceBottom(8f); // Alt boşluk %8
        leftAxis.setDrawZeroLine(false);
        
        // Sağ Y ekseni gösterme
        candleStickChart.getAxisRight().setEnabled(false);
        
        // Legend ayarları - kompakt için gizli (açıklama metni yeterli)
        Legend legend = candleStickChart.getLegend();
        legend.setEnabled(false);
        
        // Boş durum için mesaj göster
        candleStickChart.setNoDataText("Kripto para verileri yükleniyor...");
        candleStickChart.setNoDataTextColor(Color.rgb(100, 100, 100));

        // Seçici çizgi stili ve OHLC işaretçisi
        candleStickChart.setMaxHighlightDistance(80f);
        candleStickChart.setMarker(new CandleMarkerView(this, R.layout.marker_view, candleTimes));
    }
    
    /**
     * Kripto para detaylarını güncelle
     */
    private void updateCryptoDetails() {
        if (cryptoData == null) {
            Log.d("ChartDataActivity", "updateCryptoDetails: cryptoData null");
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUiUpdateTime < UI_UPDATE_THROTTLE) {
            return; // Çok sık güncellemeyi engelle
        }
        lastUiUpdateTime = currentTime;

        runOnUiThread(() -> {
            try {
                String formattedPrice = cryptoData.getFormattedPrice();
                String formattedHigh = cryptoData.getHigh();
                String formattedLow = cryptoData.getLow();
                String formattedVolume = cryptoData.getFormattedVolume();
                
                Log.d("ChartDataActivity", "Arayüz güncelleniyor: " + cryptoData.getPair() + 
                      ", Fiyat: " + formattedPrice);
                
                // Para birimi TRY olduğundan emin ol
                String currency = " ₺";
                
                coinNameText.setText(cryptoData.getCoinName() + " (" + cryptoData.getPair() + ")");
                priceText.setText("Fiyat: " + formattedPrice + currency);
                highLowText.setText("En Yüksek: " + formattedHigh + currency + " / En Düşük: " + formattedLow + currency);
                volumeText.setText("Hacim: " + formattedVolume);
                
                String percentValue = cryptoData.getDailyPercent();
                
                // Pozitif/negatif değişime göre renklendirme
                if (percentValue != null && !percentValue.isEmpty()) {
                    boolean isPositive = !percentValue.contains("-");
                    
                    if (isPositive) {
                        percentText.setText("Değişim: +" + percentValue + "%");
                        percentText.setTextColor(Color.parseColor("#006400")); // Koyu yeşil
                    } else {
                        percentText.setText("Değişim: " + percentValue + "%");
                        percentText.setTextColor(Color.parseColor("#B71C1C")); // Koyu kırmızı
                    }
                } else {
                    percentText.setText("Değişim: 0.00%");
                    percentText.setTextColor(Color.parseColor("#616161")); // Gri
                }
            } catch (Exception e) {
                Log.e("ChartDataActivity", "UI güncelleme hatası: " + e.getMessage());
            }
        });
    }
    
    /**
     * WebSocket servisine bağlan
     */
    private void bindWebSocketService() {
        Intent intent = new Intent(this, WebSocketService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        Log.d("ChartDataActivity", "WebSocket servisine bağlanılıyor...");
    }
    
    /**
     * WebSocket Service bağlantısı
     */
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            WebSocketService.LocalBinder binder = (WebSocketService.LocalBinder) service;
            webSocketService = binder.getService();
            webSocketBound = true;
            
            Log.d("ChartDataActivity", "WebSocket servisine bağlandı");
            
            // WebSocket callback'i ayarla
            webSocketService.setCallback(ChartDataActivity.this);
            
            // Eğer mevcut kripto para bilgisi varsa, WebSocket üzerinden veri talep et
            if (cryptoData != null && cryptoData.getPair() != null) {
                Log.d("ChartDataActivity", "Veri talebi gönderiliyor: " + cryptoData.getPair());
                
                // Eğer WebSocket bağlantısı yoksa, bağlantı kur ve talep gönder
                if (!webSocketService.isConnected()) {
                    Log.d("ChartDataActivity", "WebSocket bağlı değil, bağlanılıyor...");
                    webSocketService.connectWebSocket();
                    
                    // 1 saniye sonra veri talep et (bağlantı kurulana kadar bekle)
                    new Handler().postDelayed(() -> {
                        if (webSocketService != null && webSocketService.isConnected()) {
                            Log.d("ChartDataActivity", "WebSocket bağlandı, veri talep ediliyor: " + cryptoData.getPair());
                            boolean success = webSocketService.requestSymbolData(cryptoData.getPair());
                            Log.d("ChartDataActivity", "Veri talebi sonucu: " + (success ? "Başarılı" : "Başarısız"));
                        } else {
                            Log.e("ChartDataActivity", "WebSocket hala bağlı değil, varsayılan veriler kullanılacak");
                        }
                    }, 1000);
                } else {
                    // WebSocket zaten bağlıysa hemen veri talep et
                    boolean success = webSocketService.requestSymbolData(cryptoData.getPair());
                    Log.d("ChartDataActivity", "Veri talebi sonucu: " + (success ? "Başarılı" : "Başarısız"));
                }
            } else {
                Log.e("ChartDataActivity", "Kripto para verisi bulunamadı!");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            webSocketBound = false;
            webSocketService = null;
            Log.d("ChartDataActivity", "WebSocket servisi bağlantısı kesildi");
        }
    };
    
    @Override
    protected void onDestroy() {
        // WebSocket servis bağlantısını kapat
        if (webSocketBound) {
            // Mevcut kripto para için aboneliği iptal et
            if (webSocketService != null && currentPair != null) {
                webSocketService.unsubscribeFromSymbol(currentPair);
                Log.d("ChartDataActivity", "WebSocket aboneliği iptal edildi: " + currentPair);
            }
            
            // Callback'i kaldır
            if (webSocketService != null) {
                webSocketService.setCallback(null);
            }
            
            // Servisi unbind et
            unbindService(serviceConnection);
            webSocketBound = false;
        }
        
        super.onDestroy();
    }
    
    @Override
    public void onDataReceived(List<CryptoCurrencies> data) {
        if (data == null || data.isEmpty() || cryptoData == null) {
            Log.d("ChartDataActivity", "Veri boş veya cryptoData null");
            return;
        }
        
        // Gelen verilerden şu anki kripto para ile eşleşeni bul
        for (CryptoCurrencies newCrypto : data) {
            if (newCrypto.getPair() != null && newCrypto.getPair().equals(currentPair)) {
                Log.d("ChartDataActivity", "Eşleşen veri bulundu: " + newCrypto.getPair() + 
                      ", Fiyat: " + newCrypto.getFormattedPrice());
                
                // Kripto para verilerini güncelle
                cryptoData = newCrypto;
                
                // UI'ı güncelle
                updateCryptoDetails();
                
                // Websocket verisiyle grafiği güncelle
                updateCandleWithWebSocketData(newCrypto);
                
                break;
            }
        }
    }
    
    /**
     * WebSocket'ten gelen verileri mum grafiğe ekler
     * @param cryptoData Güncel kripto para verisi
     */
    private void updateCandleWithWebSocketData(CryptoCurrencies cryptoData) {
        // Grafik verisi thread-safe değil, tüm güncelleme UI thread'de yapılmalı
        runOnUiThread(() -> {
            try {
                // Çok sık gelen tick'leri kırp
                long now = System.currentTimeMillis();
                if (now - lastChartUpdateTime < CHART_UPDATE_THROTTLE) {
                    return;
                }
                lastChartUpdateTime = now;

                long currentTime = System.currentTimeMillis();
                float currentPrice = Float.parseFloat(cryptoData.getLast());

                // Tarihsel veriler henüz yüklenmediyse tick'i atla; sahte veri üretme
                if (candleStickChart.getData() == null || candleStickChart.getData().getDataSetCount() == 0) {
                    return;
                }

                CandleDataSet set = (CandleDataSet) candleStickChart.getData().getDataSetByIndex(0);
                if (set == null || set.getEntryCount() == 0) {
                    return;
                }

                // Mevcut mumun zamanını kontrol et
                long currentCandleStart = currentTime - (currentTime % CANDLE_INTERVAL);

                if (lastCandleTime == 0 || currentCandleStart > lastCandleTime) {
                    // Yeni mum başlat - index bazlı konum
                    int nextIndex = set.getEntryCount();
                    lastCandle = new CandleEntry(
                            nextIndex,
                            currentPrice,  // high
                            currentPrice,  // low
                            currentPrice,  // open
                            currentPrice); // close

                    set.addEntry(lastCandle);
                    candleTimes.add(currentCandleStart);
                    lastCandleTime = currentCandleStart;
                    lastAppliedPrice = currentPrice;

                    Log.d("ChartDataActivity", "Yeni mum eklendi: Zaman=" +
                          currentCandleStart + ", Fiyat=" + currentPrice);

                    // En son 50 mumu tut (performans için)
                    if (set.getEntryCount() > 50) {
                        set.removeEntryByXValue(set.getEntryForIndex(0).getX());
                        if (!candleTimes.isEmpty()) {
                            candleTimes.remove(0);
                        }
                    }
                } else {
                    // Fiyat değişmediyse yeniden çizmeye gerek yok
                    if (currentPrice == lastAppliedPrice) {
                        return;
                    }
                    // Mevcut mumu güncelle
                    float open = lastCandle.getOpen();
                    float high = Math.max(lastCandle.getHigh(), currentPrice);
                    float low = Math.min(lastCandle.getLow(), currentPrice);

                    // Son mumu kaldır ve yenisini ekle
                    set.removeEntry(lastCandle);
                    lastCandle = new CandleEntry(lastCandle.getX(), high, low, open, currentPrice);
                    set.addEntry(lastCandle);

                    Log.d("ChartDataActivity", "Mum güncellendi: Zaman=" +
                          lastCandleTime + ", Open=" + open + ", High=" + high +
                          ", Low=" + low + ", Close=" + currentPrice);
                }

                lastAppliedPrice = currentPrice;

                // Veri setini güncelle
                candleStickChart.getData().notifyDataChanged();
                candleStickChart.notifyDataSetChanged();
                candleStickChart.invalidate();
            } catch (Exception e) {
                Log.e("ChartDataActivity", "Grafik güncellenirken hata: " + e.getMessage(), e);
            }
        });
    }
    
    @Override
    public void onConnectionStateChanged(boolean connected) {
        // Bağlantı durumu değiştiğinde
        runOnUiThread(() -> {
            // Eğer bağlantı kurulduysa ve kripto para verisi varsa, veri talep et
            if (connected && webSocketService != null && cryptoData != null && cryptoData.getPair() != null) {
                Log.d("ChartDataActivity", "Bağlantı durumu değişti, veri talep ediliyor: " + cryptoData.getPair());
                boolean success = webSocketService.requestSymbolData(cryptoData.getPair());
                Log.d("ChartDataActivity", "Veri talebi sonucu: " + (success ? "Başarılı" : "Başarısız"));
            }
        });
    }
    
    @Override
    public void onError(String message) {
        // Hata durumunda loglama yap
        Log.e("ChartDataActivity", "WebSocket hatası: " + message);
    }

    /**
     * Tarihsel mum verilerini al. Önce 10 dakikalık in-memory cache'e bakar,
     * tazeyse ağı hiç meşgul etmeden grafiği çizer.
     */
    private static final long HISTORY_CACHE_TTL = 10 * 60 * 1000;
    private static final java.util.Map<String, Object[]> historyCache = new java.util.HashMap<>();
    private static final java.util.Map<String, Long> historyCacheTime = new java.util.HashMap<>();

    private void getHistoricalCandleData() {
        String coinName = getCoinName();
        String url = getHistoricalCandleUrl(coinName);
        if (url.isEmpty()) {
            Log.e("ChartDataActivity", "Geçersiz URL, tarihsel veri alınamıyor");
            return;
        }

        // Cache kontrolü
        Long cachedAt = historyCacheTime.get(url);
        if (cachedAt != null && System.currentTimeMillis() - cachedAt < HISTORY_CACHE_TTL
                && historyCache.containsKey(url)) {
            applyHistoryFromCache(url);
            return;
        }

        Log.d("ChartDataActivity", "Tarihsel veri URL: " + url);

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    try {
                        candleEntries.clear();
                        candleTimes.clear();
                        buildCandles(url, response.getJSONArray("prices"));
                    } catch (JSONException e) {
                        Log.e("ChartDataActivity", "Tarihsel veriler işlenirken hata: " + e.getMessage(), e);
                        createSimulatedCandles();
                    } catch (Exception e) {
                        Log.e("ChartDataActivity", "Beklenmeyen hata: " + e.getMessage(), e);
                        createSimulatedCandles();
                    }
                },
                error -> {
                    Log.e("ChartDataActivity", "Tarihsel veriler alınırken hata: " +
                          (error.getMessage() != null ? error.getMessage() : "Bilinmeyen hata"));

                    // Ağ hatasında eski cache varsa onu kullan
                    if (historyCache.containsKey(url)) {
                        Log.d("ChartDataActivity", "Ağ hatası, önbellekteki veriler kullanılıyor");
                        applyHistoryFromCache(url);
                        return;
                    }
                    // Hata durumunda simüle edilmiş veri oluştur
                    createSimulatedCandles();
                });

        // Timeout süresi artırılmış request
        req.setRetryPolicy(new DefaultRetryPolicy(
                15000, // 15 saniye timeout
                2, // 2 yeniden deneme
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        VolleySingleton.getInstance(getApplicationContext()).addToRequestQueue(req);
    }

    /** Cache'teki mumları bu aktivitenin alanlarına kopyalayıp çizer */
    @SuppressWarnings("unchecked")
    private void applyHistoryFromCache(String url) {
        Object[] cached = historyCache.get(url);
        if (cached == null) {
            return;
        }
        candleEntries.clear();
        candleEntries.addAll((List<CandleEntry>) cached[0]);
        candleTimes.clear();
        candleTimes.addAll((List<Long>) cached[1]);
        lastAppliedPrice = Float.NaN;
        drawCandleStickChart(candleEntries);
        Log.d("ChartDataActivity", "Önbellekten " + candleEntries.size() + " mum yüklendi");
    }

    /** CoinGecko yanıtından 6 saatlik mumları üretir, cache'e yazar ve grafiği çizer */
    private void buildCandles(String url, JSONArray pricesArray) {
        try {
            Log.d("ChartDataActivity", "CoinGecko API'den " + pricesArray.length() + " fiyat verisi alındı");

            if (pricesArray.length() == 0) {
                Log.e("ChartDataActivity", "CoinGecko API yanıtı boş veri dizisi içeriyor");
                createSimulatedCandles();
                return;
            }

            List<Float> prices = new ArrayList<>();
            List<Long> timestamps = new ArrayList<>();

            for (int i = 0; i < pricesArray.length(); i++) {
                JSONArray priceData = pricesArray.getJSONArray(i);
                long timestamp = priceData.getLong(0); // Zaman damgası (milisaniye)
                float price = (float) priceData.getDouble(1);

                timestamps.add(timestamp);
                prices.add(price);
            }

            // CoinGecko saatlik veri döndürür; 6 noktayı birleştirerek 6 saatlik mum üret
            int pointsPerCandle = 6;

            for (int i = 0; i < timestamps.size(); i += pointsPerCandle) {
                int endIndex = Math.min(i + pointsPerCandle, timestamps.size()) - 1;

                if (endIndex - i >= 3) { // En az 3 veri noktası olmalı
                    long openTime = timestamps.get(i);
                    float openPrice = prices.get(i);
                    float closePrice = prices.get(endIndex);

                    // Yüksek ve düşük fiyatları belirle
                    float highPrice = openPrice;
                    float lowPrice = openPrice;

                    for (int j = i; j <= endIndex; j++) {
                        float currentPrice = prices.get(j);
                        if (currentPrice > highPrice) highPrice = currentPrice;
                        if (currentPrice < lowPrice) lowPrice = currentPrice;
                    }

                    // Mum verisini oluştur - index bazlı konum, eşit aralıklı çizim garantilenir
                    CandleEntry entry = new CandleEntry(
                            candleEntries.size(),
                            highPrice,
                            lowPrice,
                            openPrice,
                            closePrice
                    );

                    candleEntries.add(entry);
                    candleTimes.add(openTime);
                }
            }

            Log.d("ChartDataActivity", candleEntries.size() + " adet mum oluşturuldu");

            // Eğer veri varsa grafiği çiz ve cache'e yaz
            if (!candleEntries.isEmpty()) {
                // Son mum zamanını güncelle (epoch ms)
                lastCandle = candleEntries.get(candleEntries.size() - 1);
                lastCandleTime = 0; // Sonraki tick yeni canlı mum başlatır
                lastAppliedPrice = Float.NaN;

                historyCache.put(url, new Object[]{new ArrayList<>(candleEntries), new ArrayList<>(candleTimes)});
                historyCacheTime.put(url, System.currentTimeMillis());

                drawCandleStickChart(candleEntries);
                Log.d("ChartDataActivity", "Mum grafiği çizildi");
            } else {
                Log.e("ChartDataActivity", "Mum verisi oluşturulamadı");
                createSimulatedCandles();
            }
        } catch (Exception e) {
            Log.e("ChartDataActivity", "Tarihsel veriler işlenirken hata: " + e.getMessage(), e);
            createSimulatedCandles();
        }
    }
    
    /**
     * CoinGecko API için tarihsel veri URL'i oluştur
     */
    private String getHistoricalCandleUrl(String coinName) {
        if (coinName == null || coinName.isEmpty()) {
            Log.e("ChartDataActivity", "Coin adı boş");
            return "";
        }
        
        String coinId = coinNameToCoinGeckoId(coinName.toLowerCase());
        
        // Son 7 gün için fiyat verileri (saatlik fiyatlar için vs_currency=try&days=7)
        String baseUrl = "https://api.coingecko.com/api/v3/coins/";
        String params = "/market_chart?vs_currency=try&days=7";
        
        String url = baseUrl + coinId + params;
        Log.d("ChartDataActivity", "CoinGecko API URL: " + url);
        return url;
    }

    /**
     * Coin adını CoinGecko API için kullanılan coin id'sine dönüştürür
     */
    private String coinNameToCoinGeckoId(String coinName) {
        // Boşlukları tire ile değiştirerek temel id üret (örn: "binance coin" -> "binance-coin")
        String coinId = coinName.toLowerCase().trim().replace(" ", "-");

        // CoinGecko'nun kullandığı özel ID'ler için eşleştirme
        switch (coinId) {
            case "btc": case "bitcoin": return "bitcoin";
            case "eth": case "ethereum": return "ethereum";
            case "usdt": return "tether";
            case "bnb": case "binance-coin": return "binancecoin";
            case "xrp": case "ripple": return "ripple";
            case "ada": case "cardano": return "cardano";
            case "sol": case "solana": return "solana";
            case "doge": case "dogecoin": return "dogecoin";
            case "dot": case "polkadot": return "polkadot";
            case "shib": return "shiba-inu";
            case "dai": return "dai";
            case "matic": case "polygon": return "matic-network";
            case "trx": case "tron": return "tron";
            case "avax": case "avalanche-2": return "avalanche-2";
            case "link": case "chainlink": return "chainlink";
            case "etc": return "ethereum-classic";
            case "ltc": case "litecoin": return "litecoin";
            case "near": return "near";
            case "bch": return "bitcoin-cash";
            case "usdc": case "usd-coin": return "usd-coin";
            case "xlm": case "stellar": return "stellar";
            case "atom": case "cosmos": return "cosmos";
            case "algo": case "algorand": return "algorand";
            case "uni": case "uniswap": return "uniswap";
            default: return coinId;
        }
    }
    
    /**
     * Simüle edilmiş mum verileri oluştur (API hatası durumunda)
     */
    private void createSimulatedCandles() {
        // Her WebSocket tick'inde rastgele veri yeniden üretilmesin
        if (fallbackUsed) {
            return;
        }
        fallbackUsed = true;

        if (cryptoData != null && cryptoData.getLast() != null) {
            try {
                candleEntries.clear();
                candleTimes.clear();
                float currentPrice = Float.parseFloat(cryptoData.getLast());
                long currentTime = System.currentTimeMillis();
                
                // Son fiyat bilgisinden başla ve geriye doğru gerçekçi mumlar oluştur
                float lastPrice = currentPrice;
                
                // Son 24 saat için (24 mum) - her saat için bir mum
                for (int i = 24; i >= 0; i--) {
                    // Mum zamanını hesapla (saat başları için)
                    long hourInMillis = 60 * 60 * 1000; // 1 saat
                    long candleTime = currentTime - (i * hourInMillis);
                    
                    // Fiyat sapması (bir önceki kapanıştan % -2 ile +2 arasında) - gerçekçi piyasa hareketi
                    float changePercent = (float) (Math.random() * 4 - 2) / 100;
                    float basePrice = lastPrice * (1 + changePercent);
                    
                    // Mumun bileşenlerini oluştur
                    float direction = Math.random() > 0.5 ? 1 : -1; // Yukarı/aşağı
                    float range = basePrice * 0.01f * (float) Math.random(); // Açılış-kapanış arası fark
                    
                    float open, close;
                    if (direction > 0) {
                        // Yukarı mum
                        open = basePrice - range / 2;
                        close = basePrice + range / 2;
                    } else {
                        // Aşağı mum
                        open = basePrice + range / 2;
                        close = basePrice - range / 2;
                    }
                    
                    // Fiyat aralıklarının gerçekçi olmasını sağla
                    float wickRange = range * (1 + (float) Math.random()); // Mum fitillerinin boyutu
                    float high = Math.max(open, close) + wickRange / 2;
                    float low = Math.min(open, close) - wickRange / 2;
                    
                    // Bir sonraki mum için son fiyatı güncelle
                    lastPrice = close;
                    
                    // Mum girdisini oluştur ve listeye ekle (index bazlı konum)
                    CandleEntry entry = new CandleEntry(candleEntries.size(), high, low, open, close);
                    candleEntries.add(entry);
                    candleTimes.add(candleTime);
                }
                
                // Son mum referansını güncelle
                if (!candleEntries.isEmpty()) {
                    lastCandle = candleEntries.get(candleEntries.size() - 1);
                    lastCandleTime = candleTimes.get(candleTimes.size() - 1);
                    
                    // Verileri görselleştir
                    drawCandleStickChart(candleEntries);
                    Log.d("ChartDataActivity", "Simüle edilmiş " + candleEntries.size() + " mum oluşturuldu ve çizildi");
                }
            } catch (Exception e) {
                Log.e("ChartDataActivity", "Simüle edilmiş mumlar oluşturulurken hata: " + e.getMessage(), e);
            }
        } else {
            Log.e("ChartDataActivity", "Simülasyon için kripto veri yok veya fiyat bilgisi eksik");
        }
    }
    
    /**
     * CandleStick grafiğini çiz
     */
    private void drawCandleStickChart(List<CandleEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            Log.e("ChartDataActivity", "Çizilecek mum verisi yok");
            return;
        }
        
        runOnUiThread(() -> {
            try {
                CandleDataSet dataSet = new CandleDataSet(entries, cryptoData != null ? 
                                                         cryptoData.getCoinName() + " Fiyat Mumları" : 
                                                         "Kripto Para Mumları");
                
                // Mum görünümünü ayarla
                dataSet.setColor(Color.rgb(80, 80, 80));
                dataSet.setShadowColor(Color.DKGRAY);
                dataSet.setShadowWidth(0.7f);
                dataSet.setDecreasingColor(Color.rgb(220, 50, 50)); // Kırmızı - düşüş
                dataSet.setDecreasingPaintStyle(Paint.Style.FILL);
                dataSet.setIncreasingColor(Color.rgb(50, 180, 50)); // Yeşil - yükseliş
                dataSet.setIncreasingPaintStyle(Paint.Style.FILL);
                dataSet.setNeutralColor(Color.rgb(50, 50, 150)); // Mavi - değişim yok
                dataSet.setDrawValues(false);
                dataSet.setHighlightEnabled(true);
                dataSet.setShadowWidth(0.8f);
                dataSet.setBarSpace(15f); // Düşük değer = daha kalın mum gövdesi
                
                // Veriyi grafiğe ayarla
                CandleData candleData = new CandleData(dataSet);
                candleStickChart.setData(candleData);
                
                // Özel ayarlar - index bazlı grafikte ekranda ~30 mum göster
                candleStickChart.setVisibleXRangeMaximum(30f);
                candleStickChart.moveViewToX(entries.get(entries.size() - 1).getX()); // En son muma odaklan
                
                // Grafiği güncelle
                candleStickChart.invalidate();
                
                // Kısa animasyon
                candleStickChart.animateX(400);
                
                Log.d("ChartDataActivity", "Mum grafiği çizildi: " + entries.size() + " mum");
            } catch (Exception e) {
                Log.e("ChartDataActivity", "Grafik çizilirken hata: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            clearChartData();
        }
        return super.onOptionsItemSelected(item);
    }

    @Nullable
    private String getCoinName() {
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            return null;
        }
        String coinName = extras.getString(Constants.COIN_NAME);
        if (coinName == null) {
            return null;
        }
        if (coinName.equals("avalanche")) {
            return coinName.concat("-2");
        } else if (coinName.equals("usd coin")) {
            String[] s = coinName.split(" ");
            return s[0].concat("-").concat(s[1]);
        }
        return coinName;
    }

    private String coinNameFormatter(String coinName) {
        coinName = coinName.replace("-", " ");
        return coinName.substring(0, 1).toUpperCase() + coinName.substring(1);
    }

    private float getFormattedVal(ChartData chartData) {
        DecimalFormat decimalFormat = new DecimalFormat("#.###");
        decimalFormat.setRoundingMode(RoundingMode.CEILING);
        return (float) chartData.getCost();
    }

    private void clearChartData() {
        candleEntries.clear();
        candleTimes.clear();
        chartDataList.clear();
        lastCandle = null;
        lastCandleTime = 0;

        if (candleStickChart != null) {
            candleStickChart.clear();
            candleStickChart.invalidate();
        }
    }
}