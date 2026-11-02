package com.abraxel.cryptocurrency.data;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Binance birleşik akışına (combined stream) bağlanır ve verilen akışlara abone olur.
 * Ekran görünürken açılır (onStart), görünmezken kapatılır (onStop); bağlantı koparsa
 * artan beklemeyle (1 sn → 30 sn) yeniden bağlanır. Tüm geri çağrılar ana thread'dedir.
 */
public final class PriceSocket {
    public interface Listener {
        void onMessage(String stream, JSONObject data);

        void onStatusChanged(boolean live);
    }

    private final List<String> streams;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebSocket socket;
    private boolean closed = true;
    private int attempts;

    public PriceSocket(List<String> streams, Listener listener) {
        this.streams = streams;
        this.listener = listener;
    }

    public void open() {
        if (!closed) return;
        closed = false;
        connect();
    }

    public void close() {
        closed = true;
        main.removeCallbacksAndMessages(null);
        if (socket != null) {
            socket.close(1000, null);
            socket = null;
        }
    }

    private void connect() {
        if (closed || streams.isEmpty()) return;
        socket = Binance.client().newWebSocket(new Request.Builder().url(Binance.WS).build(), new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {
                // Tek mesajla toplu abonelik (bağlantı başına en fazla 1024 akış)
                ws.send("{\"method\":\"SUBSCRIBE\",\"id\":1,\"params\":" + new JSONArray(streams) + "}");
                main.post(() -> {
                    if (ws != socket) return;
                    attempts = 0;
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
        listener.onStatusChanged(false);
        long delay = Math.min(30_000L, 1000L << Math.min(attempts++, 5));
        main.postDelayed(this::connect, delay);
    }
}
