package com.abraxel.cryptocurrency.chart;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.widget.TextView;

import com.abraxel.cryptocurrency.R;
import com.abraxel.cryptocurrency.data.Format;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Seçilen mumun zamanını ve OHLC değerlerini gösterir; grafik kenarından taşmaz. */
@SuppressLint("ViewConstructor")
final class CandleMarkerView extends MarkerView {
    private static final float MARGIN = 8f;

    private final TextView time;
    private final TextView ohlc;
    private final List<Long> times;
    private final SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    CandleMarkerView(Context context, List<Long> times) {
        super(context, R.layout.marker_view);
        this.times = times;
        time = findViewById(R.id.marker_time);
        ohlc = findViewById(R.id.marker_ohlc);
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        if (e instanceof CandleEntry) {
            CandleEntry c = (CandleEntry) e;
            int i = Math.round(c.getX());
            time.setText(i >= 0 && i < times.size() ? format.format(new Date(times.get(i))) : "");
            ohlc.setText(getContext().getString(R.string.marker_ohlc,
                    Format.number(c.getOpen()), Format.number(c.getHigh()),
                    Format.number(c.getLow()), Format.number(c.getClose())));
        }
        super.refreshContent(e, highlight);
    }

    @Override
    public void draw(Canvas canvas, float posX, float posY) {
        View chart = getChartView();
        if (chart == null) {
            super.draw(canvas, posX, posY);
            return;
        }
        // Dokunulan noktanın karşı tarafında, sol/sağ üst köşede göster
        float x = posX > chart.getWidth() / 2f ? MARGIN : chart.getWidth() - getWidth() - MARGIN;
        canvas.translate(x, MARGIN);
        draw(canvas);
        canvas.translate(-x, -MARGIN);
    }
}
