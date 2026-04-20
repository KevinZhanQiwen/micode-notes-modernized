/*
 * Copyright (c) 2010-2011, The MiCode.net
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

import java.text.DateFormatSymbols;
import java.util.Calendar;

import net.micode.notes.R;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.NumberPicker;

/**
 * 自定义日期时间选择器（滚动滚轮）
 *
 * 功能：
 * 1. 提供 近7天、小时、分钟、AM/PM 滚动选择
 * 2. 支持 12小时制 / 24小时制 自动切换
 * 3. 小时/分钟/日期 滚动时自动跨天处理
 * 4. 实时回调时间变化
 *
 * 供 DateTimePickerDialog 使用
 */
public class DateTimePicker extends FrameLayout {

    // 默认启用状态
    private static final boolean DEFAULT_ENABLE_STATE = true;

    // 时间常量
    private static final int HOURS_IN_HALF_DAY = 12;
    private static final int HOURS_IN_ALL_DAY = 24;
    private static final int DAYS_IN_ALL_WEEK = 7;

    // 日期滚轮范围
    private static final int DATE_SPINNER_MIN_VAL = 0;
    private static final int DATE_SPINNER_MAX_VAL = DAYS_IN_ALL_WEEK - 1;

    // 24小时制小时范围
    private static final int HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW = 0;
    private static final int HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW = 23;

    // 12小时制小时范围
    private static final int HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW = 1;
    private static final int HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW = 12;

    // 分钟范围
    private static final int MINUT_SPINNER_MIN_VAL = 0;
    private static final int MINUT_SPINNER_MAX_VAL = 59;

    // AM/PM 选择范围
    private static final int AMPM_SPINNER_MIN_VAL = 0;
    private static final int AMPM_SPINNER_MAX_VAL = 1;

    // 四个滚动滚轮
    private final NumberPicker mDateSpinner;    // 日期（近7天）
    private final NumberPicker mHourSpinner;    // 小时
    private final NumberPicker mMinuteSpinner;  // 分钟
    private final NumberPicker mAmPmSpinner;    // 上/下午

    // 日历实例，存储当前选择时间
    private Calendar mDate;

    // 日期显示文字
    private String[] mDateDisplayValues = new String[DAYS_IN_ALL_WEEK];

    // 当前是否是上午
    private boolean mIsAm;

    // 是否使用24小时制
    private boolean mIs24HourView;

    // 控件是否可用
    private boolean mIsEnabled = DEFAULT_ENABLE_STATE;

    // 是否正在初始化（防止初始化时触发回调）
    private boolean mInitialising;

    // 时间变化监听器
    private OnDateTimeChangedListener mOnDateTimeChangedListener;

    // ======================== 滚轮值变化监听器 ========================

    /**
     * 日期滚轮变化：前后调整天数
     */
    private NumberPicker.OnValueChangeListener mOnDateChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            mDate.add(Calendar.DAY_OF_YEAR, newVal - oldVal);
            updateDateControl();
            onDateTimeChanged();
        }
    };

    /**
     * 小时滚轮变化：处理跨日、AM/PM 切换
     */
    private NumberPicker.OnValueChangeListener mOnHourChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            boolean isDateChanged = false;
            Calendar cal = Calendar.getInstance();

            // 12小时制逻辑
            if (!mIs24HourView) {
                // 从 11点 → 12点 跨半天
                if (!mIsAm && oldVal == 11 && newVal == 12) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                } else if (mIsAm && oldVal == 12 && newVal == 11) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }
                // 切换上/下午
                if (oldVal == 11 && newVal == 12 || oldVal == 12 && newVal == 11) {
                    mIsAm = !mIsAm;
                    updateAmPmControl();
                }
            } else {
                // 24小时制：23点 → 0点 跨天
                if (oldVal == 23 && newVal == 0) {
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                } else if (oldVal == 0 && newVal == 23) {
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }
            }

            // 更新小时
            int newHour = mHourSpinner.getValue() % 12 + (mIsAm ? 0 : 12);
            mDate.set(Calendar.HOUR_OF_DAY, newHour);
            onDateTimeChanged();

            // 跨天则更新年月日
            if (isDateChanged) {
                setCurrentYear(cal.get(Calendar.YEAR));
                setCurrentMonth(cal.get(Calendar.MONTH));
                setCurrentDay(cal.get(Calendar.DAY_OF_MONTH));
            }
        }
    };

    /**
     * 分钟滚轮变化：处理跨小时、跨天
     */
    private NumberPicker.OnValueChangeListener mOnMinuteChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            int minValue = mMinuteSpinner.getMinValue();
            int maxValue = mMinuteSpinner.getMaxValue();
            int offset = 0;

            // 从 59 → 0 进一小时
            if (oldVal == maxValue && newVal == minValue) {
                offset += 1;
            } else if (oldVal == minValue && newVal == maxValue) {
                offset -= 1;
            }

            if (offset != 0) {
                mDate.add(Calendar.HOUR_OF_DAY, offset);
                mHourSpinner.setValue(getCurrentHour());
                updateDateControl();

                // 更新 AM/PM
                int newHour = getCurrentHourOfDay();
                mIsAm = newHour < 12;
                updateAmPmControl();
            }

            mDate.set(Calendar.MINUTE, newVal);
            onDateTimeChanged();
        }
    };

    /**
     * AM/PM 切换：直接 ±12小时
     */
    private NumberPicker.OnValueChangeListener mOnAmPmChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            mIsAm = !mIsAm;
            if (mIsAm) {
                mDate.add(Calendar.HOUR_OF_DAY, -12);
            } else {
                mDate.add(Calendar.HOUR_OF_DAY, 12);
            }
            updateAmPmControl();
            onDateTimeChanged();
        }
    };

    // ======================== 时间变化回调接口 ========================
    public interface OnDateTimeChangedListener {
        void onDateTimeChanged(DateTimePicker view, int year, int month,
                               int dayOfMonth, int hourOfDay, int minute);
    }

    // ======================== 构造方法 ========================
    public DateTimePicker(Context context) {
        this(context, System.currentTimeMillis());
    }

    public DateTimePicker(Context context, long date) {
        this(context, date, DateFormat.is24HourFormat(context));
    }

    public DateTimePicker(Context context, long date, boolean is24HourView) {
        super(context);
        mDate = Calendar.getInstance();
        mInitialising = true;
        mIsAm = getCurrentHourOfDay() >= HOURS_IN_HALF_DAY;

        // 加载布局
        inflate(context, R.layout.datetime_picker, this);

        // 初始化日期滚轮
        mDateSpinner = (NumberPicker) findViewById(R.id.date);
        mDateSpinner.setMinValue(DATE_SPINNER_MIN_VAL);
        mDateSpinner.setMaxValue(DATE_SPINNER_MAX_VAL);
        mDateSpinner.setOnValueChangedListener(mOnDateChangedListener);

        // 初始化小时滚轮
        mHourSpinner = (NumberPicker) findViewById(R.id.hour);
        mHourSpinner.setOnValueChangedListener(mOnHourChangedListener);

        // 初始化分钟滚轮
        mMinuteSpinner =  (NumberPicker) findViewById(R.id.minute);
        mMinuteSpinner.setMinValue(MINUT_SPINNER_MIN_VAL);
        mMinuteSpinner.setMaxValue(MINUT_SPINNER_MAX_VAL);
        mMinuteSpinner.setOnLongPressUpdateInterval(100);
        mMinuteSpinner.setOnValueChangedListener(mOnMinuteChangedListener);

        // 初始化 AM/PM 滚轮
        String[] stringsForAmPm = new DateFormatSymbols().getAmPmStrings();
        mAmPmSpinner = (NumberPicker) findViewById(R.id.amPm);
        mAmPmSpinner.setMinValue(AMPM_SPINNER_MIN_VAL);
        mAmPmSpinner.setMaxValue(AMPM_SPINNER_MAX_VAL);
        mAmPmSpinner.setDisplayedValues(stringsForAmPm);
        mAmPmSpinner.setOnValueChangedListener(mOnAmPmChangedListener);

        // 更新控件状态
        updateDateControl();
        updateHourControl();
        updateAmPmControl();

        // 设置24/12小时制
        set24HourView(is24HourView);

        // 设置当前时间
        setCurrentDate(date);
        setEnabled(isEnabled());

        mInitialising = false;
    }

    // ======================== 启用/禁用 ========================
    @Override
    public void setEnabled(boolean enabled) {
        if (mIsEnabled == enabled) return;
        super.setEnabled(enabled);
        mDateSpinner.setEnabled(enabled);
        mMinuteSpinner.setEnabled(enabled);
        mHourSpinner.setEnabled(enabled);
        mAmPmSpinner.setEnabled(enabled);
        mIsEnabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return mIsEnabled;
    }

    // ======================== 日期时间获取/设置 ========================
    public long getCurrentDateInTimeMillis() {
        return mDate.getTimeInMillis();
    }

    public void setCurrentDate(long date) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date);
        setCurrentDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    public void setCurrentDate(int year, int month, int dayOfMonth, int hourOfDay, int minute) {
        setCurrentYear(year);
        setCurrentMonth(month);
        setCurrentDay(dayOfMonth);
        setCurrentHour(hourOfDay);
        setCurrentMinute(minute);
    }

    // 年、月、日、小时、分钟 的 get/set 方法（略去重复注释，保持简洁）
    public int getCurrentYear() { return mDate.get(Calendar.YEAR); }
    public void setCurrentYear(int year) {
        if (!mInitialising && year == getCurrentYear()) return;
        mDate.set(Calendar.YEAR, year);
        updateDateControl();
        onDateTimeChanged();
    }

    public int getCurrentMonth() { return mDate.get(Calendar.MONTH); }
    public void setCurrentMonth(int month) {
        if (!mInitialising && month == getCurrentMonth()) return;
        mDate.set(Calendar.MONTH, month);
        updateDateControl();
        onDateTimeChanged();
    }

    public int getCurrentDay() { return mDate.get(Calendar.DAY_OF_MONTH); }
    public void setCurrentDay(int dayOfMonth) {
        if (!mInitialising && dayOfMonth == getCurrentDay()) return;
        mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        updateDateControl();
        onDateTimeChanged();
    }

    public int getCurrentHourOfDay() { return mDate.get(Calendar.HOUR_OF_DAY); }

    private int getCurrentHour() {
        if (mIs24HourView) return getCurrentHourOfDay();
        int hour = getCurrentHourOfDay();
        return hour > 12 ? hour - 12 : (hour == 0 ? 12 : hour);
    }

    public void setCurrentHour(int hourOfDay) {
        if (!mInitialising && hourOfDay == getCurrentHourOfDay()) return;
        mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);

        if (!mIs24HourView) {
            mIsAm = hourOfDay < 12;
            hourOfDay = getCurrentHour();
            updateAmPmControl();
        }
        mHourSpinner.setValue(hourOfDay);
        onDateTimeChanged();
    }

    public int getCurrentMinute() { return mDate.get(Calendar.MINUTE); }

    public void setCurrentMinute(int minute) {
        if (!mInitialising && minute == getCurrentMinute()) return;
        mMinuteSpinner.setValue(minute);
        mDate.set(Calendar.MINUTE, minute);
        onDateTimeChanged();
    }

    // ======================== 12/24小时制切换 ========================
    public boolean is24HourView() { return mIs24HourView; }

    public void set24HourView(boolean is24HourView) {
        if (mIs24HourView == is24HourView) return;
        mIs24HourView = is24HourView;
        mAmPmSpinner.setVisibility(is24HourView ? View.GONE : View.VISIBLE);
        updateHourControl();
        setCurrentHour(getCurrentHourOfDay());
        updateAmPmControl();
    }

    // ======================== 控件状态更新 ========================

    /**
     * 更新日期滚轮显示：显示近7天（格式：月.日 星期）
     */
    private void updateDateControl() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mDate.getTimeInMillis());
        cal.add(Calendar.DAY_OF_YEAR, -DAYS_IN_ALL_WEEK / 2 - 1);

        mDateSpinner.setDisplayedValues(null);
        for (int i = 0; i < DAYS_IN_ALL_WEEK; ++i) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            mDateDisplayValues[i] = (String) DateFormat.format("MM.dd EEEE", cal);
        }

        mDateSpinner.setDisplayedValues(mDateDisplayValues);
        mDateSpinner.setValue(DAYS_IN_ALL_WEEK / 2);
        mDateSpinner.invalidate();
    }

    /**
     * 更新 AM/PM 显示
     */
    private void updateAmPmControl() {
        if (mIs24HourView) {
            mAmPmSpinner.setVisibility(View.GONE);
        } else {
            mAmPmSpinner.setValue(mIsAm ? Calendar.AM : Calendar.PM);
            mAmPmSpinner.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 更新小时滚轮范围（12/24小时制）
     */
    private void updateHourControl() {
        if (mIs24HourView) {
            mHourSpinner.setMinValue(0);
            mHourSpinner.setMaxValue(23);
        } else {
            mHourSpinner.setMinValue(1);
            mHourSpinner.setMaxValue(12);
        }
    }

    // ======================== 回调监听 ========================
    public void setOnDateTimeChangedListener(OnDateTimeChangedListener callback) {
        mOnDateTimeChangedListener = callback;
    }

    private void onDateTimeChanged() {
        if (mOnDateTimeChangedListener != null) {
            mOnDateTimeChangedListener.onDateTimeChanged(
                    this, getCurrentYear(), getCurrentMonth(),
                    getCurrentDay(), getCurrentHourOfDay(), getCurrentMinute());
        }
    }
}