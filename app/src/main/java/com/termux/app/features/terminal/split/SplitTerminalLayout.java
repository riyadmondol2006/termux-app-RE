package com.termux.app.features.terminal.split;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The container of the terminal view, which can split it into two panes.
 *
 * Without a split it lays out its only child, the terminal view of the activity, over its whole
 * size, like a {@link android.widget.FrameLayout}. With a split, the panes are side by side in
 * landscape and stacked in portrait, with a divider between them. The primary view is the terminal
 * view of the activity, which always has the input focus, while the secondary view shows the other
 * pane and is dimmed. Moving the focus to the other pane swaps the places of the views, see
 * {@link SplitScreenController}.
 */
public final class SplitTerminalLayout extends ViewGroup {

    /** Notified of taps on the secondary pane. */
    interface Listener {
        void onSecondaryPaneTapped();
    }

    /** The min size of a pane as a fraction of the size of the layout. */
    static final float MIN_PANE_FRACTION = 0.15f;

    @Nullable private View mPrimaryView;
    @Nullable private View mSecondaryView;
    @Nullable private View mDivider;
    @Nullable private Listener mListener;

    private boolean mSplit;
    /** The size of the first pane (top or left) as a fraction of the size available for both panes. */
    private float mRatio = 0.5f;
    /** The pane the primary view is in, 0 for the top or left pane and 1 for the other. */
    private int mPrimaryPane;

    private final Rect mPrimaryRect = new Rect();
    private final Rect mSecondaryRect = new Rect();
    private final Rect mDividerRect = new Rect();
    private final Paint mScrimPaint = new Paint();

    private final int mTouchSlop;
    private boolean mSecondaryTapCandidate;
    private float mDownX, mDownY;

    public SplitTerminalLayout(Context context) {
        this(context, null);
    }

    public SplitTerminalLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        // Dim the pane without the input focus.
        mScrimPaint.setColor(0x47000000);
        setWillNotDraw(true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() > 0) mPrimaryView = getChildAt(0);
    }

    void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    boolean isSplit() {
        return mSplit;
    }

    /** Show {@code secondaryView} and {@code divider} next to the primary view. */
    void split(@NonNull View secondaryView, @NonNull View divider) {
        if (mSplit) return;
        mSecondaryView = secondaryView;
        mDivider = divider;
        if (secondaryView.getParent() == null) addView(secondaryView);
        if (divider.getParent() == null) addView(divider);
        mSplit = true;
        setWillNotDraw(false);
        layoutNow();
    }

    /** Remove the secondary view and the divider, so the primary view takes the whole size again. */
    void unsplit() {
        if (!mSplit) return;
        mSplit = false;
        if (mSecondaryView != null) removeView(mSecondaryView);
        if (mDivider != null) removeView(mDivider);
        mSecondaryView = null;
        mDivider = null;
        setWillNotDraw(true);
        layoutNow();
    }

    float getRatio() {
        return mRatio;
    }

    void setRatio(float ratio) {
        mRatio = Math.max(MIN_PANE_FRACTION, Math.min(1 - MIN_PANE_FRACTION, ratio));
        if (mSplit) layoutNow();
    }

    int getPrimaryPane() {
        return mPrimaryPane;
    }

    /** Set the pane the primary view is in, 0 for the top or left pane and 1 for the other. */
    void setPrimaryPane(int pane) {
        mPrimaryPane = pane == 1 ? 1 : 0;
        if (mSplit) layoutNow();
    }

    /** Whether the panes are side by side, which is the case in landscape. */
    boolean isSideBySide() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * Measure and lay out the children now, so the terminal views get their new sizes before
     * sessions are attached to them, instead of resizing the sessions twice.
     */
    void layoutNow() {
        requestLayout();
        if (getWidth() == 0 || getHeight() == 0) return;
        measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
        layout(getLeft(), getTop(), getRight(), getBottom());
        invalidate();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // The panes change from stacked to side by side.
        if (mSplit) requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        int height = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(width, height);

        if (!mSplit || mSecondaryView == null || mDivider == null) {
            mPrimaryRect.set(0, 0, width, height);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != GONE) measureChild(child, mPrimaryRect);
            }
            return;
        }

        boolean sideBySide = isSideBySide();
        int total = sideBySide ? width : height;
        int dividerSize = sideBySide ? mDivider.getMinimumWidth() : mDivider.getMinimumHeight();
        int available = Math.max(0, total - dividerSize);
        int minPane = Math.round(total * MIN_PANE_FRACTION);
        int first = Math.max(Math.min(minPane, available / 2), Math.min(Math.round(available * mRatio), available - minPane));

        Rect firstRect = mPrimaryPane == 0 ? mPrimaryRect : mSecondaryRect;
        Rect secondRect = mPrimaryPane == 0 ? mSecondaryRect : mPrimaryRect;
        if (sideBySide) {
            firstRect.set(0, 0, first, height);
            mDividerRect.set(first, 0, first + dividerSize, height);
            secondRect.set(first + dividerSize, 0, width, height);
        } else {
            firstRect.set(0, 0, width, first);
            mDividerRect.set(0, first, width, first + dividerSize);
            secondRect.set(0, first + dividerSize, width, height);
        }

        if (mPrimaryView != null) measureChild(mPrimaryView, mPrimaryRect);
        measureChild(mSecondaryView, mSecondaryRect);
        measureChild(mDivider, mDividerRect);
    }

    private static void measureChild(@NonNull View child, @NonNull Rect rect) {
        child.measure(MeasureSpec.makeMeasureSpec(rect.width(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(rect.height(), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            Rect rect;
            if (!mSplit || child == mPrimaryView) rect = mPrimaryRect;
            else if (child == mSecondaryView) rect = mSecondaryRect;
            else if (child == mDivider) rect = mDividerRect;
            else rect = mPrimaryRect;
            child.layout(rect.left, rect.top, rect.right, rect.bottom);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mSplit) canvas.drawRect(mSecondaryRect, mScrimPaint);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Watch for taps on the secondary pane to move the focus to it. The secondary view still
        // gets the events, so it can be scrolled.
        if (mSplit) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mSecondaryTapCandidate = mSecondaryRect.contains((int) event.getX(), (int) event.getY());
                    mDownX = event.getX();
                    mDownY = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mSecondaryTapCandidate && (Math.abs(event.getX() - mDownX) > mTouchSlop || Math.abs(event.getY() - mDownY) > mTouchSlop))
                        mSecondaryTapCandidate = false;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_CANCEL:
                    mSecondaryTapCandidate = false;
                    break;
                case MotionEvent.ACTION_UP:
                    if (mSecondaryTapCandidate && event.getEventTime() - event.getDownTime() < ViewConfiguration.getLongPressTimeout()) {
                        mSecondaryTapCandidate = false;
                        // After the secondary view handled the event.
                        post(() -> {
                            if (mSplit && mListener != null) mListener.onSecondaryPaneTapped();
                        });
                    }
                    break;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

}
