package com.abraxel.cryptocurrency.chart;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.widget.TextView;

import com.abraxel.cryptocurrency.R;
import com.abraxel.cryptocurrency.data.Money;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Seçilen mumun zamanını ve OHLC (çizgi modunda kapanış) değerini gösterir; grafik kenarından taşmaz. */
@SuppressLint("ViewConstructor")
final class CandleMarkerView extends MarkerView {
    private static final float MARGIN = 8f;

    private final TextView time;
    private final TextView values;
    private final List<Long> times;
    private final DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
    private Money money;

    CandleMarkerView(Context context, List<Long> times, Money money) {
        super(context, R.layout.marker_view);
        this.times = times;
        this.money = money;
        time = findViewById(R.id.marker_time);
        values = findViewById(R.id.marker_ohlc);
    }

    void setMoney(Money money) {
        this.money = money;
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        int i = Math.round(e.getX());
        time.setText(i >= 0 && i < times.size() ? format.format(new Date(times.get(i))) : "");
        if (e instanceof CandleEntry) {
            CandleEntry c = (CandleEntry) e;
            values.setText(getContext().getString(R.string.marker_ohlc,
                    money.format(c.getOpen()), money.format(c.getHigh()),
                    money.format(c.getLow()), money.format(c.getClose())));
        } else {
            values.setText(money.format(e.getY()));
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
