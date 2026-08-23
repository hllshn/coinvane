package com.abraxel.cryptocurrency;

import static android.content.ContentValues.TAG;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import androidx.cardview.widget.CardView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;

import com.abraxel.cryptocurrency.adapter.CurrencyAdapter;
import com.abraxel.cryptocurrency.constants.Constants;
import com.abraxel.cryptocurrency.model.CryptoCurrencies;
import com.abraxel.cryptocurrency.websocket.WebSocketService;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class MainActivity extends AppCompatActivity implements WebSocketService.WebSocketCallback {
    Logger logger = Logger.getLogger(MainActivity.class.getName());

    private RequestQueue requestQueue;
    private CurrencyAdapter currencyAdapter;
    public ProgressBar progressBar;
    private CardView loadingContainer;
    private TextView errorMessage;
    private List<CryptoCurrencies> cryptoCurrenciesList;
    private MethodServer methodServer;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    
    // WebSocket ile ilgili değişkenler
    private WebSocketService webSocketService;
    private boolean webSocketBound = false;
    private View connectionIndicator;
    private TextView connectionStatusText;
    private Button connectionActionButton;
    private CardView webSocketStatusCard;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Toolbar'ı ayarla
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        // İlk kurulumları yap
        initializeVariables();
        
        // Bildirim kanalını oluştur
        createNotificationChannel();

        // Android 13+ için bildirim izni (ön plan servisi bildirimi ve hatırlatıcılar için gerekli)
        requestNotificationPermissionIfNeeded();
        
        // RecyclerView'ı kur
        setupRecyclerView();
        
        // SwipeRefreshLayout'ı kur
        setupSwipeRefreshLayout();
        
        // WebSocket durum görünümünü kur
        setupWebSocketStatusView();
        
        // WebSocket servisini başlat
        startWebSocketService();
        
        // Açılışta güncel fiyatları API'den çek
        fetchCryptoData();
    }
    
    /**
     * Değişkenleri ve temel bileşenleri başlatır
     */
    private void initializeVariables() {
        Context context = getApplicationContext();
        methodServer = new MethodServer(context);

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progress_circular);
        loadingContainer = findViewById(R.id.loading_container);
        errorMessage = findViewById(R.id.error_message);
        recyclerView = findViewById(R.id.recycleView);
        
        // Uygulama genelinde paylaşılan tek RequestQueue
        requestQueue = VolleySingleton.getInstance(context).getRequestQueue();

        // Veri listesini başlat - tüm kripto paraların statik listesi
        cryptoCurrenciesList = CryptoCurrencies.createStaticCryptoList();

        // Adapter'ı oluştur ve statik liste ile doldur
        currencyAdapter = new CurrencyAdapter(cryptoCurrenciesList, getApplicationContext());
        
        // Tıklama dinleyicisini ayarla
        currencyAdapter.setOnItemClickListener(cryptoCurrency -> {
            // Tıklanan kripto para için detay ekranını aç
            openDetailPage(cryptoCurrency);
        });
    }
    
    /**
     * RecyclerView'ı kurar
     */
    private void setupRecyclerView() {
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(
                recyclerView.getContext(), 
                linearLayoutManager.getOrientation());
                
        recyclerView.addItemDecoration(dividerItemDecoration);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.setAdapter(currencyAdapter);
    }
    
    /**
     * WebSocket durum görünümünü başlangıç durumuna getirir
     */
    private void setupWebSocketStatusView() {
        // WebSocket durum göstergesi bileşenlerini tanımla
        connectionIndicator = findViewById(R.id.connection_indicator);
        connectionStatusText = findViewById(R.id.connection_status_text);
        connectionActionButton = findViewById(R.id.connection_action_button);
        webSocketStatusCard = findViewById(R.id.websocket_status_card);
        
        if (connectionIndicator == null || connectionStatusText == null || 
            connectionActionButton == null || webSocketStatusCard == null) {
            Log.e("MainActivity", "WebSocket durum bileşenleri bulunamadı");
            return;
        }
        
        // Bağlantı durumu başlangıçta "Bağlanıyor" olarak ayarla
        connectionStatusText.setText(R.string.websocket_connecting);
        connectionIndicator.setBackgroundResource(R.drawable.status_indicator_connecting);
        
        // Yeniden bağlanma butonu tıklaması
        connectionActionButton.setOnClickListener(v -> {
            connectionStatusText.setText(R.string.websocket_connecting);
            connectionIndicator.setBackgroundResource(R.drawable.status_indicator_connecting);
            
            if (webSocketBound && webSocketService != null) {
                // WebSocket bağlantısını yeniden kur
                webSocketService.connectWebSocket();
            } else {
                // WebSocket servisi bağlı değilse, yeniden başlat
                startWebSocketService();
            }
        });
        
        // Başlangıçta yeniden bağlanma butonunu göster
        connectionActionButton.setVisibility(View.VISIBLE);
    }
    
    /**
     * SwipeRefreshLayout'ı kurar
     */
    private void setupSwipeRefreshLayout() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // WebSocket bağlantısını kontrol et
            if (webSocketService != null) {
                boolean isConnected = webSocketService.isConnected();
                Log.d("MainActivity", "WebSocket bağlantı durumu: " + isConnected);
                
                if (!isConnected) {
                    // WebSocket bağlantısı yoksa yeniden bağlan
                    Log.d("MainActivity", "WebSocket bağlantısı yok, yeniden bağlanılıyor...");
                    webSocketService.connectWebSocket();
                } else {
                    // WebSocket bağlantısı varsa, manuel olarak veri iste
                    Log.d("MainActivity", "WebSocket bağlantısı var, manuel veri isteği gönderiliyor...");
                    // WebSocket üzerinden manuel veri isteği gönder
                    webSocketService.sendManualDataRequest();
                }
            }
            
            // API'den verileri yenile
            fetchCryptoData();
        });
    }

    // Yükleme göstergesini göster/gizle
    private void showLoading(boolean show) {
        if (show) {
            loadingContainer.setVisibility(View.VISIBLE);
        } else {
            loadingContainer.setVisibility(View.GONE);
        }
    }

    // Hata mesajını göster
    private void showError(String message) {
        errorMessage.setText(message);
        errorMessage.setVisibility(View.VISIBLE);
        
        // Hata mesajını 5 saniye sonra gizle
        new Handler().postDelayed(() -> {
            // Yumuşak bir geçişle kaybolması için animasyon ekleyelim
            AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
            fadeOut.setDuration(500);
            fadeOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    errorMessage.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            errorMessage.startAnimation(fadeOut);
        }, 5000);
    }
    
    /**
     * WebSocket Service'i başlatır ve bağlar
     */
    private void startWebSocketService() {
        Intent intent = new Intent(this, WebSocketService.class);
        startService(intent);
        
        // Servis zaten çalışıyor olabilir, önce bağlanmayı deneyelim
        if (!bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)) {
            Toast.makeText(this, "WebSocket servisine bağlanılamadı", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * WebSocket bağlantı durumuna göre UI'ı günceller
     */
    private void updateConnectionStatusUI(boolean connected) {
        if (connectionIndicator == null || connectionStatusText == null || 
            connectionActionButton == null || webSocketStatusCard == null) {
            Log.e("MainActivity", "WebSocket durum bileşenleri başlatılmamış");
            return;
        }
        
        if (connected) {
            // Bağlantı varsa
            connectionIndicator.setBackgroundResource(R.drawable.status_indicator_connected);
            connectionStatusText.setText(R.string.websocket_connected);
            connectionActionButton.setVisibility(View.GONE);
            webSocketStatusCard.setCardBackgroundColor(getResources().getColor(R.color.colorConnectionOk, null));
        } else {
            // Bağlantı yoksa
            connectionIndicator.setBackgroundResource(R.drawable.status_indicator_disconnected);
            connectionStatusText.setText(R.string.websocket_disconnected);
            connectionActionButton.setVisibility(View.VISIBLE);
            webSocketStatusCard.setCardBackgroundColor(getResources().getColor(R.color.colorConnectionError, null));
        }
    }

    /**
     * WebSocket Service ile bağlantı için ServiceConnection
     */
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            WebSocketService.LocalBinder binder = (WebSocketService.LocalBinder) service;
            webSocketService = binder.getService();
            webSocketBound = true;
            
            // WebSocket callback'i ayarla
            webSocketService.setCallback(MainActivity.this);
            
            // Eğer WebSocket zaten bağlıysa UI'ı güncelle
            if (webSocketService.isConnected()) {
                updateConnectionStatusUI(true);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            webSocketBound = false;
            webSocketService = null;
            updateConnectionStatusUI(false);
        }
    };
    
    /**
     * WebSocketCallback bağlantı durum değişikliği metodu
     */
    @Override
    public void onConnectionStateChanged(boolean connected) {
        runOnUiThread(() -> updateConnectionStatusUI(connected));
    }
    
    /**
     * Seçilen kripto para için detay sayfasını açar ve gerçek zamanlı veri aboneliği oluşturur
     */
    private void openDetailPage(CryptoCurrencies cryptoCurrency) {
        if (cryptoCurrency == null) {
            Log.e(TAG, "Kripto para verisi boş, detay sayfası açılamıyor.");
            return;
        }
        
        // İlk olarak WebSocket bağlantısını kontrol et
        if (webSocketService == null || !webSocketService.isConnected()) {
            Log.d(TAG, "WebSocket bağlantısı yok, bağlanmaya çalışılıyor...");
            // Bağlantı yoksa yeniden bağlan
            if (webSocketService != null) {
                webSocketService.connectWebSocket();
            }
            // Kullanıcıya bilgi ver
            Toast.makeText(this, "Gerçek zamanlı veri akışı için bağlanılıyor...", Toast.LENGTH_SHORT).show();
        }
        
        Intent intent = new Intent(this, ChartDataActivity.class);
        intent.putExtra("crypto_data", cryptoCurrency);
        intent.putExtra(Constants.COIN_NAME, cryptoCurrency.getCoinName().toLowerCase());
        Log.d(TAG, "Detay sayfası açılıyor: " + cryptoCurrency.getPair() + 
              ", Fiyat: " + cryptoCurrency.getFormattedPrice());
        
        // WebSocket üzerinden anlık veri talebinde bulun
        if (webSocketService != null) {
            // Bağlı olsun veya olmasın, abonelik işlemini başlat
            boolean requested = webSocketService.requestSymbolData(cryptoCurrency.getPair());
            Log.d(TAG, "WebSocket veri talebi gönderildi: " + requested);
            
            if (!requested) {
                // Abonelik isteği gönderilemediyse, statik verilerle devam et
                // ve 3 saniye sonra yeniden dene
                new Handler().postDelayed(() -> {
                    if (webSocketService != null && webSocketService.isConnected()) {
                        webSocketService.requestSymbolData(cryptoCurrency.getPair());
                        Log.d(TAG, "WebSocket veri talebi tekrar gönderildi");
                    }
                }, 3000);
            }
        }
        
        startActivity(intent);
    }
    
    /**
     * WebSocketCallback veri alındığında çağrılan metodu
     */
    @Override
    public void onDataReceived(List<CryptoCurrencies> data) {
        if (data == null || data.isEmpty()) {
            if (swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }
        
        runOnUiThread(() -> {
            // Her gelen veri için mevcut listedeki eşleşen kripto para güncellenir
            // veya listede yoksa yeni eklenir
            boolean hasChanges = false;
            
            for (CryptoCurrencies newCrypto : data) {
                boolean found = false;
                
                // Eşleşen kripto para var mı diye kontrol et
                for (int i = 0; i < cryptoCurrenciesList.size(); i++) {
                    CryptoCurrencies existingCrypto = cryptoCurrenciesList.get(i);
                    
                    // Aynı para birimi ise güncelle
                    if (existingCrypto.getPair() != null && 
                        existingCrypto.getPair().equals(newCrypto.getPair())) {
                        
                        // Eski kripto parayı yenisiyle değiştir
                        cryptoCurrenciesList.set(i, newCrypto);
                        found = true;
                        hasChanges = true;
                        break;
                    }
                }
                
                // Listede yoksa ekle
                if (!found) {
                    cryptoCurrenciesList.add(newCrypto);
                    hasChanges = true;
                }
            }
            
            // Eğer veri değişmişse adapter'ı güncelle
            if (hasChanges) {
                currencyAdapter.updateData(cryptoCurrenciesList);
            }
            
            // Yenileme işareti varsa kapat
            if (swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }
    
    /**
     * WebSocketCallback hata durumunda çağrılan metodu
     */
    @Override
    public void onError(String message) {
        runOnUiThread(() -> showError(message));
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.search_menu, menu);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setQueryHint(Constants.SEARCH_HINT);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currencyAdapter.getFilter().filter(newText);
                return false;
            }
        });
        return true;
    }

    public List<CryptoCurrencies> callRest() {
        showLoading(true);
        final List<CryptoCurrencies> ccList = new ArrayList<>();

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.GET,
                Constants.API_URL,
                null,
                response -> {
                    try {
                        JSONArray data = response.getJSONArray("data");
                        methodServer.cryptoSetter(data, ccList);
                    } catch (JSONException e) {
                        logger.warning(e.getMessage());
                        showError(Constants.ERROR_DATA_PROCESSING + e.getMessage());
                        showLoading(false);
                        return;
                    }
                    currencyAdapter.updateData(ccList);
                    showLoading(false);
                },
                error -> {
                    showError(Constants.ERROR_DATA_LOADING);
                    showLoading(false);
                });
        jsonObjectRequest.setTag(Constants.REQUEST_TAG);
        requestQueue.add(jsonObjectRequest);
        return ccList;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (requestQueue != null) {
            requestQueue.cancelAll(Constants.REQUEST_TAG);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Servis bağlantısını kapat
        if (webSocketBound) {
            unbindService(serviceConnection);
            webSocketBound = false;
        }
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_ID,
                    Constants.NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(Constants.NOTIFICATION_CHANNEL_DESC);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // Android 13+ (API 33) üzerinde bildirim göstermek için çalışma zamanı izni
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1001;

    private void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 /* TIRAMISU */) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    // API'den kripto para verilerini çekme
    private void fetchCryptoData() {
        showLoading(true);
        
        final List<CryptoCurrencies> newList = new ArrayList<>();
        
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.GET,
                Constants.API_URL,
                null,
                response -> {
                    try {
                        JSONArray data = response.getJSONArray("data");
                        methodServer.cryptoSetter(data, newList);
                        
                        runOnUiThread(() -> {
                            cryptoCurrenciesList.clear();
                            cryptoCurrenciesList.addAll(newList);
                            
                            currencyAdapter.updateData(cryptoCurrenciesList);
                            showLoading(false);
                            swipeRefreshLayout.setRefreshing(false);
                        });
                    } catch (JSONException e) {
                        logger.warning(e.getMessage());
                        runOnUiThread(() -> {
                            showError(Constants.ERROR_DATA_PROCESSING + e.getMessage());
                            showLoading(false);
                            swipeRefreshLayout.setRefreshing(false);
                        });
                    }
                },
                error -> {
                    runOnUiThread(() -> {
                        showError(Constants.ERROR_DATA_LOADING);
                        showLoading(false);
                        swipeRefreshLayout.setRefreshing(false);
                    });
                });
        jsonObjectRequest.setTag(Constants.REQUEST_TAG);
        requestQueue.add(jsonObjectRequest);
    }
}

