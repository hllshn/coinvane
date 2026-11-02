package com.abraxel.cryptocurrency.data;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Binance birleşik akışına (combined stream) tek bağlantı. Abone olunan akışlar çalışırken değiştirilebilir
 * ({@link #setStreams}): yalnızca fark SUBSCRIBE/UNSUBSCRIBE edilir. Ekran görünürken açılır (onStart),
 * görünmezken kapatılır (onStop); bağlantı koparsa artan beklemeyle (1 sn → 30 sn) yeniden bağlanır.
 * Tüm geri çağrılar ana thread'dedir.
 */
public final class PriceSocket {
    public interface Listener {
        void onMessage(String stream, JSONObject data);

        void onStatusChanged(boolean live);
    }

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Set<String> streams = new LinkedHashSet<>();
    private Set<String> subscribed = new HashSet<>(); // sunucuya bildirilmiş akışlar
    private WebSocket socket;
    private boolean open; // bağlantı kuruldu ve hazır
    private boolean closed = true;
    private int attempts;
    private int requestId;

    public PriceSocket(Listener listener) {
        this.listener = listener;
    }

    public void open() {
        if (!closed) return;
        closed = false;
        connect();
    }

    public void close() {
        closed = true;
        open = false;
        main.removeCallbacksAndMessages(null);
        if (socket != null) {
            socket.close(1000, null);
            socket = null;
        }
    }

    /** İstenen akış kümesini ayarlar (en fazla 1024). Bağlıysa yalnızca fark gönderilir. */
    public void setStreams(Collection<String> wanted) {
        streams = new LinkedHashSet<>(wanted);
        if (open) syncSubscriptions();
        else if (!closed && socket == null) connect();
    }

    private void syncSubscriptions() {
        Set<String> add = new LinkedHashSet<>(streams);
        add.removeAll(subscribed);
        Set<String> remove = new HashSet<>(subscribed);
        remove.removeAll(streams);
        if (!remove.isEmpty()) send("UNSUBSCRIBE", remove);
        if (!add.isEmpty()) send("SUBSCRIBE", add);
        subscribed = new HashSet<>(streams);
    }

    private void send(String method, Set<String> params) {
        socket.send("{\"method\":\"" + method + "\",\"id\":" + (++requestId) + ",\"params\":" + new JSONArray(params) + "}");
    }

    private void connect() {
        if (closed || streams.isEmpty()) return;
        socket = Binance.client().newWebSocket(new Request.Builder().url(Binance.WS).build(), new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {
                main.post(() -> {
                    if (ws != socket) return;
                    attempts = 0;
                    open = true;
                    subscribed = new HashSet<>();
                    syncSubscriptions();
                    listener.onStatusChanged(true);
                });
            }

            @Override
            public void onMessage(@NonNull WebSocket ws, @NonNull String text) {
                JSONObject message;
                try {
                    message = new JSONObject(text);
                } catch (JSONException e) {
                    return;
                }
                String stream = message.optString("stream");
                JSONObject data = message.optJSONObject("data");
                if (stream.isEmpty() || data == null) return; // abonelik onayı vb.
                main.post(() -> {
                    if (ws == socket) listener.onMessage(stream, data);
                });
            }

            @Override
            public void onClosing(@NonNull WebSocket ws, int code, @NonNull String reason) {
                ws.close(1000, null); // Binance 24 saatte bir bağlantıyı kapatır
                main.post(() -> retry(ws));
            }

            @Override
            public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t, @Nullable Response response) {
                main.post(() -> retry(ws));
            }
        });
    }

    private void retry(WebSocket failed) {
        if (closed || failed != socket) return;
        socket = null;
        open = false;
        listener.onStatusChanged(false);
        long delay = Math.min(30_000L, 1000L << Math.min(attempts++, 5));
        main.postDelayed(this::connect, delay);
    }
}
