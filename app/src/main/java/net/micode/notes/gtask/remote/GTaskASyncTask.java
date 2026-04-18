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

package net.micode.notes.gtask.remote;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import net.micode.notes.R;
import net.micode.notes.ui.NotesListActivity;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * Google Tasks 异步同步任务
 *
 * 功能：
 * 1. 在后台线程执行同步操作（避免阻塞 UI）
 * 2. 通过通知栏和广播实时反馈同步进度
 * 3. 处理同步完成后的结果通知
 *
 * 继承关系：
 * AsyncTask<Void, String, Integer>
 * - 参数类型：Void（不需要输入参数）
 * - 进度类型：String（进度消息文本）
 * - 结果类型：Integer（状态码）
 *
 * 使用流程：
 * 1. GTaskSyncService 创建实例
 * 2. 调用 execute() 启动同步
 * 3. doInBackground() 执行 GTaskManager.sync()
 * 4. onProgressUpdate() 更新通知栏
 * 5. onPostExecute() 显示结果并回调完成监听器
 *
 * 设计模式：观察者模式（OnCompleteListener）
 */
public class GTaskASyncTask extends AsyncTask<Void, String, Integer> {

    /** 同步通知的唯一 ID（用于更新/取消通知） */
    private static int GTASK_SYNC_NOTIFICATION_ID = 5234235;

    /**
     * 同步完成监听器接口
     * 用于通知调用方同步已完成
     */
    public interface OnCompleteListener {
        void onComplete();
    }

    private Context mContext;

    /** 通知管理器，用于显示同步进度和结果 */
    private NotificationManager mNotifiManager;

    /** 同步管理器（单例） */
    private GTaskManager mTaskManager;

    /** 完成监听器 */
    private OnCompleteListener mOnCompleteListener;

    public GTaskASyncTask(Context context, OnCompleteListener listener) {
        mContext = context;
        mOnCompleteListener = listener;
        mNotifiManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        mTaskManager = GTaskManager.getInstance();
    }

    /**
     * 取消同步
     * 供外部调用（如用户点击取消按钮）
     */
    public void cancelSync() {
        mTaskManager.cancelSync();
    }

    /**
     * 发布进度（供 GTaskManager 调用）
     *
     * @param message 进度消息
     */
    public void publishProgess(String message) {
        publishProgress(new String[] { message });
    }

    /**
     * 显示通知
     *
     * @param tickerId 状态栏提示文字的资源 ID
     * @param content 通知内容文字
     */
    private void showNotification(int tickerId, String content) {
        // 1. 获取 PendingIntent（点击通知后的跳转）
        PendingIntent pendingIntent;
        Intent intent;
        if (tickerId != R.string.ticker_success) {
            // 失败或进行中：跳转到设置页
            intent = new Intent(mContext, NotesPreferenceActivity.class);
        } else {
            // 成功：跳转到笔记列表页
            intent = new Intent(mContext, NotesListActivity.class);
        }

        // 注意：Android 12 (API 31) 及以上必须显式指定 FLAG_IMMUTABLE 或 FLAG_MUTABLE
        pendingIntent = PendingIntent.getActivity(mContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 2. 创建通知渠道（Android 8.0/API 26+ 强制要求）
        String channelId = "gtask_sync";
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId, "Sync Notification", NotificationManager.IMPORTANCE_LOW);
            mNotifiManager.createNotificationChannel(channel);
        }

        // 3. 构建通知
        Notification.Builder builder;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder = new Notification.Builder(mContext, channelId);
        } else {
            builder = new Notification.Builder(mContext);
        }

        builder.setContentTitle(mContext.getString(R.string.app_name))
                .setContentText(content)
                .setSmallIcon(R.drawable.notification) // 确保此资源存在
                .setTicker(mContext.getString(tickerId))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_LIGHTS);

        // 4. 发送通知
        mNotifiManager.notify(GTASK_SYNC_NOTIFICATION_ID, builder.build());
    }

    /**
     * 后台执行同步（在子线程中运行）
     *
     * @param unused 无参数
     * @return 同步结果状态码
     */
    @Override
    protected Integer doInBackground(Void... unused) {
        // 发布登录进度
        publishProgess(mContext.getString(R.string.sync_progress_login,
                NotesPreferenceActivity.getSyncAccountName(mContext)));
        // 执行同步
        return mTaskManager.sync(mContext, this);
    }

    /**
     * 进度更新时回调（在 UI 线程中运行）
     *
     * @param progress 进度消息数组
     */
    @Override
    protected void onProgressUpdate(String... progress) {
        // 更新通知栏
        showNotification(R.string.ticker_syncing, progress[0]);
        // 如果当前 Context 是 GTaskSyncService，发送广播通知 UI
        if (mContext instanceof GTaskSyncService) {
            ((GTaskSyncService) mContext).sendBroadcast(progress[0]);
        }
    }

    /**
     * 同步完成时回调（在 UI 线程中运行）
     *
     * @param result 同步结果状态码
     */
    @Override
    protected void onPostExecute(Integer result) {
        if (result == GTaskManager.STATE_SUCCESS) {
            // 同步成功
            showNotification(R.string.ticker_success, mContext.getString(
                    R.string.success_sync_account, mTaskManager.getSyncAccount()));
            NotesPreferenceActivity.setLastSyncTime(mContext, System.currentTimeMillis());
        } else if (result == GTaskManager.STATE_NETWORK_ERROR) {
            // 网络错误
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_network));
        } else if (result == GTaskManager.STATE_INTERNAL_ERROR) {
            // 内部错误
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_internal));
        } else if (result == GTaskManager.STATE_SYNC_CANCELLED) {
            // 同步被取消
            showNotification(R.string.ticker_cancel, mContext
                    .getString(R.string.error_sync_cancelled));
        }

        // 触发完成回调（在新线程中执行，避免阻塞 UI）
        if (mOnCompleteListener != null) {
            new Thread(new Runnable() {
                public void run() {
                    mOnCompleteListener.onComplete();
                }
            }).start();
        }
    }
}