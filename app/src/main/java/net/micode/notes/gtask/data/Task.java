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

import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 笔记模型（对应 Google Tasks 中的 Task）
 *
 * 功能：
 * 1. 将本地笔记映射为 Google Tasks 中的任务
 * 2. 生成创建/更新操作的 JSON 请求
 * 3. 解析云端返回的 JSON 数据
 * 4. 判断同步冲突类型
 *
 * 数据映射：
 * - 本地笔记内容 → Google Task.name
 * - 本地笔记摘要 → Google Task.notes
 * - 清单模式完成项 → Google Task.completed
 *
 * 元数据机制：
 * 由于 Google Tasks 的 notes 字段有长度限制，笔记的完整信息
 * 存储在关联的 MetaData 任务中（固定名称 "[META INFO] DON'T UPDATE AND DELETE"）
 */
public class Task extends Node {
    private static final String TAG = Task.class.getSimpleName();

    /** 是否完成（用于清单模式） */
    private boolean mCompleted;

    /** 备注内容（对应 Google Tasks 的 notes 字段，存储摘要或元数据） */
    private String mNotes;

    /** 元数据 JSON（存储笔记的完整信息，包括背景色、提醒时间等） */
    private JSONObject mMetaInfo;

    /** 前一个兄弟节点（用于维护任务顺序） */
    private Task mPriorSibling;

    /** 所属的父任务列表 */
    private TaskList mParent;

    public Task() {
        super();
        mCompleted = false;
        mNotes = null;
        mPriorSibling = null;
        mParent = null;
        mMetaInfo = null;
    }

    /**
     * 生成创建任务的 JSON 请求
     *
     * 请求格式：
     * {
     *   "action_type": "create",
     *   "action_id": 123,
     *   "index": 0,
     *   "entity_delta": { "name": "笔记内容", "creator_id": "null", "entity_type": "TASK" },
     *   "parent_id": "父任务列表的 GID",
     *   "dest_parent_type": "GROUP",
     *   "list_id": "任务列表 GID",
     *   "prior_sibling_id": "前一个兄弟任务的 GID"
     * }
     *
     * @param actionId 操作 ID
     * @return JSONObject 请求体
     */
    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // 操作类型：创建
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // 操作 ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // 索引位置（用于排序）
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mParent.getChildTaskIndex(this));

            // 实体数据
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_TASK);
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

            // 父节点信息
            js.put(GTaskStringUtils.GTASK_JSON_PARENT_ID, mParent.getGid());
            js.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);
            js.put(GTaskStringUtils.GTASK_JSON_LIST_ID, mParent.getGid());

            // 前一个兄弟节点（用于确定插入位置）
            if (mPriorSibling != null) {
                js.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, mPriorSibling.getGid());
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate task-create jsonobject");
        }

        return js;
    }

    /**
     * 生成更新任务的 JSON 请求
     *
     * @param actionId 操作 ID
     * @return JSONObject 请求体
     */
    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // 操作类型：更新
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            // 操作 ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // 任务 ID
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // 实体更新数据
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate task-update jsonobject");
        }

        return js;
    }

    /**
     * 从云端 JSON 设置任务内容
     *
     * @param js 云端返回的 JSON 对象
     */
    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                // 解析 GID
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }

                // 解析最后修改时间
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }

                // 解析名称
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }

                // 解析备注
                if (js.has(GTaskStringUtils.GTASK_JSON_NOTES)) {
                    setNotes(js.getString(GTaskStringUtils.GTASK_JSON_NOTES));
                }

                // 解析删除标记
                if (js.has(GTaskStringUtils.GTASK_JSON_DELETED)) {
                    setDeleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_DELETED));
                }

                // 解析完成标记
                if (js.has(GTaskStringUtils.GTASK_JSON_COMPLETED)) {
                    setCompleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_COMPLETED));
                }
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get task content from jsonobject");
            }
        }
    }

    /**
     * 从本地 JSON 设置任务内容
     *
     * 用于从备份恢复时解析数据
     *
     * @param js 本地 JSON 对象
     */
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)
                || !js.has(GTaskStringUtils.META_HEAD_DATA)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }

        try {
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

            // 验证类型必须是笔记
            if (note.getInt(NoteColumns.TYPE) != Notes.TYPE_NOTE) {
                Log.e(TAG, "invalid type");
                return;
            }

            // 从 data 数组中提取文本内容作为任务名称
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject data = dataArray.getJSONObject(i);
                if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                    setName(data.getString(DataColumns.CONTENT));
                    break;
                }
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 从任务内容生成本地 JSON
     *
     * 用于将云端任务导出为本地笔记格式
     *
     * @return JSONObject 本地格式的 JSON
     */
    public JSONObject getLocalJSONFromContent() {
        String name = getName();
        try {
            if (mMetaInfo == null) {
                // 新建的任务（从云端首次同步）
                if (name == null) {
                    Log.w(TAG, "the note seems to be an empty one");
                    return null;
                }

                // 构建简单的笔记 JSON
                JSONObject js = new JSONObject();
                JSONObject note = new JSONObject();
                JSONArray dataArray = new JSONArray();
                JSONObject data = new JSONObject();
                data.put(DataColumns.CONTENT, name);
                dataArray.put(data);
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
                return js;
            } else {
                // 已有元数据的任务（更新本地元数据中的内容）
                JSONObject note = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                JSONArray dataArray = mMetaInfo.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                        data.put(DataColumns.CONTENT, getName());
                        break;
                    }
                }

                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                return mMetaInfo;
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 设置元数据
     *
     * 从 MetaData 对象中解析 JSON，存储笔记的完整信息
     *
     * @param metaData 元数据对象
     */
    public void setMetaInfo(MetaData metaData) {
        if (metaData != null && metaData.getNotes() != null) {
            try {
                mMetaInfo = new JSONObject(metaData.getNotes());
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                mMetaInfo = null;
            }
        }
    }

    /**
     * 判断同步操作类型
     *
     * 核心逻辑：
     * 1. 比较本地 SYNC_ID 与云端 lastModified
     * 2. 检查本地修改标记 LOCAL_MODIFIED
     * 3. 判断冲突并返回对应的同步操作
     *
     * @param c 数据库游标
     * @return SYNC_ACTION_* 常量
     */
    public int getSyncAction(Cursor c) {
        try {
            JSONObject noteInfo = null;
            if (mMetaInfo != null && mMetaInfo.has(GTaskStringUtils.META_HEAD_NOTE)) {
                noteInfo = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            }

            if (noteInfo == null) {
                Log.w(TAG, "it seems that note meta has been deleted");
                return SYNC_ACTION_UPDATE_REMOTE;
            }

            if (!noteInfo.has(NoteColumns.ID)) {
                Log.w(TAG, "remote note id seems to be deleted");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            // 验证本地 ID 与云端 ID 是否匹配
            if (c.getLong(SqlNote.ID_COLUMN) != noteInfo.getLong(NoteColumns.ID)) {
                Log.w(TAG, "note id doesn't match");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // 本地无修改
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 双方无更新
                    return SYNC_ACTION_NONE;
                } else {
                    // 云端有新版本，需要更新本地
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            } else {
                // 本地有修改
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 仅有本地修改
                    return SYNC_ACTION_UPDATE_REMOTE;
                } else {
                    // 冲突：双方都修改了
                    return SYNC_ACTION_UPDATE_CONFLICT;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    /**
     * 判断是否值得保存
     *
     * @return true 表示有内容需要保存
     */
    public boolean isWorthSaving() {
        return mMetaInfo != null || (getName() != null && getName().trim().length() > 0)
                || (getNotes() != null && getNotes().trim().length() > 0);
    }

    // ==================== Getter / Setter ====================

    public void setCompleted(boolean completed) {
        this.mCompleted = completed;
    }

    public void setNotes(String notes) {
        this.mNotes = notes;
    }

    public void setPriorSibling(Task priorSibling) {
        this.mPriorSibling = priorSibling;
    }

    public void setParent(TaskList parent) {
        this.mParent = parent;
    }

    public boolean getCompleted() {
        return this.mCompleted;
    }

    public String getNotes() {
        return this.mNotes;
    }

    public Task getPriorSibling() {
        return this.mPriorSibling;
    }

    public TaskList getParent() {
        return this.mParent;
    }
}