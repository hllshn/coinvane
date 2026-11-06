package com.abraxel.cryptocurrency.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Logosu hiçbir kaynakta olmayan coin için nötr daire + baş harfler (ör. "BMT"). Emoji ya da sahte logo değil. */
public final class InitialsDrawable extends android.graphics.drawable.Drawable {
    private final Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String label;

    public InitialsDrawable(String base, int background, int foreground) {
        int end = base.offsetByCodePoints(0, Math.min(3, base.codePointCount(0, base.length())));
        label = base.substring(0, end);
        circle.setColor(background);
        text.setColor(foreground);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        float r = Math.min(b.width(), b.height()) / 2f;
        canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r, circle);
        text.setTextSize(r * (label.length() > 2 ? 0.62f : 0.8f));
        float y = b.exactCenterY() - (text.descent() + text.ascent()) / 2;
        canvas.drawText(label, b.exactCenterX(), y, text);
    }

    @Override
    public void setAlpha(int alpha) {
        circle.setAlpha(alpha);
        text.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter filter) {
        text.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
