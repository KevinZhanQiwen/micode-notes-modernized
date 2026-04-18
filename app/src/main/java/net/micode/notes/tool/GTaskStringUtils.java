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

package net.micode.notes.tool;

/**
 * Google Tasks 同步字符串常量类
 *
 * 功能：定义 Google Tasks API 交互中使用的所有 JSON 字段名和特殊字符串常量
 *
 * 设计意图：
 * 1. 集中管理所有 JSON 键名，避免拼写错误
 * 2. 定义 MIUI 文件夹在云端的前缀映射规则
 * 3. 定义元数据任务的特殊常量
 *
 * 使用场景：GTaskClient、Task、TaskList、MetaData、GTaskManager 等类
 */
public class GTaskStringUtils {

    // ==================== Google Tasks API JSON 字段名 ====================

    /** 操作 ID */
    public final static String GTASK_JSON_ACTION_ID = "action_id";

    /** 操作列表（批量操作时使用） */
    public final static String GTASK_JSON_ACTION_LIST = "action_list";

    /** 操作类型 */
    public final static String GTASK_JSON_ACTION_TYPE = "action_type";

    /** 操作类型：创建 */
    public final static String GTASK_JSON_ACTION_TYPE_CREATE = "create";

    /** 操作类型：获取全部 */
    public final static String GTASK_JSON_ACTION_TYPE_GETALL = "get_all";

    /** 操作类型：移动 */
    public final static String GTASK_JSON_ACTION_TYPE_MOVE = "move";

    /** 操作类型：更新 */
    public final static String GTASK_JSON_ACTION_TYPE_UPDATE = "update";

    /** 创建者 ID */
    public final static String GTASK_JSON_CREATOR_ID = "creator_id";

    /** 子实体（用于任务列表中的任务） */
    public final static String GTASK_JSON_CHILD_ENTITY = "child_entity";

    /** 客户端版本 */
    public final static String GTASK_JSON_CLIENT_VERSION = "client_version";

    /** 完成标记（清单模式） */
    public final static String GTASK_JSON_COMPLETED = "completed";

    /** 当前列表 ID */
    public final static String GTASK_JSON_CURRENT_LIST_ID = "current_list_id";

    /** 默认列表 ID */
    public final static String GTASK_JSON_DEFAULT_LIST_ID = "default_list_id";

    /** 删除标记 */
    public final static String GTASK_JSON_DELETED = "deleted";

    /** 目标列表（移动操作） */
    public final static String GTASK_JSON_DEST_LIST = "dest_list";

    /** 目标父节点（移动操作） */
    public final static String GTASK_JSON_DEST_PARENT = "dest_parent";

    /** 目标父节点类型 */
    public final static String GTASK_JSON_DEST_PARENT_TYPE = "dest_parent_type";

    /** 实体差异数据 */
    public final static String GTASK_JSON_ENTITY_DELTA = "entity_delta";

    /** 实体类型 */
    public final static String GTASK_JSON_ENTITY_TYPE = "entity_type";

    /** 是否获取已删除的实体 */
    public final static String GTASK_JSON_GET_DELETED = "get_deleted";

    /** 实体 ID */
    public final static String GTASK_JSON_ID = "id";

    /** 索引（排序用） */
    public final static String GTASK_JSON_INDEX = "index";

    /** 最后修改时间 */
    public final static String GTASK_JSON_LAST_MODIFIED = "last_modified";

    /** 最新同步点 */
    public final static String GTASK_JSON_LATEST_SYNC_POINT = "latest_sync_point";

    /** 列表 ID */
    public final static String GTASK_JSON_LIST_ID = "list_id";

    /** 列表数组 */
    public final static String GTASK_JSON_LISTS = "lists";

    /** 名称 */
    public final static String GTASK_JSON_NAME = "name";

    /** 新创建的 ID */
    public final static String GTASK_JSON_NEW_ID = "new_id";

    /** 备注内容（对应笔记的正文） */
    public final static String GTASK_JSON_NOTES = "notes";

    /** 父节点 ID */
    public final static String GTASK_JSON_PARENT_ID = "parent_id";

    /** 前一个兄弟节点 ID（用于排序） */
    public final static String GTASK_JSON_PRIOR_SIBLING_ID = "prior_sibling_id";

    /** 结果数组 */
    public final static String GTASK_JSON_RESULTS = "results";

    /** 源列表（移动操作） */
    public final static String GTASK_JSON_SOURCE_LIST = "source_list";

    /** 任务数组 */
    public final static String GTASK_JSON_TASKS = "tasks";

    /** 类型 */
    public final static String GTASK_JSON_TYPE = "type";

    /** 类型值：分组（TaskList） */
    public final static String GTASK_JSON_TYPE_GROUP = "GROUP";

    /** 类型值：任务（Task） */
    public final static String GTASK_JSON_TYPE_TASK = "TASK";

    /** 用户信息 */
    public final static String GTASK_JSON_USER = "user";

    // ==================== MIUI 文件夹映射常量 ====================

    /**
     * MIUI 文件夹在云端的前缀
     *
     * 映射规则：本地文件夹名 → 云端任务列表名
     * 例如：本地 "默认文件夹" → 云端 "[MIUI_Notes]默认文件夹"
     *
     * 目的：避免与用户自己的 Google Tasks 列表冲突
     */
    public final static String MIUI_FOLDER_PREFFIX = "[MIUI_Notes]";

    /** 默认文件夹名称 */
    public final static String FOLDER_DEFAULT = "Default";

    /** 通话记录文件夹名称 */
    public final static String FOLDER_CALL_NOTE = "Call_Note";

    /** 元数据文件夹名称（存储笔记完整信息的特殊任务列表） */
    public final static String FOLDER_META = "METADATA";

    // ==================== 元数据相关常量 ====================

    /** 元数据中关联的笔记 gid */
    public final static String META_HEAD_GTASK_ID = "meta_gid";

    /** 元数据中的笔记部分 */
    public final static String META_HEAD_NOTE = "meta_note";

    /** 元数据中的数据部分 */
    public final static String META_HEAD_DATA = "meta_data";

    /**
     * 元数据任务的固定名称
     * 用于存储笔记的完整信息（Google Tasks 的 notes 字段有限制）
     *
     * 警告文本：提示用户不要手动更新或删除
     */
    public final static String META_NOTE_NAME = "[META INFO] DON'T UPDATE AND DELETE";

}