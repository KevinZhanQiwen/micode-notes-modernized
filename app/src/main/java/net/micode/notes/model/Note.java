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

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;

/**
 * 笔记数据底层封装类
 *
 * 职责：
 * 1. 封装对 note 表和 data 表的 ContentValues 差异值
 * 2. 将内存中的变更批量同步到数据库
 * 3. 支持文本笔记和通话记录两种数据类型
 *
 * 设计模式：
 * - 差异更新：只记录变更字段，减少数据库写入
 * - 组合模式：Note 组合 NoteData 管理数据表
 * - 批量操作：使用 ContentProviderOperation 保证事务原子性
 *
 * 与 WorkingNote 的关系：
 * - WorkingNote 是业务层模型，Note 是其底层数据操作类
 * - WorkingNote 组合 Note，将数据操作委托给 Note 执行
 */
public class Note {
    /** note 表的差异值（只记录需要更新的字段） */
    private ContentValues mNoteDiffValues;

    /** data 表的数据封装 */
    private NoteData mNoteData;

    private static final String TAG = "Note";

    /**
     * 获取一个新的笔记 ID
     *
     * 设计意图：预先在数据库中创建一条空笔记记录，获得 ID
     * 这样后续设置提醒（需要 noteId 注册 AlarmManager）和
     * 创建桌面快捷方式（需要 noteId 构造 Intent）才能正常工作
     *
     * @param context 上下文
     * @param folderId 所属文件夹 ID
     * @return 新创建的笔记 ID，失败返回 0
     */
    public static synchronized long getNewNoteId(Context context, long folderId) {
        // 构建初始数据
        ContentValues values = new ContentValues();
        long createdTime = System.currentTimeMillis();
        values.put(NoteColumns.CREATED_DATE, createdTime);
        values.put(NoteColumns.MODIFIED_DATE, createdTime);
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);      // 类型：笔记
        values.put(NoteColumns.LOCAL_MODIFIED, 1);          // 标记为本地已修改
        values.put(NoteColumns.PARENT_ID, folderId);        // 设置父文件夹

        // 插入空笔记，获得 URI
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);

        long noteId = 0;
        try {
            // 从 URI 中解析出 ID，格式：content://micode_notes/note/123
            noteId = Long.valueOf(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Get note id error :" + e.toString());
            noteId = 0;
        }

        if (noteId == -1) {
            throw new IllegalStateException("Wrong note id:" + noteId);
        }
        return noteId;
    }

    /**
     * 构造方法：初始化差异值容器和数据封装
     */
    public Note() {
        mNoteDiffValues = new ContentValues();
        mNoteData = new NoteData();
    }

    /**
     * 设置 note 表的字段值
     * 自动标记 LOCAL_MODIFIED=1 并更新 MODIFIED_DATE
     *
     * @param key   列名（如 NoteColumns.BG_COLOR_ID）
     * @param value 值
     */
    public void setNoteValue(String key, String value) {
        mNoteDiffValues.put(key, value);
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
    }

    /**
     * 设置文本数据
     * @param key   列名（如 DataColumns.CONTENT）
     * @param value 值
     */
    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value);
    }

    /**
     * 设置文本数据的 ID（更新时使用）
     */
    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id);
    }

    /**
     * 获取文本数据的 ID
     */
    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    /**
     * 设置通话数据的 ID
     */
    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id);
    }

    /**
     * 设置通话数据
     * @param key   列名（如 CallNote.CALL_DATE）
     * @param value 值
     */
    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value);
    }

    /**
     * 判断是否有本地修改
     * @return true 表示有未同步的修改
     */
    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    /**
     * 同步笔记到数据库
     *
     * 流程：
     * 1. 更新 note 表（使用 mNoteDiffValues）
     * 2. 更新 data 表（通过 NoteData.pushIntoContentResolver）
     *
     * @param context 上下文
     * @param noteId  笔记 ID
     * @return true 表示同步成功
     */
    public boolean syncNote(Context context, long noteId) {
        if (noteId <= 0) {
            throw new IllegalArgumentException("Wrong note id:" + noteId);
        }

        // 无修改则直接返回
        if (!isLocalModified()) {
            return true;
        }

        /**
         * 更新 note 表
         * 理论上数据变化时应该更新 LOCAL_MODIFIED 和 MODIFIED_DATE
         * 即使更新失败，也继续尝试更新 data 表以保证数据安全
         */
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                mNoteDiffValues, null, null) == 0) {
            Log.e(TAG, "Update note error, should not happen");
            // 不返回，继续执行
        }
        mNoteDiffValues.clear();  // 清空差异值

        // 更新 data 表
        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            return false;
        }

        return true;
    }

    /**
     * 内部类：笔记数据封装
     *
     * 管理 data 表中与当前笔记关联的所有数据行
     * 支持两种 MIME 类型：
     * - TextNote.CONTENT_ITEM_TYPE：文本笔记内容
     * - CallNote.CONTENT_ITEM_TYPE：通话记录
     */
    private class NoteData {
        /** 文本数据的 ID（0 表示新建，需要 INSERT） */
        private long mTextDataId;

        /** 文本数据的差异值 */
        private ContentValues mTextDataValues;

        /** 通话数据的 ID（0 表示新建） */
        private long mCallDataId;

        /** 通话数据的差异值 */
        private ContentValues mCallDataValues;

        private static final String TAG = "NoteData";

        public NoteData() {
            mTextDataValues = new ContentValues();
            mCallDataValues = new ContentValues();
            mTextDataId = 0;
            mCallDataId = 0;
        }

        /**
         * 判断是否有本地修改
         */
        boolean isLocalModified() {
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        /**
         * 设置文本数据 ID（更新时使用）
         */
        void setTextDataId(long id) {
            if(id <= 0) {
                throw new IllegalArgumentException("Text data id should larger than 0");
            }
            mTextDataId = id;
        }

        /**
         * 设置通话数据 ID（更新时使用）
         */
        void setCallDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("Call data id should larger than 0");
            }
            mCallDataId = id;
        }

        /**
         * 设置通话数据
         * 同时更新 note 表的 LOCAL_MODIFIED 和 MODIFIED_DATE
         */
        void setCallData(String key, String value) {
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 设置文本数据
         * 同时更新 note 表的 LOCAL_MODIFIED 和 MODIFIED_DATE
         */
        void setTextData(String key, String value) {
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 将数据变更推送到 ContentResolver
         *
         * 使用批量操作（applyBatch）保证多个数据操作的原子性
         *
         * @param context 上下文
         * @param noteId  关联的笔记 ID
         * @return 成功返回笔记 URI，失败返回 null
         */
        Uri pushIntoContentResolver(Context context, long noteId) {
            if (noteId <= 0) {
                throw new IllegalArgumentException("Wrong note id:" + noteId);
            }

            ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
            ContentProviderOperation.Builder builder = null;

            // ========== 处理文本数据 ==========
            if(mTextDataValues.size() > 0) {
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);

                if (mTextDataId == 0) {
                    // 新建：插入数据行
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mTextDataValues);
                    try {
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new text data fail with noteId" + noteId);
                        mTextDataValues.clear();
                        return null;
                    }
                } else {
                    // 更新：构建更新操作
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId));
                    builder.withValues(mTextDataValues);
                    operationList.add(builder.build());
                }
                mTextDataValues.clear();
            }

            // ========== 处理通话数据 ==========
            if(mCallDataValues.size() > 0) {
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);

                if (mCallDataId == 0) {
                    // 新建：插入数据行
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mCallDataValues);
                    try {
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new call data fail with noteId" + noteId);
                        mCallDataValues.clear();
                        return null;
                    }
                } else {
                    // 更新：构建更新操作
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId));
                    builder.withValues(mCallDataValues);
                    operationList.add(builder.build());
                }
                mCallDataValues.clear();
            }

            // ========== 执行批量操作 ==========
            if (operationList.size() > 0) {
                try {
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    return (results == null || results.length == 0 || results[0] == null) ? null
                            : ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                } catch (RemoteException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                } catch (OperationApplicationException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                }
            }
            return null;
        }
    }
}