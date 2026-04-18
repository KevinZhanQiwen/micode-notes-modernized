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

import org.json.JSONObject;

/**
 * Google Tasks 同步节点抽象基类
 *
 * 这是同步模块的顶层抽象类，定义了同步节点的通用属性和操作接口。
 *
 * 继承体系：
 * - Node (抽象基类)
 *   ├── TaskList (文件夹，对应 Google TaskList)
 *   └── Task (笔记，对应 Google Task)
 *        └── MetaData (元数据，存储笔记完整信息)
 *
 * 同步操作常量：
 * 定义了本地与云端之间的 9 种同步操作类型
 *
 * 设计模式：模板方法模式
 * - 子类必须实现 getCreateAction、getUpdateAction、setContentByRemoteJSON 等方法
 * - 这些方法定义了节点与 Google Tasks API 交互的具体行为
 */
public abstract class Node {

    // ==================== 同步操作常量 ====================

    /** 无需同步（双方数据一致） */
    public static final int SYNC_ACTION_NONE = 0;

    /** 本地新增 → 上传到云端 */
    public static final int SYNC_ACTION_ADD_REMOTE = 1;

    /** 云端新增 → 下载到本地 */
    public static final int SYNC_ACTION_ADD_LOCAL = 2;

    /** 本地删除 → 云端删除 */
    public static final int SYNC_ACTION_DEL_REMOTE = 3;

    /** 云端删除 → 本地删除 */
    public static final int SYNC_ACTION_DEL_LOCAL = 4;

    /** 本地修改 → 更新云端 */
    public static final int SYNC_ACTION_UPDATE_REMOTE = 5;

    /** 云端修改 → 更新本地 */
    public static final int SYNC_ACTION_UPDATE_LOCAL = 6;

    /** 冲突（双方都修改了，需要解决） */
    public static final int SYNC_ACTION_UPDATE_CONFLICT = 7;

    /** 同步错误（如 ID 不匹配） */
    public static final int SYNC_ACTION_ERROR = 8;

    // ==================== 节点属性 ====================

    /** Google Tasks 中的唯一标识符（Global ID） */
    private String mGid;

    /** 节点名称（文件夹名或笔记摘要） */
    private String mName;

    /** 最后修改时间戳（云端时间） */
    private long mLastModified;

    /** 是否已删除（软删除标记） */
    private boolean mDeleted;

    /**
     * 构造方法：初始化默认值
     */
    public Node() {
        mGid = null;
        mName = "";
        mLastModified = 0;
        mDeleted = false;
    }

    // ==================== 抽象方法（子类必须实现） ====================

    /**
     * 生成创建操作的 JSON 请求体
     *
     * 用于向 Google Tasks API 发送创建请求
     *
     * @param actionId 操作 ID（用于批量操作时标识）
     * @return JSONObject 请求体
     */
    public abstract JSONObject getCreateAction(int actionId);

    /**
     * 生成更新操作的 JSON 请求体
     *
     * @param actionId 操作 ID
     * @return JSONObject 请求体
     */
    public abstract JSONObject getUpdateAction(int actionId);

    /**
     * 从云端 JSON 设置节点内容
     *
     * 用于解析 Google Tasks API 返回的数据
     *
     * @param js 云端返回的 JSON 对象
     */
    public abstract void setContentByRemoteJSON(JSONObject js);

    /**
     * 从本地 JSON 设置节点内容
     *
     * 用于解析本地数据库导出的 JSON（备份/恢复）
     *
     * @param js 本地 JSON 对象
     */
    public abstract void setContentByLocalJSON(JSONObject js);

    /**
     * 从节点内容生成本地 JSON
     *
     * 用于导出节点到本地数据库
     *
     * @return JSONObject 本地格式的 JSON
     */
    public abstract JSONObject getLocalJSONFromContent();

    /**
     * 判断同步操作类型
     *
     * 根据本地数据库游标和节点自身的状态，判断需要执行哪种同步操作
     *
     * @param c 数据库游标（指向当前节点对应的本地记录）
     * @return SYNC_ACTION_* 常量
     */
    public abstract int getSyncAction(Cursor c);

    // ==================== Getter / Setter ====================

    public void setGid(String gid) {
        this.mGid = gid;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public void setLastModified(long lastModified) {
        this.mLastModified = lastModified;
    }

    public void setDeleted(boolean deleted) {
        this.mDeleted = deleted;
    }

    public String getGid() {
        return this.mGid;
    }

    public String getName() {
        return this.mName;
    }

    public long getLastModified() {
        return this.mLastModified;
    }

    public boolean getDeleted() {
        return this.mDeleted;
    }
}