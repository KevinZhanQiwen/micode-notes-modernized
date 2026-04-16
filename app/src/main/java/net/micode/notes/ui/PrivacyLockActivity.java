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

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;

/**
 * Fullscreen unlock screen for the Privacy Space.
 *
 * <p>Shows either the pattern lock grid or the PIN numpad depending on
 * what the user configured. Returns {@code RESULT_OK} on successful unlock.
 */
public class PrivacyLockActivity extends Activity {

    private static final int PIN_LENGTH = 4;

    private PrivacySpaceManager mManager;

    private View mPatternContainer;
    private PatternLockView mPatternView;
    private TextView mHint;

    private View mPinContainer;
    private LinearLayout mPinDotsLayout;
    private final StringBuilder mPinInput = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_lock);

        mManager = PrivacySpaceManager.getInstance(this);

        mHint = findViewById(R.id.tv_lock_hint);

        mPatternContainer = findViewById(R.id.pattern_lock_view);
        mPatternView = (PatternLockView) mPatternContainer;

        mPinContainer    = findViewById(R.id.ll_pin_container);
        mPinDotsLayout   = findViewById(R.id.ll_pin_dots);

        String authType = mManager.getAuthType();

        if (PrivacySpaceManager.AUTH_TYPE_PATTERN.equals(authType)) {
            mPatternContainer.setVisibility(View.VISIBLE);
            mHint.setText(R.string.privacy_lock_draw_pattern);
            mPatternView.setOnPatternCompleteListener(pattern -> {
                if (mManager.verify(pattern)) {
                    setResult(RESULT_OK);
                    finish();
                } else {
                    mHint.setText(R.string.privacy_lock_wrong_pattern);
                    mPatternView.showError();
                }
            });
        } else {
            mPinContainer.setVisibility(View.VISIBLE);
            mHint.setText(R.string.privacy_lock_enter_pin);
            buildPinDots();
            setupNumberPad();
        }
    }

    // ---- PIN ----

    private void buildPinDots() {
        mPinDotsLayout.removeAllViews();
        int dotSizePx = dpToPx(14);
        int marginPx  = dpToPx(10);
        for (int i = 0; i < PIN_LENGTH; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotSizePx, dotSizePx);
            lp.setMargins(marginPx, 0, marginPx, 0);
            dot.setLayoutParams(lp);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(Color.parseColor("#FFAAAAAA"));
            dot.setBackground(shape);
            dot.setTag(i);
            mPinDotsLayout.addView(dot);
        }
    }

    private void refreshPinDots() {
        int filled = mPinInput.length();
        for (int i = 0; i < PIN_LENGTH; i++) {
            View dot = mPinDotsLayout.findViewWithTag(i);
            if (dot == null) continue;
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(i < filled
                    ? Color.parseColor("#FF4A90D9")
                    : Color.parseColor("#FFAAAAAA"));
            dot.setBackground(shape);
        }
    }

    private void setupNumberPad() {
        int[] btnIds = {
                R.id.btn_1, R.id.btn_2, R.id.btn_3,
                R.id.btn_4, R.id.btn_5, R.id.btn_6,
                R.id.btn_7, R.id.btn_8, R.id.btn_9,
                R.id.btn_0
        };
        String[] digits = {"1","2","3","4","5","6","7","8","9","0"};

        for (int i = 0; i < btnIds.length; i++) {
            final String digit = digits[i];
            View btn = findViewById(btnIds[i]);
            if (btn != null) {
                btn.setOnClickListener(v -> appendDigit(digit));
            }
        }
        View deleteBtn = findViewById(R.id.btn_delete);
        if (deleteBtn != null) {
            deleteBtn.setOnClickListener(v -> deleteDigit());
        }
    }

    private void appendDigit(String digit) {
        if (mPinInput.length() >= PIN_LENGTH) return;
        mPinInput.append(digit);
        refreshPinDots();
        if (mPinInput.length() == PIN_LENGTH) {
            onPinComplete(mPinInput.toString());
        }
    }

    private void deleteDigit() {
        if (mPinInput.length() > 0) {
            mPinInput.deleteCharAt(mPinInput.length() - 1);
            refreshPinDots();
        }
    }

    private void onPinComplete(String pin) {
        if (mManager.verify(pin)) {
            setResult(RESULT_OK);
            finish();
        } else {
            mHint.setText(R.string.privacy_lock_wrong_pin);
            mPinInput.setLength(0);
            refreshPinDots();
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
