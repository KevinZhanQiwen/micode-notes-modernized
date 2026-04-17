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

package net.micode.notes.model;

import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.tool.ResourceParser.NoteBgResources;

/**
 * 正在编辑的笔记业务模型
 *
 * 这是 UI 层和数据层之间的桥梁，负责：
 * 1. 管理当前正在编辑的笔记的所有状态
 * 2. 提供业务方法（保存、加载、修改属性）
 * 3. 通过监听器回调 UI 层更新界面
 *
 * 设计模式：
 * - 工厂方法：createEmptyNote() / load() 统一对象创建
 * - 观察者模式：NoteSettingChangedListener 通知 UI 更新
 * - 组合模式：组合 Note 类执行底层数据操作
 *
 * 与 Note 的关系：
 * - WorkingNote 是业务模型（上层），Note 是数据操作类（下层）
 * - WorkingNote 持有 Note 实例，将数据操作委托给它
 *
 * 与 NoteEditActivity 的关系：
 * - Activity 创建 WorkingNote 实例，实现 NoteSettingChangedListener
 * - 用户操作时调用 WorkingNote 的 setter 方法，setter 更新 Note 并触发回调
 */
public class WorkingNote {
    // ==================== 数据字段 ====================
    /** 底层数据操作对象 */
    private Note mNote;

    /** 笔记 ID（0 表示新建，未持久化） */
    private long mNoteId;

    /** 笔记内容 */
    private String mContent;

    /** 清单模式：0=普通文本，1=清单模式 */
    private int mMode;

    /** 提醒时间戳（毫秒），0 表示无提醒 */
    private long mAlertDate;

    /** 最后修改时间戳 */
    private long mModifiedDate;

    /** 背景颜色 ID：0=黄，1=蓝，2=白，3=绿，4=红 */
    private int mBgColorId;

    /** 关联的桌面小部件 ID，INVALID_APPWIDGET_ID 表示无关联 */
    private int mWidgetId;

    /** 小部件类型：0=2x2，1=4x4，-1=无效 */
    private int mWidgetType;

    /** 所属文件夹 ID */
    private long mFolderId;

    /** 上下文 */
    private Context mContext;

    private static final String TAG = "WorkingNote";

    /** 是否已标记删除 */
    private boolean mIsDeleted;

    /** UI 层监听器，用于回调界面更新 */
    private NoteSettingChangedListener mNoteSettingStatusListener;

    // ==================== 数据库投影列 ====================
    /**
     * data 表的查询投影列
     * 列索引对应：
     * 0: _id, 1: content, 2: mime_type, 3: data1, 4: data2, 5: data3, 6: data4
     */
    public static final String[] DATA_PROJECTION = new String[] {
            DataColumns.ID,
            DataColumns.CONTENT,
            DataColumns.MIME_TYPE,
            DataColumns.DATA1,
            DataColumns.DATA2,
            DataColumns.DATA3,
            DataColumns.DATA4,
    };

    /**
     * note 表的查询投影列
     * 列索引对应：
     * 0: parent_id, 1: alert_date, 2: bg_color_id, 3: widget_id, 4: widget_type, 5: modified_date
     */
    public static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.PARENT_ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.WIDGET_ID,
            NoteColumns.WIDGET_TYPE,
            NoteColumns.MODIFIED_DATE
    };

    // ==================== 列索引常量 ====================
    private static final int DATA_ID_COLUMN = 0;
    private static final int DATA_CONTENT_COLUMN = 1;
    private static final int DATA_MIME_TYPE_COLUMN = 2;
    private static final int DATA_MODE_COLUMN = 3;

    private static final int NOTE_PARENT_ID_COLUMN = 0;
    private static final int NOTE_ALERTED_DATE_COLUMN = 1;
    private static final int NOTE_BG_COLOR_ID_COLUMN = 2;
    private static final int NOTE_WIDGET_ID_COLUMN = 3;
    private static final int NOTE_WIDGET_TYPE_COLUMN = 4;
    private static final int NOTE_MODIFIED_DATE_COLUMN = 5;

    // ==================== 构造方法（私有） ====================
    /**
     * 新建笔记的构造方法
     * @param context 上下文
     * @param folderId 目标文件夹 ID
     */
    private WorkingNote(Context context, long folderId) {
        mContext = context;
        mAlertDate = 0;
        mModifiedDate = System.currentTimeMillis();
        mFolderId = folderId;
        mNote = new Note();      // 创建底层数据对象
        mNoteId = 0;             // 0 表示未持久化
        mIsDeleted = false;
        mMode = 0;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;
    }

    /**
     * 加载已有笔记的构造方法
     * @param context 上下文
     * @param noteId  笔记 ID
     * @param folderId 文件夹 ID（传入但未使用，实际从数据库读取）
     */
    private WorkingNote(Context context, long noteId, long folderId) {
        mContext = context;
        mNoteId = noteId;
        mFolderId = folderId;
        mIsDeleted = false;
        mNote = new Note();
        loadNote();     // 从数据库加载数据
    }

    // ==================== 数据库加载方法 ====================
    /**
     * 从 note 表加载笔记元数据
     */
    private void loadNote() {
        Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mNoteId),
                NOTE_PROJECTION, null, null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                mFolderId = cursor.getLong(NOTE_PARENT_ID_COLUMN);
                mBgColorId = cursor.getInt(NOTE_BG_COLOR_ID_COLUMN);
                mWidgetId = cursor.getInt(NOTE_WIDGET_ID_COLUMN);
                mWidgetType = cursor.getInt(NOTE_WIDGET_TYPE_COLUMN);
                mAlertDate = cursor.getLong(NOTE_ALERTED_DATE_COLUMN);
                mModifiedDate = cursor.getLong(NOTE_MODIFIED_DATE_COLUMN);
            }
            cursor.close();
        } else {
            Log.e(TAG, "No note with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note with id " + mNoteId);
        }
        loadNoteData();  // 加载 data 表中的内容
    }

    /**
     * 从 data 表加载笔记内容
     * 支持文本笔记和通话记录两种类型
     */
    private void loadNoteData() {
        Cursor cursor = mContext.getContentResolver().query(
                Notes.CONTENT_DATA_URI,
                DATA_PROJECTION,
                DataColumns.NOTE_ID + "=?",
                new String[] { String.valueOf(mNoteId) },
                null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(DATA_MIME_TYPE_COLUMN);
                    if (DataConstants.NOTE.equals(type)) {
                        // 文本笔记
                        mContent = cursor.getString(DATA_CONTENT_COLUMN);
                        mMode = cursor.getInt(DATA_MODE_COLUMN);
                        mNote.setTextDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else if (DataConstants.CALL_NOTE.equals(type)) {
                        // 通话记录
                        mNote.setCallDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else {
                        Log.d(TAG, "Wrong note type with type:" + type);
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        } else {
            Log.e(TAG, "No data with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note's data with id " + mNoteId);
        }
    }

    // ==================== 工厂方法 ====================
    /**
     * 创建空笔记（新建）
     *
     * @param context 上下文
     * @param folderId 目标文件夹 ID
     * @param widgetId 关联的小部件 ID（可选）
     * @param widgetType 小部件类型
     * @param defaultBgColorId 默认背景颜色
     * @return WorkingNote 实例
     */
    public static WorkingNote createEmptyNote(Context context, long folderId, int widgetId,
                                              int widgetType, int defaultBgColorId) {
        WorkingNote note = new WorkingNote(context, folderId);
        note.setBgColorId(defaultBgColorId);
        note.setWidgetId(widgetId);
        note.setWidgetType(widgetType);
        return note;
    }

    /**
     * 加载已有笔记
     * @param context 上下文
     * @param id 笔记 ID
     * @return WorkingNote 实例
     */
    public static WorkingNote load(Context context, long id) {
        return new WorkingNote(context, id, 0);
    }

    // ==================== 业务方法 ====================
    /**
     * 保存笔记到数据库
     *
     * @return true 表示保存成功
     */
    public synchronized boolean saveNote() {
        if (isWorthSaving()) {
            // 如果不存在于数据库，先创建空笔记获得 ID
            if (!existInDatabase()) {
                if ((mNoteId = Note.getNewNoteId(mContext, mFolderId)) == 0) {
                    Log.e(TAG, "Create new note fail with id:" + mNoteId);
                    return false;
                }
            }

            // 同步数据到数据库
            mNote.syncNote(mContext, mNoteId);

            /**
             * 如果笔记关联了桌面小部件，通知小部件更新内容
             */
            if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    && mWidgetType != Notes.TYPE_WIDGET_INVALIDE
                    && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * 判断笔记是否已存在于数据库
     */
    public boolean existInDatabase() {
        return mNoteId > 0;
    }

    /**
     * 判断笔记是否值得保存
     * 条件：未删除，且（有内容 或 已有持久化且有本地修改）
     */
    private boolean isWorthSaving() {
        if (mIsDeleted || (!existInDatabase() && TextUtils.isEmpty(mContent))
                || (existInDatabase() && !mNote.isLocalModified())) {
            return false;
        } else {
            return true;
        }
    }

    // ==================== Setter 方法（带 UI 回调） ====================
    /**
     * 设置 UI 状态变更监听器
     */
    public void setOnSettingStatusChangedListener(NoteSettingChangedListener l) {
        mNoteSettingStatusListener = l;
    }

    /**
     * 设置提醒时间
     * @param date 时间戳，0 表示取消提醒
     * @param set true 表示设置提醒，false 表示取消
     */
    public void setAlertDate(long date, boolean set) {
        if (date != mAlertDate) {
            mAlertDate = date;
            mNote.setNoteValue(NoteColumns.ALERTED_DATE, String.valueOf(mAlertDate));
        }
        if (mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onClockAlertChanged(date, set);
        }
    }

    /**
     * 标记删除
     * @param mark true 表示标记为已删除
     */
    public void markDeleted(boolean mark) {
        mIsDeleted = mark;
        // 如果有关联的小部件，通知更新
        if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onWidgetChanged();
        }
    }

    /**
     * 设置背景颜色
     * @param id 颜色 ID（0-4）
     */
    public void setBgColorId(int id) {
        if (id != mBgColorId) {
            mBgColorId = id;
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onBackgroundColorChanged();
            }
            mNote.setNoteValue(NoteColumns.BG_COLOR_ID, String.valueOf(id));
        }
    }

    /**
     * 设置清单模式
     * @param mode 0=普通文本，1=清单模式
     */
    public void setCheckListMode(int mode) {
        if (mMode != mode) {
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onCheckListModeChanged(mMode, mode);
            }
            mMode = mode;
            mNote.setTextData(TextNote.MODE, String.valueOf(mMode));
        }
    }

    /**
     * 设置小部件类型
     */
    public void setWidgetType(int type) {
        if (type != mWidgetType) {
            mWidgetType = type;
            mNote.setNoteValue(NoteColumns.WIDGET_TYPE, String.valueOf(mWidgetType));
        }
    }

    /**
     * 设置小部件 ID
     */
    public void setWidgetId(int id) {
        if (id != mWidgetId) {
            mWidgetId = id;
            mNote.setNoteValue(NoteColumns.WIDGET_ID, String.valueOf(mWidgetId));
        }
    }

    /**
     * 设置笔记内容
     * @param text 文本内容
     */
    public void setWorkingText(String text) {
        if (!TextUtils.equals(mContent, text)) {
            mContent = text;
            mNote.setTextData(DataColumns.CONTENT, mContent);
        }
    }

    /**
     * 转换为通话记录笔记
     * @param phoneNumber 电话号码
     * @param callDate 通话时间
     */
    public void convertToCallNote(String phoneNumber, long callDate) {
        mNote.setCallData(CallNote.CALL_DATE, String.valueOf(callDate));
        mNote.setCallData(CallNote.PHONE_NUMBER, phoneNumber);
        mNote.setNoteValue(NoteColumns.PARENT_ID, String.valueOf(Notes.ID_CALL_RECORD_FOLDER));
    }

    // ==================== Getter 方法 ====================
    public boolean hasClockAlert() {
        return (mAlertDate > 0 ? true : false);
    }

    public String getContent() {
        return mContent;
    }

    public long getAlertDate() {
        return mAlertDate;
    }

    public long getModifiedDate() {
        return mModifiedDate;
    }

    public int getBgColorResId() {
        return NoteBgResources.getNoteBgResource(mBgColorId);
    }

    public int getBgColorId() {
        return mBgColorId;
    }

    public int getTitleBgResId() {
        return NoteBgResources.getNoteTitleBgResource(mBgColorId);
    }

    public int getCheckListMode() {
        return mMode;
    }

    public long getNoteId() {
        return mNoteId;
    }

    public long getFolderId() {
        return mFolderId;
    }

    public int getWidgetId() {
        return mWidgetId;
    }

    public int getWidgetType() {
        return mWidgetType;
    }

    // ==================== 监听器接口 ====================
    /**
     * 笔记设置变更监听器
     * UI 层（NoteEditActivity）实现此接口，响应模型层的变化
     */
    public interface NoteSettingChangedListener {
        /**
         * 背景颜色变化时回调
         */
        void onBackgroundColorChanged();

        /**
         * 提醒时间变化时回调
         * @param date 提醒时间戳
         * @param set true=设置提醒，false=取消提醒
         */
        void onClockAlertChanged(long date, boolean set);

        /**
         * 小部件内容变化时回调
         * 用于更新桌面小部件的显示
         */
        void onWidgetChanged();

        /**
         * 清单模式切换时回调
         * @param oldMode 切换前的模式
         * @param newMode 切换后的模式
         */
        void onCheckListModeChanged(int oldMode, int newMode);
    }
}