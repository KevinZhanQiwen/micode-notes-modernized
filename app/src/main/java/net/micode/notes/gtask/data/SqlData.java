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

package net.micode.notes.gtask.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;
import net.micode.notes.gtask.exception.ActionFailureException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 本地数据包装类
 *
 * 功能：
 * 1. 封装对本地数据库 data 表的操作
 * 2. 支持 JSON 与 ContentValues 的双向转换
 * 3. 支持新建和更新两种模式
 *
 * data 表存储笔记的实际内容，支持两种 MIME 类型：
 * - DataConstants.NOTE：普通文本笔记
 * - DataConstants.CALL_NOTE：通话记录笔记
 *
 * 字段映射：
 * - DATA1：文本笔记的清单模式标记 / 通话记录的通话时间
 * - DATA3：通话记录的电话号码
 */
public class SqlData {
    private static final String TAG = SqlData.class.getSimpleName();

    /** 无效 ID 常量 */
    private static final int INVALID_ID = -99999;

    /**
     * data 表查询投影列
     * 列索引：
     * 0: _id
     * 1: mime_type
     * 2: content
     * 3: data1
     * 4: data3
     */
    public static final String[] PROJECTION_DATA = new String[] {
            DataColumns.ID, DataColumns.MIME_TYPE, DataColumns.CONTENT, DataColumns.DATA1,
            DataColumns.DATA3
    };

    public static final int DATA_ID_COLUMN = 0;
    public static final int DATA_MIME_TYPE_COLUMN = 1;
    public static final int DATA_CONTENT_COLUMN = 2;
    public static final int DATA_CONTENT_DATA_1_COLUMN = 3;
    public static final int DATA_CONTENT_DATA_3_COLUMN = 4;

    // ==================== 实例字段 ====================

    private ContentResolver mContentResolver;

    /** 是否为新建模式（true=新建，false=更新已有） */
    private boolean mIsCreate;

    /** 数据 ID */
    private long mDataId;

    /** MIME 类型 */
    private String mDataMimeType;

    /** 内容 */
    private String mDataContent;

    /** 通用字段1（文本模式标志 或 通话时间） */
    private long mDataContentData1;

    /** 通用字段3（电话号码） */
    private String mDataContentData3;

    /** 差异值（只记录需要更新的字段） */
    private ContentValues mDiffDataValues;

    /**
     * 构造方法：新建模式
     *
     * @param context 上下文
     */
    public SqlData(Context context) {
        mContentResolver = context.getContentResolver();
        mIsCreate = true;
        mDataId = INVALID_ID;
        mDataMimeType = DataConstants.NOTE;      // 默认为文本笔记
        mDataContent = "";
        mDataContentData1 = 0;
        mDataContentData3 = "";
        mDiffDataValues = new ContentValues();
    }

    /**
     * 构造方法：从游标加载已有数据
     *
     * @param context 上下文
     * @param c 游标
     */
    public SqlData(Context context, Cursor c) {
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(c);
        mDiffDataValues = new ContentValues();
    }

    /**
     * 从游标加载数据
     *
     * @param c 游标
     */
    private void loadFromCursor(Cursor c) {
        mDataId = c.getLong(DATA_ID_COLUMN);
        mDataMimeType = c.getString(DATA_MIME_TYPE_COLUMN);
        mDataContent = c.getString(DATA_CONTENT_COLUMN);
        mDataContentData1 = c.getLong(DATA_CONTENT_DATA_1_COLUMN);
        mDataContentData3 = c.getString(DATA_CONTENT_DATA_3_COLUMN);
    }

    /**
     * 从 JSON 设置数据内容
     *
     * 自动记录差异值，用于后续提交
     *
     * @param js JSON 对象
     * @throws JSONException JSON 解析异常
     */
    public void setContent(JSONObject js) throws JSONException {
        // 解析 ID
        long dataId = js.has(DataColumns.ID) ? js.getLong(DataColumns.ID) : INVALID_ID;
        if (mIsCreate || mDataId != dataId) {
            mDiffDataValues.put(DataColumns.ID, dataId);
        }
        mDataId = dataId;

        // 解析 MIME 类型
        String dataMimeType = js.has(DataColumns.MIME_TYPE) ? js.getString(DataColumns.MIME_TYPE)
                : DataConstants.NOTE;
        if (mIsCreate || !mDataMimeType.equals(dataMimeType)) {
            mDiffDataValues.put(DataColumns.MIME_TYPE, dataMimeType);
        }
        mDataMimeType = dataMimeType;

        // 解析内容
        String dataContent = js.has(DataColumns.CONTENT) ? js.getString(DataColumns.CONTENT) : "";
        if (mIsCreate || !mDataContent.equals(dataContent)) {
            mDiffDataValues.put(DataColumns.CONTENT, dataContent);
        }
        mDataContent = dataContent;

        // 解析 DATA1
        long dataContentData1 = js.has(DataColumns.DATA1) ? js.getLong(DataColumns.DATA1) : 0;
        if (mIsCreate || mDataContentData1 != dataContentData1) {
            mDiffDataValues.put(DataColumns.DATA1, dataContentData1);
        }
        mDataContentData1 = dataContentData1;

        // 解析 DATA3
        String dataContentData3 = js.has(DataColumns.DATA3) ? js.getString(DataColumns.DATA3) : "";
        if (mIsCreate || !mDataContentData3.equals(dataContentData3)) {
            mDiffDataValues.put(DataColumns.DATA3, dataContentData3);
        }
        mDataContentData3 = dataContentData3;
    }

    /**
     * 获取数据内容为 JSON
     *
     * @return JSON 对象，新建模式下返回 null
     * @throws JSONException JSON 构造异常
     */
    public JSONObject getContent() throws JSONException {
        if (mIsCreate) {
            Log.e(TAG, "it seems that we haven't created this in database yet");
            return null;
        }
        JSONObject js = new JSONObject();
        js.put(DataColumns.ID, mDataId);
        js.put(DataColumns.MIME_TYPE, mDataMimeType);
        js.put(DataColumns.CONTENT, mDataContent);
        js.put(DataColumns.DATA1, mDataContentData1);
        js.put(DataColumns.DATA3, mDataContentData3);
        return js;
    }

    /**
     * 提交数据到数据库
     *
     * @param noteId 关联的笔记 ID
     * @param validateVersion 是否验证版本号
     * @param version 版本号（用于冲突检测）
     * @throws ActionFailureException 操作失败时抛出
     */
    public void commit(long noteId, boolean validateVersion, long version) {

        if (mIsCreate) {
            // ========== 新建模式：插入数据 ==========
            // 移除 ID 字段（数据库会自动生成）
            if (mDataId == INVALID_ID && mDiffDataValues.containsKey(DataColumns.ID)) {
                mDiffDataValues.remove(DataColumns.ID);
            }

            // 关联笔记 ID
            mDiffDataValues.put(DataColumns.NOTE_ID, noteId);

            // 执行插入
            Uri uri = mContentResolver.insert(Notes.CONTENT_DATA_URI, mDiffDataValues);
            try {
                mDataId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
        } else {
            // ========== 更新模式：更新数据 ==========
            if (mDiffDataValues.size() > 0) {
                int result = 0;
                if (!validateVersion) {
                    // 不验证版本号
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues, null, null);
                } else {
                    // 验证版本号：确保本地版本不高于传入的版本
                    result = mContentResolver.update(ContentUris.withAppendedId(
                                    Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues,
                            " ? in (SELECT " + NoteColumns.ID + " FROM " + TABLE.NOTE
                                    + " WHERE " + NoteColumns.VERSION + "=?)", new String[] {
                                    String.valueOf(noteId), String.valueOf(version)
                            });
                }
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }
        }

        // 清空差异值，标记为非新建模式
        mDiffDataValues.clear();
        mIsCreate = false;
    }

    /**
     * 获取数据 ID
     */
    public long getId() {
        return mDataId;
    }
}