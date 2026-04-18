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

package net.micode.notes.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;
import android.widget.RemoteViews;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NoteEditActivity;
import net.micode.notes.ui.NotesListActivity;

/**
 * 桌面小部件抽象基类
 *
 * 职责：
 * 1. 提供桌面小部件的通用逻辑（查询、更新、删除）
 * 2. 子类只需实现布局和背景资源的差异
 *
 * 设计模式：模板方法模式（Template Method Pattern）
 * - 基类定义通用流程（update）
 * - 子类实现差异部分（getLayoutId、getBgResourceId、getWidgetType）
 *
 * 与系统的交互：
 * - 通过 AppWidgetProvider 接收系统广播
 * - 通过 RemoteViews 更新小部件界面（跨进程）
 * - 通过 PendingIntent 设置点击行为
 *
 * 数据关联：
 * - 笔记与 Widget 的关联存储在 note 表的 WIDGET_ID 和 WIDGET_TYPE 字段
 * - 从小部件创建笔记时自动关联
 * - 小部件被删除时自动清空关联
 */
public abstract class NoteWidgetProvider extends AppWidgetProvider {

    /**
     * 查询小部件关联笔记的投影列
     * 只需要 ID、背景色和摘要（用于显示）
     */
    public static final String [] PROJECTION = new String [] {
            NoteColumns.ID,           // 笔记 ID
            NoteColumns.BG_COLOR_ID,  // 背景颜色 ID
            NoteColumns.SNIPPET       // 笔记摘要（预览内容）
    };

    /** 投影列索引：笔记 ID */
    public static final int COLUMN_ID           = 0;

    /** 投影列索引：背景颜色 ID */
    public static final int COLUMN_BG_COLOR_ID  = 1;

    /** 投影列索引：笔记摘要 */
    public static final int COLUMN_SNIPPET      = 2;

    private static final String TAG = "NoteWidgetProvider";

    /**
     * 小部件被删除时的回调
     *
     * 功能：清空数据库中关联的 WIDGET_ID 字段
     * 设计意图：确保删除小部件后，对应的笔记不再关联无效的 widgetId
     *
     * @param context 上下文
     * @param appWidgetIds 被删除的小部件 ID 数组
     */
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        ContentValues values = new ContentValues();
        // 将 WIDGET_ID 设置为无效值（-1 或 0 表示无关联）
        values.put(NoteColumns.WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        for (int i = 0; i < appWidgetIds.length; i++) {
            // 更新所有关联了此 widgetId 的笔记
            context.getContentResolver().update(
                    Notes.CONTENT_NOTE_URI,
                    values,
                    NoteColumns.WIDGET_ID + "=?",
                    new String[] { String.valueOf(appWidgetIds[i]) });
        }
    }

    /**
     * 查询小部件关联的笔记信息
     *
     * @param context 上下文
     * @param widgetId 小部件 ID
     * @return Cursor 包含笔记信息，如果没有关联笔记则返回空
     */
    private Cursor getNoteWidgetInfo(Context context, int widgetId) {
        return context.getContentResolver().query(
                Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.WIDGET_ID + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[] { String.valueOf(widgetId), String.valueOf(Notes.ID_TRASH_FOLER) },
                null);
    }

    /**
     * 更新小部件（公开方法，供子类调用）
     * 非隐私模式调用此方法
     *
     * @param context 上下文
     * @param appWidgetManager AppWidgetManager 实例
     * @param appWidgetIds 要更新的小部件 ID 数组
     */
    protected void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        update(context, appWidgetManager, appWidgetIds, false);
    }

    /**
     * 更新小部件（私有核心方法）
     *
     * 核心逻辑：
     * 1. 查询该 widgetId 关联的笔记
     * 2. 有关联笔记 → 显示笔记摘要，点击打开笔记
     * 3. 无关联笔记 → 显示提示文字，点击创建新笔记
     *
     * @param context 上下文
     * @param appWidgetManager AppWidgetManager 实例
     * @param appWidgetIds 要更新的小部件 ID 数组
     * @param privacyMode 隐私模式：true 时隐藏内容，只显示"隐私模式下无法查看"
     */
    private void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds,
                        boolean privacyMode) {
        for (int i = 0; i < appWidgetIds.length; i++) {
            // 跳过无效的小部件 ID
            if (appWidgetIds[i] != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // 默认值（无关联笔记时使用）
                int bgId = ResourceParser.getDefaultBgId(context);  // 默认背景色
                String snippet = "";  // 摘要内容

                // 构建 Intent（点击小部件时启动）
                Intent intent = new Intent(context, NoteEditActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_ID, appWidgetIds[i]);
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_TYPE, getWidgetType());

                // ========== 查询关联的笔记 ==========
                Cursor c = getNoteWidgetInfo(context, appWidgetIds[i]);
                if (c != null && c.moveToFirst()) {
                    // 安全检查：同一个 widgetId 不应该关联多条笔记
                    if (c.getCount() > 1) {
                        Log.e(TAG, "Multiple message with same widget id:" + appWidgetIds[i]);
                        c.close();
                        return;
                    }
                    // 从数据库读取笔记信息
                    snippet = c.getString(COLUMN_SNIPPET);
                    bgId = c.getInt(COLUMN_BG_COLOR_ID);
                    intent.putExtra(Intent.EXTRA_UID, c.getLong(COLUMN_ID));  // 笔记 ID
                    intent.setAction(Intent.ACTION_VIEW);  // 查看模式
                } else {
                    // 无关联笔记：显示提示，点击后新建笔记
                    snippet = context.getResources().getString(R.string.widget_havenot_content);
                    intent.setAction(Intent.ACTION_INSERT_OR_EDIT);  // 新建/编辑模式
                }

                if (c != null) {
                    c.close();  // 确保关闭游标
                }

                // ========== 构建 RemoteViews（小部件界面） ==========
                RemoteViews rv = new RemoteViews(context.getPackageName(), getLayoutId());
                // 设置背景图片（根据颜色 ID 选择对应资源）
                rv.setImageViewResource(R.id.widget_bg_image, getBgResourceId(bgId));
                intent.putExtra(Notes.INTENT_EXTRA_BACKGROUND_ID, bgId);

                // ========== 创建 PendingIntent（点击响应） ==========
                PendingIntent pendingIntent = null;
                if (privacyMode) {
                    // 隐私模式：不显示内容，点击跳转到笔记列表页
                    rv.setTextViewText(R.id.widget_text,
                            context.getString(R.string.widget_under_visit_mode));
                    pendingIntent = PendingIntent.getActivity(
                            context,
                            appWidgetIds[i],
                            new Intent(context, NotesListActivity.class),
                            PendingIntent.FLAG_UPDATE_CURRENT);
                } else {
                    // 正常模式：显示摘要，点击跳转到对应操作
                    rv.setTextViewText(R.id.widget_text, snippet);
                    pendingIntent = PendingIntent.getActivity(
                            context,
                            appWidgetIds[i],
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                }

                // 设置点击 TextView 时触发的 PendingIntent
                rv.setOnClickPendingIntent(R.id.widget_text, pendingIntent);

                // 通知系统更新小部件
                appWidgetManager.updateAppWidget(appWidgetIds[i], rv);
            }
        }
    }

    // ==================== 抽象方法（子类实现） ====================

    /**
     * 获取背景资源 ID
     * 子类需要根据小部件尺寸返回对应的背景资源
     *
     * @param bgId 背景颜色 ID（0-4）
     * @return 对应的 drawable 资源 ID
     */
    protected abstract int getBgResourceId(int bgId);

    /**
     * 获取小部件布局文件 ID
     * 子类返回对应尺寸的布局
     *
     * @return layout 资源 ID
     */
    protected abstract int getLayoutId();

    /**
     * 获取小部件类型
     *
     * @return TYPE_WIDGET_2X (0) 或 TYPE_WIDGET_4X (1)
     */
    protected abstract int getWidgetType();
}