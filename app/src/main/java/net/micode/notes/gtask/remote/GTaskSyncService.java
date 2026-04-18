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

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

/**
 * Google Tasks 同步服务
 *
 * 功能：
 * 1. 作为同步的入口 Service，对外提供静态 API
 * 2. 管理 GTaskASyncTask 生命周期
 * 3. 通过广播通知 UI 层同步状态变化
 *
 * 使用方式：
 * - 开始同步：GTaskSyncService.startSync(activity)
 * - 取消同步：GTaskSyncService.cancelSync(context)
 * - 查询状态：GTaskSyncService.isSyncing()
 *
 * 广播：
 * - 名称：net.micode.notes.gtask.remote.gtask_sync_service
 * - 携带字段：isSyncing（是否正在同步）、progressMsg（进度消息）
 *
 * 设计模式：Service 封装 + 静态 API
 */
public class GTaskSyncService extends Service {

    // ==================== Intent 常量 ====================
    /** Intent 中操作类型的键名 */
    public final static String ACTION_STRING_NAME = "sync_action_type";

    /** 操作类型：开始同步 */
    public final static int ACTION_START_SYNC = 0;

    /** 操作类型：取消同步 */
    public final static int ACTION_CANCEL_SYNC = 1;

    /** 操作类型：无效 */
    public final static int ACTION_INVALID = 2;

    // ==================== 广播常量 ====================
    /** 广播 Action 名称 */
    public final static String GTASK_SERVICE_BROADCAST_NAME =
            "net.micode.notes.gtask.remote.gtask_sync_service";

    /** 广播中是否正在同步的字段名 */
    public final static String GTASK_SERVICE_BROADCAST_IS_SYNCING = "isSyncing";

    /** 广播中进度消息的字段名 */
    public final static String GTASK_SERVICE_BROADCAST_PROGRESS_MSG = "progressMsg";

    /** 当前正在执行的同步任务（静态，整个应用只有一个） */
    private static GTaskASyncTask mSyncTask = null;

    /** 当前同步进度消息（用于 getProgressString） */
    private static String mSyncProgress = "";

    /**
     * 开始同步
     *
     * @param activity 用于账号认证的 Activity
     */
    private void startSync() {
        if (mSyncTask == null) {
            mSyncTask = new GTaskASyncTask(this, new GTaskASyncTask.OnCompleteListener() {
                public void onComplete() {
                    // 同步完成，清理资源
                    mSyncTask = null;
                    sendBroadcast("");
                    stopSelf();  // 停止服务
                }
            });
            sendBroadcast("");
            mSyncTask.execute();  // 启动异步任务
        }
    }

    /**
     * 取消同步
     */
    private void cancelSync() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    @Override
    public void onCreate() {
        mSyncTask = null;
    }

    /**
     * 处理启动服务的 Intent
     *
     * @param intent 包含操作类型的 Intent
     * @param flags 启动标志
     * @param startId 启动 ID
     * @return START_STICKY 表示服务被杀后会重启
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle bundle = intent.getExtras();
        if (bundle != null && bundle.containsKey(ACTION_STRING_NAME)) {
            switch (bundle.getInt(ACTION_STRING_NAME, ACTION_INVALID)) {
                case ACTION_START_SYNC:
                    startSync();
                    break;
                case ACTION_CANCEL_SYNC:
                    cancelSync();
                    break;
                default:
                    break;
            }
            return START_STICKY;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * 低内存时自动取消同步
     */
    @Override
    public void onLowMemory() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;  // 不支持绑定
    }

    /**
     * 发送广播通知 UI 层
     *
     * @param msg 进度消息
     */
    public void sendBroadcast(String msg) {
        mSyncProgress = msg;
        Intent intent = new Intent(GTASK_SERVICE_BROADCAST_NAME);
        intent.putExtra(GTASK_SERVICE_BROADCAST_IS_SYNCING, mSyncTask != null);
        intent.putExtra(GTASK_SERVICE_BROADCAST_PROGRESS_MSG, msg);
        sendBroadcast(intent);
    }

    // ==================== 静态 API ====================

    /**
     * 开始同步（对外 API）
     *
     * @param activity 用于账号认证的 Activity
     */
    public static void startSync(Activity activity) {
        GTaskManager.getInstance().setActivityContext(activity);
        Intent intent = new Intent(activity, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_START_SYNC);
        activity.startService(intent);
    }

    /**
     * 取消同步（对外 API）
     *
     * @param context 上下文
     */
    public static void cancelSync(Context context) {
        Intent intent = new Intent(context, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_CANCEL_SYNC);
        context.startService(intent);
    }

    /**
     * 查询是否正在同步
     *
     * @return true 表示正在同步
     */
    public static boolean isSyncing() {
        return mSyncTask != null;
    }

    /**
     * 获取当前同步进度消息
     *
     * @return 进度消息文本
     */
    public static String getProgressString() {
        return mSyncProgress;
    }
}