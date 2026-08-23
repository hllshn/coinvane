package com.abraxel.cryptocurrency.formatter;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.TextView;

import com.abraxel.cryptocurrency.R;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Seçici çizgi mumun üzerine geldiğinde OHLC değerlerini ve zamanı gösteren işaretçi.
 * Ekran kenarlarında kesilmemesi için konumu sınır içinde tutulur.
 */
public class CandleMarkerView extends MarkerView {
    private final TextView timeText;
    private final TextView ohlcText;
    private final List<Long> candleTimes;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());

    public CandleMarkerView(Context context, int layoutResource, List<Long> candleTimes) {
        super(context, layoutResource);
        this.candleTimes = candleTimes;
        timeText = findViewById(R.id.marker_time);
        ohlcText = findViewById(R.id.marker_ohlc);
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        if (e instanceof CandleEntry) {
            CandleEntry candle = (CandleEntry) e;

            String time = "";
            int index = Math.round(candle.getX());
            if (candleTimes != null && index >= 0 && index < candleTimes.size()) {
                time = timeFormat.format(new Date(candleTimes.get(index)));
            }
            timeText.setText(time);

            ohlcText.setText(String.format(Locale.US,
                    "K: %s  Y: %s  D: %s  A: %s",
                    formatPrice(candle.getClose()),
                    formatPrice(candle.getHigh()),
                    formatPrice(candle.getLow()),
                    formatPrice(candle.getOpen())));
        }
        super.refreshContent(e, highlight);
    }

    private String formatPrice(float price) {
        if (price >= 1000) {
            return String.format(Locale.US, "%,.0f", price);
        } else if (price >= 1) {
            return String.format(Locale.US, "%.2f", price);
        } else {
            return String.format(Locale.US, "%.4f", price);
        }
    }

    @Override
    public void draw(Canvas canvas, float posX, float posY) {
        android.view.View chart = getChartView();
        if (chart == null) {
            super.draw(canvas, posX, posY);
            return;
        }

        float width = getWidth();
        float height = getHeight();
        float margin = 8f;

        // Mumun üstünde ortalanmış konum
        float x = posX - width / 2f;
        float y = posY - height - 12f;

        // Yatay sınır: sağ/sol kenardan taşmasın
        if (x < margin) {
            x = margin;
        } else if (x + width > chart.getWidth() - margin) {
            x = chart.getWidth() - width - margin;
        }

        // Dikey sınır: üstte sığmazsa mumun altına al
        if (y < margin) {
            y = posY + 12f;
        }
        if (y + height > chart.getHeight() - margin) {
            y = chart.getHeight() - height - margin;
        }

        canvas.translate(x, y);
        draw(canvas);
        canvas.translate(-x, -y);
    }
}
