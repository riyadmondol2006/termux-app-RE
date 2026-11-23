package com.termux.app.features.system.floating;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

/** The root view of the floating terminal window, which reports the Back key, since a window has no activity to handle it. */
public final class FloatingRootLayout extends FrameLayout {

    @Nullable
    private Runnable mOnBack;

    public FloatingRootLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setOnBack(@Nullable Runnable onBack) {
        mOnBack = onBack;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && mOnBack != null) {
            if (event.getAction() == KeyEvent.ACTION_UP) mOnBack.run();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

}
