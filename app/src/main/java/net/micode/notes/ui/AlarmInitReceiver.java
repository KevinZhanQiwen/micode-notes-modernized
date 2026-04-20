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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 开机/应用重装后初始化提醒的广播接收器
 *
 * 作用：
 * 1. 接收系统广播（如开机完成 BOOT_COMPLETED）
 * 2. 从数据库查询所有【未过期的笔记提醒】
 * 3. 重新向 AlarmManager 注册这些闹钟
 * 4. 保证重启手机后，笔记提醒不会丢失
 *
 * 这是闹钟功能“持久化”的关键
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    // 查询需要的字段：笔记ID、提醒时间
    private static final String [] PROJECTION = new String [] {
            NoteColumns.ID,
            NoteColumns.ALERTED_DATE
    };

    // 字段下标
    private static final int COLUMN_ID           = 0;
    private static final int COLUMN_ALERTED_DATE = 1;

    /**
     * 收到广播时执行（开机启动）
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 当前时间，只查询 > 当前时间的提醒
        long currentDate = System.currentTimeMillis();

        // 查询数据库：所有未过期的笔记类型提醒
        Cursor cursor = context.getContentResolver().query(
                Notes.CONTENT_NOTE_URI,
                PROJECTION,
                // 条件：提醒时间 > 当前时间 + 类型是笔记
                NoteColumns.ALERTED_DATE + ">? AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE,
                new String[] { String.valueOf(currentDate) },
                null
        );

        if (cursor != null) {
            // 遍历所有有效提醒，重新设置闹钟
            if (cursor.moveToFirst()) {
                do {
                    // 提醒时间
                    long alertDate = cursor.getLong(COLUMN_ALERTED_DATE);

                    // 构建广播意图（触发 AlarmReceiver）
                    Intent sender = new Intent(context, AlarmReceiver.class);
                    // 携带笔记ID
                    sender.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, cursor.getLong(COLUMN_ID)));

                    // 创建延迟广播
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, sender, 0);

                    // 获取系统闹钟服务
                    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

                    // 设置闹钟：RTC_WAKEUP 唤醒CPU，在 alertDate 时间触发
                    alarmManager.set(AlarmManager.RTC_WAKEUP, alertDate, pendingIntent);

                } while (cursor.moveToNext());
            }
            cursor.close();
        }
    }
}