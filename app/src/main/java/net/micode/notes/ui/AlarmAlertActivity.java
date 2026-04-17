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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;

import java.io.IOException;

/**
 * 笔记提醒弹窗界面
 *
 * 功能：
 * 1. 在锁屏/黑屏状态下点亮屏幕并显示弹窗
 * 2. 播放系统默认闹钟铃声
 * 3. 显示笔记预览内容
 * 4. 提供【确定】和【进入编辑】按钮
 * 5. 关闭弹窗时停止铃声
 */
public class AlarmAlertActivity extends Activity
        implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {

    // 提醒对应的笔记ID
    private long mNoteId;
    // 笔记预览内容
    private String mSnippet;
    // 预览内容最大长度
    private static final int SNIPPET_PREW_MAX_LEN = 60;
    // 媒体播放器，播放提醒铃声
    MediaPlayer mPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 无标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        final Window window = getWindow();

        // 让弹窗在锁屏界面上显示
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        // 如果屏幕关闭，点亮屏幕并保持常亮
        if (!isScreenOn()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);
        }

        Intent intent = getIntent();
        try {
            // 从 Uri 中解析出笔记 ID
            mNoteId = Long.valueOf(intent.getData().getPathSegments().get(1));
            // 从数据库获取笔记预览内容
            mSnippet = DataUtils.getSnippetById(getContentResolver(), mNoteId);
            // 预览内容过长则截取并加省略号
            if (mSnippet.length() > SNIPPET_PREW_MAX_LEN) {
                mSnippet = mSnippet.substring(0, SNIPPET_PREW_MAX_LEN)
                        + getResources().getString(R.string.notelist_string_info);
            }
        } catch (Exception e) {
            e.printStackTrace();
            finish();
            return;
        }

        // 初始化播放器
        mPlayer = new MediaPlayer();

        // 检查笔记是否有效
        if (DataUtils.visibleInNoteDatabase(getContentResolver(), mNoteId, Notes.TYPE_NOTE)) {
            // 显示提醒对话框
            showActionDialog();
            // 播放提醒铃声
            playAlarmSound();
        } else {
            // 笔记无效，直接关闭
            finish();
        }
    }

    /**
     * 判断屏幕是否处于点亮状态
     */
    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isScreenOn();
    }

    /**
     * 播放系统默认闹钟铃声
     */
    private void playAlarmSound() {
        // 获取系统默认闹钟铃声
        Uri alarmUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);

        // 设置音频流类型为闹钟
        mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);

        try {
            mPlayer.setDataSource(this, alarmUri);
            mPlayer.prepare();
            mPlayer.setLooping(true); // 循环播放
            mPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 显示提醒对话框
     */
    private void showActionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.app_name);
        builder.setMessage(mSnippet); // 显示笔记内容

        // 确定按钮
        builder.setPositiveButton(R.string.notealert_ok, this);

        // 如果屏幕已亮，显示【进入】按钮
        if (isScreenOn()) {
            builder.setNegativeButton(R.string.notealert_enter, this);
        }

        // 显示并监听关闭事件
        builder.show().setOnDismissListener(this);
    }

    /**
     * 对话框按钮点击事件
     */
    @Override
    public void onClick(DialogInterface dialog, int which) {
        // 点击【进入】：跳转到笔记编辑页面
        if (which == DialogInterface.BUTTON_NEGATIVE) {
            Intent intent = new Intent(this, NoteEditActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.putExtra(Intent.EXTRA_UID, mNoteId);
            startActivity(intent);
        }
    }

    /**
     * 对话框消失时：停止铃声并关闭页面
     */
    @Override
    public void onDismiss(DialogInterface dialog) {
        stopAlarmSound();
        finish();
    }

    /**
     * 停止并释放铃声播放器
     */
    private void stopAlarmSound() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }
}