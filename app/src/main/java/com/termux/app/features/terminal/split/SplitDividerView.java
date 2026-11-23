package com.termux.app.features.terminal.split;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/**
 * The divider between the panes of a {@link SplitTerminalLayout}, with a drag handle. Dragging it
 * moves it and sets the sizes of the panes when released, so the sessions are resized only once.
 * Tapping it shows the split screen options.
 */
@SuppressLint("ViewConstructor")
final class SplitDividerView extends View {

    interface Listener {
        /** Called when the divider was dragged so the first pane has {@code ratio} of the size. */
        void onDividerDragged(float ratio);

        /** Called when the divider was tapped. */
        void onDividerClicked(@NonNull View divider);
    }

    private final Listener mListener;
    private final Paint mBackgroundPaint = new Paint();
    private final Paint mHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mHandleRect = new RectF();
    private final int mHandleColor;
    private final int mDraggingHandleColor;
    private final float mHandleLength;
    private final float mHandleThickness;
    private final int mTouchSlop;

    private boolean mSideBySide;
    private float mDownRawPosition;
    private boolean mDragging;

    SplitDividerView(@NonNull Context context, @NonNull Listener listener) {
        super(context);
        mListener = listener;
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(12 * density);
        setMinimumWidth(size);
        setMinimumHeight(size);
        mHandleLength = 32 * density;
        mHandleThickness = 4 * density;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        mBackgroundPaint.setColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFF303030));
        mHandleColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutline, 0xFF909090);
        mDraggingHandleColor = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0xFFFFFFFF);
        mHandlePaint.setColor(mHandleColor);

        setContentDescription(context.getString(R.string.feature_terminal_split_divider));
        setFocusable(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // A divider between panes side by side is vertical.
        mSideBySide = h > w;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int width = getWidth(), height = getHeight();
        canvas.drawRect(0, 0, width, height, mBackgroundPaint);
        float centerX = width / 2f, centerY = height / 2f;
        if (mSideBySide) {
            mHandleRect.set(centerX - mHandleThickness / 2, centerY - mHandleLength / 2, centerX + mHandleThickness / 2, centerY + mHandleLength / 2);
        } else {
            mHandleRect.set(centerX - mHandleLength / 2, centerY - mHandleThickness / 2, centerX + mHandleLength / 2, centerY + mHandleThickness / 2);
        }
        mHandlePaint.setColor(mDragging ? mDraggingHandleColor : mHandleColor);
        canvas.drawRoundRect(mHandleRect, mHandleThickness / 2, mHandleThickness / 2, mHandlePaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float rawPosition = mSideBySide ? event.getRawX() : event.getRawY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownRawPosition = rawPosition;
                mDragging = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float delta = rawPosition - mDownRawPosition;
                if (!mDragging && Math.abs(delta) > mTouchSlop) {
                    mDragging = true;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    invalidate();
                }
                if (mDragging) setDragOffset(delta);
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (mDragging) {
                    float ratio = getRatioForOffset(rawPosition - mDownRawPosition);
                    resetDrag();
                    mListener.onDividerDragged(ratio);
                } else {
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                resetDrag();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        mListener.onDividerClicked(this);
        return true;
    }

    private void resetDrag() {
        mDragging = false;
        setTranslationX(0);
        setTranslationY(0);
        invalidate();
    }

    /** Move the divider by {@code offset}, kept within the min pane sizes. */
    private void setDragOffset(float offset) {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent == null) return;
        float position = (mSideBySide ? getLeft() : getTop()) + offset;
        float total = mSideBySide ? parent.getWidth() : parent.getHeight();
        float min = total * SplitTerminalLayout.MIN_PANE_FRACTION;
        float max = total - (mSideBySide ? getWidth() : getHeight()) - min;
        position = Math.max(min, Math.min(max, position));
        if (mSideBySide) setTranslationX(position - getLeft());
        else setTranslationY(position - getTop());
    }

    /** Get the size fraction of the first pane if the divider is moved by {@code offset}. */
    private float getRatioForOffset(float offset) {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent == null) return 0.5f;
        float size = mSideBySide ? getWidth() : getHeight();
        float available = (mSideBySide ? parent.getWidth() : parent.getHeight()) - size;
        if (available <= 0) return 0.5f;
        float position = (mSideBySide ? getLeft() : getTop()) + offset;
        return position / available;
    }

}
