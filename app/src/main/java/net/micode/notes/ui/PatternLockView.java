/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * A 3×3 pattern-lock grid View.
 *
 * <p>Dot indices are arranged as:
 * <pre>
 *  0 | 1 | 2
 *  ---------
 *  3 | 4 | 5
 *  ---------
 *  6 | 7 | 8
 * </pre>
 *
 * <p>The resulting pattern is reported via {@link OnPatternCompleteListener} as a
 * comma-separated string of the selected indices, e.g. {@code "0,1,2,5,8"}.
 */
public class PatternLockView extends View {

    public interface OnPatternCompleteListener {
        /** Called when the user lifts their finger after drawing at least one dot. */
        void onPatternComplete(String pattern);
    }

    private static final int GRID_SIZE = 3;
    private static final int DOT_COUNT = GRID_SIZE * GRID_SIZE;

    // Minimum dots required for a valid pattern
    private static final int MIN_PATTERN_LENGTH = 4;

    // Reset-to-idle delay after showing error state (ms)
    private static final long RESET_DELAY_MS = 800;

    // ---- Colours ----
    private static final int COLOR_DOT_NORMAL  = 0xFFAAAAAA;
    private static final int COLOR_DOT_SELECTED = 0xFF4A90D9;
    private static final int COLOR_DOT_ERROR   = 0xFFE53935;
    private static final int COLOR_LINE_NORMAL  = 0x884A90D9;
    private static final int COLOR_LINE_ERROR   = 0x88E53935;
    private static final int COLOR_INNER_NORMAL  = 0xFFFFFFFF;
    private static final int COLOR_INNER_SELECTED = 0xFFFFFFFF;

    public enum DisplayMode { IDLE, IN_PROGRESS, CORRECT, WRONG }

    private DisplayMode mDisplayMode = DisplayMode.IDLE;

    // Dot centres in px, indexed 0-8
    private final float[] mDotX = new float[DOT_COUNT];
    private final float[] mDotY = new float[DOT_COUNT];

    // Currently selected dot indices (in order of selection)
    private final List<Integer> mPattern = new ArrayList<>();

    // Current finger position while drawing
    private float mCurrentX = -1, mCurrentY = -1;

    private final Paint mDotPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  mLinePath  = new Path();

    private OnPatternCompleteListener mListener;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public PatternLockView(Context context) {
        super(context);
        init();
    }

    public PatternLockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PatternLockView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeCap(Paint.Cap.ROUND);
        mLinePaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setOnPatternCompleteListener(OnPatternCompleteListener listener) {
        mListener = listener;
    }

    /** Clears the current pattern and resets the view to idle. */
    public void clearPattern() {
        mPattern.clear();
        mCurrentX = -1;
        mCurrentY = -1;
        mDisplayMode = DisplayMode.IDLE;
        invalidate();
    }

    /** Marks the current pattern as wrong (red) then auto-clears after a delay. */
    public void showError() {
        mDisplayMode = DisplayMode.WRONG;
        invalidate();
        mHandler.removeCallbacksAndMessages(null);
        mHandler.postDelayed(this::clearPattern, RESET_DELAY_MS);
    }

    // ---- Layout ----

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        int side = Math.min(w, h);
        float cellSize = side / (float) GRID_SIZE;
        float offsetX = (w - side) / 2f;
        float offsetY = (h - side) / 2f;
        for (int i = 0; i < DOT_COUNT; i++) {
            int col = i % GRID_SIZE;
            int row = i / GRID_SIZE;
            mDotX[i] = offsetX + col * cellSize + cellSize / 2f;
            mDotY[i] = offsetY + row * cellSize + cellSize / 2f;
        }
    }

    // ---- Drawing ----

    @Override
    protected void onDraw(Canvas canvas) {
        int side = Math.min(getWidth(), getHeight());
        float cellSize = side / (float) GRID_SIZE;
        float outerRadius = cellSize * 0.22f;
        float innerRadius = outerRadius * 0.45f;
        float lineWidth   = outerRadius * 0.3f;

        boolean isError = mDisplayMode == DisplayMode.WRONG;
        int lineColor = isError ? COLOR_LINE_ERROR : COLOR_LINE_NORMAL;
        int dotColor  = isError ? COLOR_DOT_ERROR  : COLOR_DOT_SELECTED;

        mLinePaint.setStrokeWidth(lineWidth);

        // Draw connection lines between selected dots
        mLinePath.reset();
        if (mPattern.size() > 1) {
            mLinePaint.setColor(lineColor);
            for (int i = 0; i < mPattern.size(); i++) {
                int idx = mPattern.get(i);
                if (i == 0) {
                    mLinePath.moveTo(mDotX[idx], mDotY[idx]);
                } else {
                    mLinePath.lineTo(mDotX[idx], mDotY[idx]);
                }
            }
            canvas.drawPath(mLinePath, mLinePaint);
        }

        // Draw line from last selected dot to current finger position
        if (!mPattern.isEmpty() && mCurrentX >= 0 && mDisplayMode == DisplayMode.IN_PROGRESS) {
            int last = mPattern.get(mPattern.size() - 1);
            mLinePaint.setColor(lineColor);
            canvas.drawLine(mDotX[last], mDotY[last], mCurrentX, mCurrentY, mLinePaint);
        }

        // Draw dots
        for (int i = 0; i < DOT_COUNT; i++) {
            boolean selected = mPattern.contains(i);
            mDotPaint.setColor(selected ? dotColor : COLOR_DOT_NORMAL);
            canvas.drawCircle(mDotX[i], mDotY[i], outerRadius, mDotPaint);
            // Inner white highlight
            mDotPaint.setColor(selected ? COLOR_INNER_SELECTED : COLOR_INNER_NORMAL);
            canvas.drawCircle(mDotX[i], mDotY[i], innerRadius, mDotPaint);
        }
    }

    // ---- Touch ----

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mDisplayMode == DisplayMode.WRONG || mDisplayMode == DisplayMode.CORRECT) {
            return true; // ignore input while showing result
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                clearPattern();
                mDisplayMode = DisplayMode.IN_PROGRESS;
                handleTouch(event.getX(), event.getY());
                break;
            case MotionEvent.ACTION_MOVE:
                handleTouch(event.getX(), event.getY());
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mCurrentX = -1;
                mCurrentY = -1;
                finishPattern();
                break;
        }
        invalidate();
        return true;
    }

    private void handleTouch(float x, float y) {
        mCurrentX = x;
        mCurrentY = y;

        int side = Math.min(getWidth(), getHeight());
        float hitRadius = (side / (float) GRID_SIZE) * 0.35f;

        for (int i = 0; i < DOT_COUNT; i++) {
            if (mPattern.contains(i)) continue;
            float dx = x - mDotX[i];
            float dy = y - mDotY[i];
            if (dx * dx + dy * dy <= hitRadius * hitRadius) {
                mPattern.add(i);
                break;
            }
        }
    }

    private void finishPattern() {
        if (mPattern.isEmpty()) {
            mDisplayMode = DisplayMode.IDLE;
            return;
        }
        mDisplayMode = DisplayMode.IDLE;
        if (mListener != null) {
            mListener.onPatternComplete(buildPatternString());
        }
    }

    /** Encodes the current pattern as a comma-separated string (e.g. {@code "0,1,2,5,8"}). */
    public String buildPatternString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mPattern.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(mPattern.get(i));
        }
        return sb.toString();
    }

    public int getPatternLength() {
        return mPattern.size();
    }
}
