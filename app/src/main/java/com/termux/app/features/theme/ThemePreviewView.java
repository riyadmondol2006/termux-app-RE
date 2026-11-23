package com.termux.app.features.theme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/** Live preview rendering sample terminal output (prompt, colored ls, diff) with a theme. */
public class ThemePreviewView extends View {

    private TermuxTheme mTheme = BundledThemes.defaultTheme();
    private Typeface mTypeface = Typeface.MONOSPACE;
    private float mTextSizePx = 14f;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ThemePreviewView(Context c) { super(c); }
    public ThemePreviewView(Context c, @Nullable AttributeSet a) { super(c, a); }

    /** @param textSizePx The text size in pixels, like the terminal font size preference. */
    public void setTheme(TermuxTheme theme, @Nullable Typeface typeface, float textSizePx) {
        mTheme = theme;
        if (typeface != null) mTypeface = typeface;
        mTextSizePx = textSizePx;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(mTheme.background);
        mPaint.setTypeface(mTypeface);
        mPaint.setTextSize(mTextSizePx);
        float x = 16f, y = mPaint.getTextSize() + 12f;
        float lh = mPaint.getTextSize() * 1.5f;
        line(canvas, x, y, new Seg("user@termux", mTheme.ansi[2]), new Seg(":~ $ ", mTheme.foreground), new Seg("ls --color", mTheme.foreground)); y += lh;
        line(canvas, x, y, new Seg("docs/  ", mTheme.ansi[4]), new Seg("photo.jpg  ", mTheme.ansi[5]), new Seg("run.sh*  ", mTheme.ansi[2]), new Seg("notes.txt", mTheme.foreground)); y += lh;
        line(canvas, x, y, new Seg("git diff", mTheme.ansi[6])); y += lh;
        line(canvas, x, y, new Seg("-old line", mTheme.ansi[1])); y += lh;
        line(canvas, x, y, new Seg("+new line", mTheme.ansi[2])); y += lh;
        // Cursor block at end of last prompt line:
        mPaint.setColor(mTheme.cursor);
        canvas.drawRect(x, y - mPaint.getTextSize(), x + mPaint.getTextSize() * 0.6f, y, mPaint);
    }

    private void line(Canvas canvas, float x, float y, Seg... segs) {
        float cx = x;
        for (Seg s : segs) {
            mPaint.setColor(s.color);
            canvas.drawText(s.text, cx, y, mPaint);
            cx += mPaint.measureText(s.text);
        }
    }

    private static final class Seg {
        final String text; final int color;
        Seg(String t, int c) { text = t; color = c; }
    }
}
