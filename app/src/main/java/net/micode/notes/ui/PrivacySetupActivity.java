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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;

/**
 * Guides the user through the first-time Privacy Space setup.
 *
 * <p>Flow:
 * <ol>
 *   <li>Step 1 – choose auth type (pattern or PIN)</li>
 *   <li>Step 2 – enter credential once, then confirm it</li>
 * </ol>
 *
 * <p>Sets {@code RESULT_OK} on success so the caller can proceed to
 * {@link PrivacySpaceActivity}.
 */
public class PrivacySetupActivity extends Activity {

    private static final int PIN_LENGTH = 4;

    // Step containers
    private View mStepChoose;
    private View mStepPattern;
    private View mStepPin;

    // Pattern setup
    private PatternLockView mPatternView;
    private TextView mPatternHint;
    private TextView mPatternSub;

    // PIN setup
    private LinearLayout mPinDotsLayout;
    private TextView mPinHint;
    private TextView mPinSub;
    private final StringBuilder mPinInput = new StringBuilder();

    // Setup state
    private String mSelectedAuthType;
    private String mFirstCredential;
    private boolean mWaitingForConfirm = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_setup);

        mStepChoose  = findViewById(R.id.ll_step_choose);
        mStepPattern = findViewById(R.id.ll_step_pattern);
        mStepPin     = findViewById(R.id.ll_step_pin);

        mPatternView = findViewById(R.id.pattern_setup_view);
        mPatternHint = findViewById(R.id.tv_pattern_setup_hint);
        mPatternSub  = findViewById(R.id.tv_pattern_setup_sub);

        mPinDotsLayout = findViewById(R.id.ll_pin_dots_setup);
        mPinHint = findViewById(R.id.tv_pin_setup_hint);
        mPinSub  = findViewById(R.id.tv_pin_setup_sub);

        // Step 1 buttons
        ((Button) findViewById(R.id.btn_choose_pattern)).setOnClickListener(v -> startPatternSetup());
        ((Button) findViewById(R.id.btn_choose_pin)).setOnClickListener(v -> startPinSetup());

        // Pattern listener
        mPatternView.setOnPatternCompleteListener(this::onPatternDrawn);

        // Number pad listeners
        setupNumberPad();
        buildPinDots();
    }

    // ---- Step 1 ----

    private void startPatternSetup() {
        mSelectedAuthType = PrivacySpaceManager.AUTH_TYPE_PATTERN;
        mFirstCredential   = null;
        mWaitingForConfirm = false;
        mPatternHint.setText(R.string.privacy_setup_draw_pattern);
        mPatternSub.setText("");
        mStepChoose.setVisibility(View.GONE);
        mStepPattern.setVisibility(View.VISIBLE);
    }

    private void startPinSetup() {
        mSelectedAuthType = PrivacySpaceManager.AUTH_TYPE_PIN;
        mFirstCredential   = null;
        mWaitingForConfirm = false;
        mPinInput.setLength(0);
        mPinHint.setText(R.string.privacy_setup_enter_pin);
        mPinSub.setText("");
        refreshPinDots();
        mStepChoose.setVisibility(View.GONE);
        mStepPin.setVisibility(View.VISIBLE);
    }

    // ---- Pattern flow ----

    private void onPatternDrawn(String pattern) {
        if (pattern.split(",").length < 4) {
            mPatternSub.setText(R.string.privacy_setup_pattern_too_short);
            mPatternView.showError();
            return;
        }

        if (!mWaitingForConfirm) {
            mFirstCredential   = pattern;
            mWaitingForConfirm = true;
            mPatternHint.setText(R.string.privacy_setup_draw_pattern_again);
            mPatternSub.setText("");
            mPatternView.clearPattern();
        } else {
            if (pattern.equals(mFirstCredential)) {
                finishSetup(PrivacySpaceManager.AUTH_TYPE_PATTERN, mFirstCredential);
            } else {
                mPatternSub.setText(R.string.privacy_setup_pattern_mismatch);
                mPatternView.showError();
                // Reset to first step
                mWaitingForConfirm = false;
                mFirstCredential   = null;
                mPatternHint.setText(R.string.privacy_setup_draw_pattern);
            }
        }
    }

    // ---- PIN flow ----

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
        if (!mWaitingForConfirm) {
            mFirstCredential   = pin;
            mWaitingForConfirm = true;
            mPinInput.setLength(0);
            mPinHint.setText(R.string.privacy_setup_enter_pin_again);
            mPinSub.setText("");
            refreshPinDots();
        } else {
            if (pin.equals(mFirstCredential)) {
                finishSetup(PrivacySpaceManager.AUTH_TYPE_PIN, mFirstCredential);
            } else {
                mPinSub.setText(R.string.privacy_setup_pin_mismatch);
                mPinInput.setLength(0);
                refreshPinDots();
                mWaitingForConfirm = false;
                mFirstCredential   = null;
                mPinHint.setText(R.string.privacy_setup_enter_pin);
            }
        }
    }

    // ---- Finish ----

    private void finishSetup(String authType, String credential) {
        PrivacySpaceManager.getInstance(this).setup(authType, credential);
        Toast.makeText(this, R.string.privacy_setup_success, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Allow going back to the auth-type selection step from step 2
        if (mStepPattern.getVisibility() == View.VISIBLE) {
            mStepPattern.setVisibility(View.GONE);
            mStepChoose.setVisibility(View.VISIBLE);
        } else if (mStepPin.getVisibility() == View.VISIBLE) {
            mStepPin.setVisibility(View.GONE);
            mStepChoose.setVisibility(View.VISIBLE);
        } else {
            super.onBackPressed();
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
