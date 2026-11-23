package com.termux.app.features.system.floating;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;

import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.io.File;

/**
 * The floating terminal: a window over other apps with a {@link TerminalView}, a title bar to move
 * it, a handle to resize it, a minimized form as a small draggable bubble and a keys row.
 *
 * The window does not take the keyboard focus unless the user taps the terminal, so the apps below
 * stay usable and keep their keyboard. Tapping outside the window, or Back, gives the focus back.
 *
 * All methods must be called on the main thread.
 */
final class FloatingTerminalWindow implements FloatingSessionClient.Listener, FloatingTerminalViewClient.Callbacks {

    interface Callbacks {
        /** The user closed the window. The session keeps running. */
        void onCloseRequested();
        /** Enter was pressed in the window with a finished session. */
        void onFinishedSessionEnter(@NonNull TerminalSession session);
    }

    private static final int TITLE_BAR_DP = 40;
    private static final int MIN_WIDTH_DP = 200;
    private static final int MIN_HEIGHT_DP = 140;
    private static final int BUBBLE_DP = 52;
    private static final int GRAB_DP = 56;

    private static final String LOG_TAG = "FloatingTerminalWindow";

    private final Context mContext;
    private final Callbacks mCallbacks;
    private final WindowManager mWindowManager;
    private final FloatingTerminalPreferences mPreferences;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final float mDensity;

    private final FloatingRootLayout mRoot;
    private final TerminalView mTerminalView;
    private final TextView mTitleView;
    private final FloatingTerminalViewClient mViewClient;
    private final View mBubble;
    private WindowManager.LayoutParams mWindowParams;
    private WindowManager.LayoutParams mBubbleParams;
    private final TextView mCtrlKey, mAltKey;

    @Nullable private TerminalSession mSession;

    /** The background color set on the terminal view, or 0 if not set yet. */

    private int mBackgroundColor;
    private boolean mFocusable;
    private boolean mMinimized;
    private boolean mDestroyed;
    private int mFontSize;

    @SuppressLint("ClickableViewAccessibility")
    FloatingTerminalWindow(@NonNull Context context, @NonNull Callbacks callbacks) {
        mCallbacks = callbacks;
        // The Material theme is needed for the colors of the views, which a service does not have.
        mContext = new ContextThemeWrapper(context, R.style.Theme_TermuxApp_DayNight_NoActionBar);
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mPreferences = new FloatingTerminalPreferences(context);
        mDensity = context.getResources().getDisplayMetrics().density;

        mRoot = (FloatingRootLayout) LayoutInflater.from(mContext).inflate(R.layout.feature_system_floating_window, null);
        mTerminalView = mRoot.findViewById(R.id.feature_system_floating_terminal);
        mTitleView = mRoot.findViewById(R.id.feature_system_floating_title);
        mRoot.setClipToOutline(true);
        mRoot.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);

        mViewClient = new FloatingTerminalViewClient(this);
        mTerminalView.setTerminalViewClient(mViewClient);
        mTerminalView.setTerminalCursorBlinkerRate(0);
        applyFont();

        mRoot.setOnBack(() -> {
            if (mFocusable) setFocusable(false);
        });
        // A touch outside of the window while it has the keyboard focus gives the focus back.
        mRoot.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE && mFocusable) setFocusable(false);
            return false;
        });

        mRoot.findViewById(R.id.feature_system_floating_close).setOnClickListener(v -> mCallbacks.onCloseRequested());
        mRoot.findViewById(R.id.feature_system_floating_minimize).setOnClickListener(v -> minimize());
        mRoot.findViewById(R.id.feature_system_floating_keyboard).setOnClickListener(v -> {
            if (mFocusable) setFocusable(false); else requestKeyboardFocus();
        });

        setUpMoveHandler(mRoot.findViewById(R.id.feature_system_floating_title_bar));
        setUpResizeHandler(mRoot.findViewById(R.id.feature_system_floating_resize));

        LinearLayout keys = mRoot.findViewById(R.id.feature_system_floating_keys);
        mCtrlKey = addKey(keys, "CTRL", () -> {
            mViewClient.setCtrlActive(!mViewClient.isCtrlActive());
            updateStickyKeys();
        });
        mAltKey = addKey(keys, "ALT", () -> {
            mViewClient.setAltActive(!mViewClient.isAltActive());
            updateStickyKeys();
        });
        addKey(keys, "ESC", () -> sendKey(KeyEvent.KEYCODE_ESCAPE));
        addKey(keys, "TAB", () -> sendKey(KeyEvent.KEYCODE_TAB));
        addKey(keys, "←", () -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT));
        addKey(keys, "↓", () -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN));
        addKey(keys, "↑", () -> sendKey(KeyEvent.KEYCODE_DPAD_UP));
        addKey(keys, "→", () -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT));
        addKey(keys, "HOME", () -> sendKey(KeyEvent.KEYCODE_MOVE_HOME));
        addKey(keys, "END", () -> sendKey(KeyEvent.KEYCODE_MOVE_END));
        addKey(keys, "PGUP", () -> sendKey(KeyEvent.KEYCODE_PAGE_UP));
        addKey(keys, "PGDN", () -> sendKey(KeyEvent.KEYCODE_PAGE_DOWN));
        // Space for the resize handle, which covers the end of the row.
        keys.addView(new View(mContext), new LinearLayout.LayoutParams(dp(36), 1));

        mBubble = createBubble();
        createLayoutParams();
    }



    /** Add the window and the bubble to the screen. The bubble is only shown while minimized. */
    void show() {
        mWindowManager.addView(mRoot, mWindowParams);
        mWindowManager.addView(mBubble, mBubbleParams);
        mBubble.setVisibility(View.GONE);
    }

    void destroy() {
        if (mDestroyed) return;
        mDestroyed = true;
        mHandler.removeCallbacksAndMessages(null);
        if (mFocusable) hideKeyboard();
        try {
            mWindowManager.removeView(mRoot);
        } catch (IllegalArgumentException e) {
            // Not added.
        }
        try {
            mWindowManager.removeView(mBubble);
        } catch (IllegalArgumentException e) {
            // Not added.
        }
        mSession = null;
    }

    void attachSession(@NonNull TerminalSession session) {
        mSession = session;
        mTerminalView.attachSession(session);
        updateTitle();
        // The view size is only known after it has been laid out, which sets the emulator size.
        mTerminalView.post(() -> {
            mTerminalView.updateSize();
            updateBackgroundColor();
        });
    }

    /**
     * Use the background color of the session's color scheme, like the terminal activity does for
     * its window, so the window matches the theme.
     */
    private void updateBackgroundColor() {
        if (mDestroyed || mSession == null || mSession.getEmulator() == null) return;
        int color = mSession.getEmulator().mColors.mCurrentColors[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND];
        if (color != mBackgroundColor) {
            mBackgroundColor = color;
            mTerminalView.setBackgroundColor(color);
        }
    }

    boolean isMinimized() {
        return mMinimized;
    }

    void minimize() {
        if (mMinimized || mDestroyed) return;
        mMinimized = true;
        if (mFocusable) setFocusable(false);
        mRoot.setVisibility(View.GONE);
        mBubble.setVisibility(View.VISIBLE);
        mWindowManager.updateViewLayout(mRoot, mWindowParams);
    }

    void restore() {
        if (!mMinimized || mDestroyed) return;
        mMinimized = false;
        mBubble.setVisibility(View.GONE);
        mRoot.setVisibility(View.VISIBLE);
        clampToScreen();
        mTerminalView.post(mTerminalView::updateSize);
    }

    /** Keep the window on the screen, for example after the screen was rotated. */
    void clampToScreen() {
        if (mDestroyed) return;
        int[] screen = getScreenSize();
        int[] size = FloatingGeometry.clampSize(mWindowParams.width, mWindowParams.height, screen[0], screen[1], dp(MIN_WIDTH_DP), dp(MIN_HEIGHT_DP));
        mWindowParams.width = size[0];
        mWindowParams.height = size[1];
        int[] position = FloatingGeometry.clampPosition(mWindowParams.x, mWindowParams.y, size[0], screen[0], screen[1], dp(TITLE_BAR_DP), dp(GRAB_DP));
        mWindowParams.x = position[0];
        mWindowParams.y = position[1];
        mWindowManager.updateViewLayout(mRoot, mWindowParams);

        int[] bubble = FloatingGeometry.clampBubble(mBubbleParams.x, mBubbleParams.y, dp(BUBBLE_DP), screen[0], screen[1]);
        mBubbleParams.x = bubble[0];
        mBubbleParams.y = bubble[1];
        mWindowManager.updateViewLayout(mBubble, mBubbleParams);
    }

    /** Update the colors and font after they changed in the app. */
    void applyFont() {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(mContext);
        int appSize = preferences != null ? preferences.getFontSize() : 28;
        int saved = mPreferences.getFontSize();
        mFontSize = saved != FloatingTerminalPreferences.NOT_SET ? clampFont(saved, appSize) : appSize;
        mTerminalView.setTextSize(mFontSize);
        File fontFile = TermuxConstants.TERMUX_FONT_FILE;
        Typeface typeface = Typeface.MONOSPACE;
        try {
            if (fontFile.exists() && fontFile.length() > 0) typeface = Typeface.createFromFile(fontFile);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load the terminal font", e);
        }
        mTerminalView.setTypeface(typeface);
    }

    private static int clampFont(int size, int appSize) {
        return FloatingGeometry.clamp(size, Math.max(6, appSize / 2), appSize * 3);
    }



    // Keyboard focus. The window is not focusable until the user taps the terminal, so it does
    // not take the keyboard from the app below.

    void requestKeyboardFocus() {
        if (mDestroyed || mMinimized) return;
        if (!mFocusable) setFocusable(true);
        mTerminalView.requestFocus();
        // The window only gets the focus after the layout change was processed.
        mHandler.postDelayed(this::showKeyboard, 120);
    }

    private void showKeyboard() {
        if (mDestroyed || !mFocusable) return;
        InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        mTerminalView.requestFocus();
        imm.showSoftInput(mTerminalView, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(mTerminalView.getWindowToken(), 0);
    }

    private void setFocusable(boolean focusable) {
        if (mDestroyed || mFocusable == focusable) return;
        mFocusable = focusable;
        if (focusable) {
            mWindowParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            hideKeyboard();
            mTerminalView.clearFocus();
            mWindowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        mWindowManager.updateViewLayout(mRoot, mWindowParams);
        mRoot.findViewById(R.id.feature_system_floating_keyboard).setAlpha(focusable ? 1f : 0.6f);
    }



    // Callbacks of the view client.

    @Override
    public void onTerminalTapped() {
        requestKeyboardFocus();
    }

    @Override
    public void onFontSizeChangeRequested(boolean increase) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(mContext);
        int appSize = preferences != null ? preferences.getFontSize() : 28;
        mFontSize = clampFont(mFontSize + (increase ? 1 : -1), appSize);
        mPreferences.setFontSize(mFontSize);
        mTerminalView.setTextSize(mFontSize);
    }

    @Override
    public void onFinishedSessionEnter() {
        if (mSession != null) mCallbacks.onFinishedSessionEnter(mSession);
    }

    // Callbacks of the session client.

    @Override
    public void onTextChanged() {
        if (!mDestroyed && !mMinimized) {
            mTerminalView.onScreenUpdated();
            // The colors are reset without a callback when the theme is reloaded.
            updateBackgroundColor();
        }
    }

    @Override
    public void onTitleChanged() {
        updateTitle();
    }

    @Override
    public void onSessionFinished() {
        updateTitle();
        if (!mDestroyed) mTerminalView.onScreenUpdated();
    }

    @Override
    public void onColorsChanged() {
        if (!mDestroyed) {
            updateBackgroundColor();
            mTerminalView.onScreenUpdated();
        }
    }

    private void updateTitle() {
        if (mSession == null) return;
        String name = mSession.mSessionName != null && !mSession.mSessionName.isEmpty() ? mSession.mSessionName : mSession.getTitle();
        String title = name != null && !name.isEmpty() ? name : mContext.getString(R.string.feature_system_floating_title);
        if (!mSession.isRunning()) title += " " + mContext.getString(R.string.feature_system_floating_exited);
        mTitleView.setText(title);
    }



    // Keys row.

    private TextView addKey(@NonNull LinearLayout parent, @NonNull String label, @NonNull Runnable action) {
        TextView key = new TextView(mContext);
        key.setText(label);
        key.setTextColor(0xFFFFFFFF);
        key.setTextSize(12);
        key.setTypeface(Typeface.DEFAULT_BOLD);
        key.setGravity(Gravity.CENTER);
        key.setPadding(dp(11), 0, dp(11), 0);
        key.setClickable(true);
        key.setBackground(androidx.appcompat.content.res.AppCompatResources.getDrawable(mContext,
            getSelectableBackground()));
        key.setOnClickListener(v -> action.run());
        parent.addView(key, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return key;
    }

    private int getSelectableBackground() {
        android.util.TypedValue value = new android.util.TypedValue();
        mContext.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
    }

    private void updateStickyKeys() {
        mCtrlKey.setTextColor(mViewClient.isCtrlActive() ? 0xFFFFB300 : 0xFFFFFFFF);
        mAltKey.setTextColor(mViewClient.isAltActive() ? 0xFFFFB300 : 0xFFFFFFFF);
    }

    private void sendKey(int keyCode) {
        // The terminal view reads the sticky Ctrl and Alt from the view client for this key.
        int mod = 0;
        if (mViewClient.readControlKey()) mod |= KeyEvent.META_CTRL_ON;
        if (mViewClient.readAltKey()) mod |= KeyEvent.META_ALT_ON;
        updateStickyKeys();
        mTerminalView.handleKeyCode(keyCode, mod);
    }



    // Moving and resizing.

    @SuppressLint("ClickableViewAccessibility")
    private void setUpMoveHandler(@NonNull View titleBar) {
        final float[] down = new float[4];
        titleBar.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getRawX();
                    down[1] = event.getRawY();
                    down[2] = mWindowParams.x;
                    down[3] = mWindowParams.y;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    int[] screen = getScreenSize();
                    int[] position = FloatingGeometry.clampPosition((int) (down[2] + event.getRawX() - down[0]),
                        (int) (down[3] + event.getRawY() - down[1]), mWindowParams.width, screen[0], screen[1], dp(TITLE_BAR_DP), dp(GRAB_DP));
                    mWindowParams.x = position[0];
                    mWindowParams.y = position[1];
                    mWindowManager.updateViewLayout(mRoot, mWindowParams);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    saveBounds();
                    return true;
                default:
                    return false;
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setUpResizeHandler(@NonNull View handle) {
        final float[] down = new float[4];
        handle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getRawX();
                    down[1] = event.getRawY();
                    down[2] = mWindowParams.width;
                    down[3] = mWindowParams.height;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    int[] screen = getScreenSize();
                    int width = (int) (down[2] + event.getRawX() - down[0]);
                    int height = (int) (down[3] + event.getRawY() - down[1]);
                    // The window can't grow past the right and bottom edge of the screen.
                    width = Math.min(width, screen[0] - Math.max(0, mWindowParams.x));
                    height = Math.min(height, screen[1] - Math.max(0, mWindowParams.y));
                    int[] size = FloatingGeometry.clampSize(width, height, screen[0], screen[1], dp(MIN_WIDTH_DP), dp(MIN_HEIGHT_DP));
                    mWindowParams.width = size[0];
                    mWindowParams.height = size[1];
                    mWindowManager.updateViewLayout(mRoot, mWindowParams);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    saveBounds();
                    return true;
                default:
                    return false;
            }
        });
    }

    private void saveBounds() {
        mPreferences.setWindowBounds(mWindowParams.x, mWindowParams.y, mWindowParams.width, mWindowParams.height);
    }

    @NonNull
    private View createBubble() {
        ImageView bubble = new ImageView(mContext);
        bubble.setBackgroundResource(R.drawable.feature_system_floating_bubble);
        bubble.setImageResource(R.drawable.feature_system_ic_terminal);
        bubble.setImageTintList(ColorStateList.valueOf(0xFFFFB300));
        bubble.setPadding(dp(13), dp(13), dp(13), dp(13));
        bubble.setContentDescription(mContext.getString(R.string.feature_system_floating_restore));

        final float[] down = new float[5];
        bubble.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        down[0] = event.getRawX();
                        down[1] = event.getRawY();
                        down[2] = mBubbleParams.x;
                        down[3] = mBubbleParams.y;
                        down[4] = 0;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = event.getRawX() - down[0], dy = event.getRawY() - down[1];
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) down[4] = 1; // It is a drag, not a tap.
                        int[] screen = getScreenSize();
                        int[] position = FloatingGeometry.clampBubble((int) (down[2] + dx), (int) (down[3] + dy), dp(BUBBLE_DP), screen[0], screen[1]);
                        mBubbleParams.x = position[0];
                        mBubbleParams.y = position[1];
                        mWindowManager.updateViewLayout(mBubble, mBubbleParams);
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (down[4] == 0) {
                            restore();
                        } else {
                            int[] screen = getScreenSize();
                            mBubbleParams.x = FloatingGeometry.snapBubbleToEdge(mBubbleParams.x, dp(BUBBLE_DP), screen[0]);
                            mWindowManager.updateViewLayout(mBubble, mBubbleParams);
                            mPreferences.setBubblePosition(mBubbleParams.x, mBubbleParams.y);
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
        return bubble;
    }

    private void createLayoutParams() {
        int[] screen = getScreenSize();
        int[] size = FloatingGeometry.getDefaultSize(screen[0], screen[1]);
        int width = mPreferences.getWindowWidth() != FloatingTerminalPreferences.NOT_SET ? mPreferences.getWindowWidth() : size[0];
        int height = mPreferences.getWindowHeight() != FloatingTerminalPreferences.NOT_SET ? mPreferences.getWindowHeight() : size[1];
        size = FloatingGeometry.clampSize(width, height, screen[0], screen[1], dp(MIN_WIDTH_DP), dp(MIN_HEIGHT_DP));

        int x = mPreferences.getWindowX() != FloatingTerminalPreferences.NOT_SET ? mPreferences.getWindowX() : (screen[0] - size[0]) / 2;
        int y = mPreferences.getWindowY() != FloatingTerminalPreferences.NOT_SET ? mPreferences.getWindowY() : screen[1] / 5;
        int[] position = FloatingGeometry.clampPosition(x, y, size[0], screen[0], screen[1], dp(TITLE_BAR_DP), dp(GRAB_DP));

        mWindowParams = newLayoutParams(size[0], size[1]);
        mWindowParams.x = position[0];
        mWindowParams.y = position[1];
        // Keep the window above the keyboard when it has the focus.
        mWindowParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;

        int bubbleSize = dp(BUBBLE_DP);
        mBubbleParams = newLayoutParams(bubbleSize, bubbleSize);
        int[] bubble = FloatingGeometry.clampBubble(
            mPreferences.getBubbleX() != FloatingTerminalPreferences.NOT_SET ? mPreferences.getBubbleX() : screen[0],
            mPreferences.getBubbleY() != FloatingTerminalPreferences.NOT_SET ? mPreferences.getBubbleY() : screen[1] / 3,
            bubbleSize, screen[0], screen[1]);
        mBubbleParams.x = bubble[0];
        mBubbleParams.y = bubble[1];
    }

    @NonNull
    @SuppressWarnings("deprecation")
    private static WindowManager.LayoutParams newLayoutParams(int width, int height) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, height, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        return params;
    }

    /** Returns the {width, height} of the screen in pixels. */
    @NonNull
    @SuppressWarnings("deprecation")
    private int[] getScreenSize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = mWindowManager.getCurrentWindowMetrics().getBounds();
            return new int[]{bounds.width(), bounds.height()};
        }
        DisplayMetrics metrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getRealMetrics(metrics);
        return new int[]{metrics.widthPixels, metrics.heightPixels};
    }

    private int dp(int value) {
        return Math.round(value * mDensity);
    }

}
