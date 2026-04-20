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

import java.util.Calendar;

import net.micode.notes.R;
import net.micode.notes.ui.DateTimePicker;
import net.micode.notes.ui.DateTimePicker.OnDateTimeChangedListener;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

/**
 * 日期时间选择弹窗
 *
 * 功能：
 * 1. 提供 日期 + 时间 联合选择
 * 2. 实时更新弹窗标题显示当前选择的时间
 * 3. 支持 12/24 小时制自动切换
 * 4. 通过回调接口返回选择的时间戳
 *
 * 用途：NoteEditActivity 中设置笔记提醒时间
 */
public class DateTimePickerDialog extends AlertDialog implements OnClickListener {

    // 日历对象，用于存储和计算选择的时间
    private Calendar mDate;
    // 是否使用24小时制
    private boolean mIs24HourView;
    // 时间设置完成监听器
    private OnDateTimeSetListener mOnDateTimeSetListener;
    // 自定义日期时间选择器控件
    private DateTimePicker mDateTimePicker;

    /**
     * 时间选择完成回调接口
     * 当用户点击确定时，返回选择的时间戳
     */
    public interface OnDateTimeSetListener {
        void OnDateTimeSet(AlertDialog dialog, long date);
    }

    /**
     * 构造方法
     * @param context 上下文
     * @param date 初始化显示的时间（毫秒）
     */
    public DateTimePickerDialog(Context context, long date) {
        super(context);
        mDate = Calendar.getInstance();

        // 初始化自定义日期时间选择器，并设置为弹窗内容
        mDateTimePicker = new DateTimePicker(context);
        setView(mDateTimePicker);

        // 监听选择器时间变化，实时更新日历对象和标题
        mDateTimePicker.setOnDateTimeChangedListener(new OnDateTimeChangedListener() {
            @Override
            public void onDateTimeChanged(DateTimePicker view, int year, int month,
                                          int dayOfMonth, int hourOfDay, int minute) {
                // 更新日历时间
                mDate.set(Calendar.YEAR, year);
                mDate.set(Calendar.MONTH, month);
                mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                mDate.set(Calendar.MINUTE, minute);
                // 更新弹窗标题显示当前时间
                updateTitle(mDate.getTimeInMillis());
            }
        });

        // 设置初始时间，并将秒数置为0
        mDate.setTimeInMillis(date);
        mDate.set(Calendar.SECOND, 0);
        mDateTimePicker.setCurrentDate(mDate.getTimeInMillis());

        // 设置确定/取消按钮
        setButton(context.getString(R.string.datetime_dialog_ok), this);
        setButton2(context.getString(R.string.datetime_dialog_cancel), (OnClickListener) null);

        // 根据系统设置自动切换24/12小时制
        set24HourView(DateFormat.is24HourFormat(this.getContext()));
        // 初始化标题
        updateTitle(mDate.getTimeInMillis());
    }

    /**
     * 设置是否使用24小时制
     */
    public void set24HourView(boolean is24HourView) {
        mIs24HourView = is24HourView;
    }

    /**
     * 设置时间选择完成监听器
     */
    public void setOnDateTimeSetListener(OnDateTimeSetListener callBack) {
        mOnDateTimeSetListener = callBack;
    }

    /**
     * 更新弹窗标题，显示当前选择的日期时间
     */
    private void updateTitle(long date) {
        int flag = DateUtils.FORMAT_SHOW_YEAR     // 显示年份
                | DateUtils.FORMAT_SHOW_DATE     // 显示日期
                | DateUtils.FORMAT_SHOW_TIME;     // 显示时间

        // 控制时间格式（24小时制）
        flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : DateUtils.FORMAT_12HOUR;

        // 格式化时间并设置为标题
        setTitle(DateUtils.formatDateTime(this.getContext(), date, flag));
    }

    /**
     * 按钮点击事件
     * 点击确定时，回调返回选择的时间戳
     */
    public void onClick(DialogInterface arg0, int arg1) {
        if (mOnDateTimeSetListener != null) {
            mOnDateTimeSetListener.OnDateTimeSet(this, mDate.getTimeInMillis());
        }
    }
}