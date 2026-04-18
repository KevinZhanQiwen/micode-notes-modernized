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
import android.util.Log;

import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 元数据模型（继承 Task）
 *
 * 功能：
 * 存储笔记的完整元数据，解决 Google Tasks API 字段限制问题
 *
 * 为什么需要 MetaData？
 * Google Tasks 的 Task 对象只有 name、notes、completed 等有限字段，
 * 无法存储笔记的完整信息（如背景色、提醒时间、清单模式等）。
 * 因此使用一个特殊的 Task（名称为固定值 "[META INFO] DON'T UPDATE AND DELETE"）
 * 来存储笔记的完整 JSON 数据。
 *
 * 存储内容：
 * - 笔记的完整元数据（背景色、提醒时间等）
 * - 关联的笔记 GID（mRelatedGid）
 *
 * 重要限制：
 * MetaData 只从云端创建和同步，不会从本地生成
 * 因此 setContentByLocalJSON、getLocalJSONFromContent、getSyncAction 方法被禁用
 */
public class MetaData extends Task {
    private final static String TAG = MetaData.class.getSimpleName();

    /** 关联的笔记 GID */
    private String mRelatedGid = null;

    /**
     * 设置元数据
     *
     * 将关联的笔记 GID 存入 JSON，并设置任务名称
     *
     * @param gid 关联的笔记 GID
     * @param metaInfo 元数据 JSON 对象
     */
    public void setMeta(String gid, JSONObject metaInfo) {
        try {
            // 将关联的 GID 存入元数据 JSON
            metaInfo.put(GTaskStringUtils.META_HEAD_GTASK_ID, gid);
        } catch (JSONException e) {
            Log.e(TAG, "failed to put related gid");
        }
        // 设置 notes 字段为元数据 JSON 字符串
        setNotes(metaInfo.toString());
        // 设置固定名称，防止用户误操作
        setName(GTaskStringUtils.META_NOTE_NAME);
    }

    /**
     * 获取关联的笔记 GID
     */
    public String getRelatedGid() {
        return mRelatedGid;
    }

    /**
     * 判断是否值得保存
     *
     * @return true 表示有 notes 内容需要保存
     */
    @Override
    public boolean isWorthSaving() {
        return getNotes() != null;
    }

    /**
     * 从云端 JSON 设置元数据内容
     *
     * 解析 notes 字段中的 JSON，提取关联的笔记 GID
     *
     * @param js 云端返回的 JSON 对象
     */
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        super.setContentByRemoteJSON(js);
        if (getNotes() != null) {
            try {
                JSONObject metaInfo = new JSONObject(getNotes().trim());
                mRelatedGid = metaInfo.getString(GTaskStringUtils.META_HEAD_GTASK_ID);
            } catch (JSONException e) {
                Log.w(TAG, "failed to get related gid");
                mRelatedGid = null;
            }
        }
    }

    /**
     * 从本地 JSON 设置元数据内容
     *
     * 此方法不应被调用，因为 MetaData 不从本地生成
     *
     * @throws IllegalAccessError 总是抛出此异常
     */
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        throw new IllegalAccessError("MetaData:setContentByLocalJSON should not be called");
    }

    /**
     * 从元数据内容生成本地 JSON
     *
     * 此方法不应被调用，因为 MetaData 不从本地生成
     *
     * @throws IllegalAccessError 总是抛出此异常
     */
    @Override
    public JSONObject getLocalJSONFromContent() {
        throw new IllegalAccessError("MetaData:getLocalJSONFromContent should not be called");
    }

    /**
     * 判断同步操作类型
     *
     * 此方法不应被调用，因为 MetaData 不从本地生成
     *
     * @throws IllegalAccessError 总是抛出此异常
     */
    @Override
    public int getSyncAction(Cursor c) {
        throw new IllegalAccessError("MetaData:getSyncAction should not be called");
    }
}