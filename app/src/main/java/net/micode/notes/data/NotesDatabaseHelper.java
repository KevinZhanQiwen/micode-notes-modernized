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

package net.micode.notes.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 数据库帮助类
 *
 * 职责：
 * 1. 创建和升级数据库
 * 2. 定义表结构
 * 3. 管理数据库触发器（保证数据一致性）
 *
 * 设计亮点：
 * - 单例模式：确保只有一个数据库连接
 * - 触发器：8个触发器自动维护数据一致性，减少代码逻辑
 *
 * 数据库版本历史：
 * v1: 初始版本
 * v2: 重建表（破坏性升级）
 * v3: 添加 GTASK_ID 列，添加回收站文件夹
 * v4: 添加 VERSION 列（用于同步冲突检测）
 */
public class NotesDatabaseHelper extends SQLiteOpenHelper {
    /** 数据库文件名 */
    private static final String DB_NAME = "note.db";

    /** 当前数据库版本 */
    private static final int DB_VERSION = 4;

    /**
     * 表名常量
     */
    public interface TABLE {
        /** 笔记表：存储笔记和文件夹的元数据 */
        public static final String NOTE = "note";

        /** 数据表：存储笔记的实际内容 */
        public static final String DATA = "data";
    }

    private static final String TAG = "NotesDatabaseHelper";

    /** 单例实例 */
    private static NotesDatabaseHelper mInstance;

    // ==================== 建表 SQL 语句 ====================

    /**
     * note 表建表语句
     *
     * 字段说明：
     * - _id: 主键
     * - parent_id: 父文件夹 ID（自引用外键）
     * - alert_date: 提醒时间
     * - bg_color_id: 背景颜色（0-4）
     * - created_date: 创建时间，默认当前时间戳（毫秒）
     * - has_attachment: 是否有附件（预留）
     * - modified_date: 修改时间
     * - notes_count: 文件夹内的笔记数
     * - snippet: 摘要/预览
     * - type: 0=笔记，1=文件夹，2=系统文件夹
     * - widget_id: 关联的小部件 ID
     * - widget_type: 小部件类型
     * - sync_id: 同步 ID（云端时间戳）
     * - local_modified: 本地修改标记
     * - origin_parent_id: 原始父文件夹（移入临时文件夹前）
     * - gtask_id: Google Tasks ID
     * - version: 版本号（冲突检测）
     */
    private static final String CREATE_NOTE_TABLE_SQL =
            "CREATE TABLE " + TABLE.NOTE + "(" +
                    NoteColumns.ID + " INTEGER PRIMARY KEY," +
                    NoteColumns.PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.ALERTED_DATE + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.BG_COLOR_ID + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
                    NoteColumns.HAS_ATTACHMENT + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
                    NoteColumns.NOTES_COUNT + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.SNIPPET + " TEXT NOT NULL DEFAULT ''," +
                    NoteColumns.TYPE + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.WIDGET_ID + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.WIDGET_TYPE + " INTEGER NOT NULL DEFAULT -1," +
                    NoteColumns.SYNC_ID + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.LOCAL_MODIFIED + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.ORIGIN_PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.GTASK_ID + " TEXT NOT NULL DEFAULT ''," +
                    NoteColumns.VERSION + " INTEGER NOT NULL DEFAULT 0" +
                    ")";

    /**
     * data 表建表语句
     *
     * 字段说明：
     * - _id: 主键
     * - mime_type: MIME 类型（区分文本笔记/通话记录）
     * - note_id: 外键，关联 note 表
     * - content: 内容
     * - data1~data5: 通用字段，用途由 MIME 类型决定
     *   - 文本笔记：data1 存储模式（0=普通，1=清单）
     *   - 通话记录：data1 存储通话时间，data3 存储电话号码
     */
    private static final String CREATE_DATA_TABLE_SQL =
            "CREATE TABLE " + TABLE.DATA + "(" +
                    DataColumns.ID + " INTEGER PRIMARY KEY," +
                    DataColumns.MIME_TYPE + " TEXT NOT NULL," +
                    DataColumns.NOTE_ID + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
                    NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
                    DataColumns.CONTENT + " TEXT NOT NULL DEFAULT ''," +
                    DataColumns.DATA1 + " INTEGER," +
                    DataColumns.DATA2 + " INTEGER," +
                    DataColumns.DATA3 + " TEXT NOT NULL DEFAULT ''," +
                    DataColumns.DATA4 + " TEXT NOT NULL DEFAULT ''," +
                    DataColumns.DATA5 + " TEXT NOT NULL DEFAULT ''" +
                    ")";

    /**
     * 为 data 表的 note_id 字段创建索引
     * 加速根据笔记 ID 查询数据的操作
     */
    private static final String CREATE_DATA_NOTE_ID_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS note_id_index ON " +
                    TABLE.DATA + "(" + DataColumns.NOTE_ID + ");";

    // ==================== 触发器 SQL 语句 ====================
    // 触发器用于自动维护数据一致性，避免在 Java 代码中重复编写逻辑

    /**
     * 触发器：移动笔记时，目标文件夹的笔记数 +1
     * 触发时机：更新 note 表的 parent_id 字段之后
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
            "CREATE TRIGGER increase_folder_count_on_update "+
                    " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
                    "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
                    " END";

    /**
     * 触发器：移出笔记时，原文件夹的笔记数 -1
     * 触发时机：更新 note 表的 parent_id 字段之后
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
            "CREATE TRIGGER decrease_folder_count_on_update " +
                    " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
                    "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
                    "  AND " + NoteColumns.NOTES_COUNT + ">0" + ";" +
                    " END";

    /**
     * 触发器：插入新笔记时，父文件夹的笔记数 +1
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER =
            "CREATE TRIGGER increase_folder_count_on_insert " +
                    " AFTER INSERT ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
                    "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
                    " END";

    /**
     * 触发器：删除笔记时，父文件夹的笔记数 -1
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER =
            "CREATE TRIGGER decrease_folder_count_on_delete " +
                    " AFTER DELETE ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
                    "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
                    "  AND " + NoteColumns.NOTES_COUNT + ">0;" +
                    " END";

    /**
     * 触发器：插入文本数据时，自动更新 note 表的 snippet 字段
     * 这样列表页不需要查询 data 表就能显示预览
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER =
            "CREATE TRIGGER update_note_content_on_insert " +
                    " AFTER INSERT ON " + TABLE.DATA +
                    " WHEN new." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
                    "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
                    " END";

    /**
     * 触发器：更新文本数据时，自动更新 note 表的 snippet 字段
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER =
            "CREATE TRIGGER update_note_content_on_update " +
                    " AFTER UPDATE ON " + TABLE.DATA +
                    " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
                    "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
                    " END";

    /**
     * 触发器：删除文本数据时，清空 note 表的 snippet 字段
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER =
            "CREATE TRIGGER update_note_content_on_delete " +
                    " AFTER delete ON " + TABLE.DATA +
                    " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.SNIPPET + "=''" +
                    "  WHERE " + NoteColumns.ID + "=old." + DataColumns.NOTE_ID + ";" +
                    " END";

    /**
     * 触发器：删除笔记时，级联删除其所有数据
     * 保证外键完整性
     */
    private static final String NOTE_DELETE_DATA_ON_DELETE_TRIGGER =
            "CREATE TRIGGER delete_data_on_delete " +
                    " AFTER DELETE ON " + TABLE.NOTE +
                    " BEGIN" +
                    "  DELETE FROM " + TABLE.DATA +
                    "   WHERE " + DataColumns.NOTE_ID + "=old." + NoteColumns.ID + ";" +
                    " END";

    /**
     * 触发器：删除文件夹时，级联删除其下的所有笔记
     * 递归删除，实现文件夹的批量删除
     */
    private static final String FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER =
            "CREATE TRIGGER folder_delete_notes_on_delete " +
                    " AFTER DELETE ON " + TABLE.NOTE +
                    " BEGIN" +
                    "  DELETE FROM " + TABLE.NOTE +
                    "   WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
                    " END";

    /**
     * 触发器：文件夹移入回收站时，其下的所有笔记也移入回收站
     * 保持文件夹和子笔记的状态一致
     */
    private static final String FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER =
            "CREATE TRIGGER folder_move_notes_on_trash " +
                    " AFTER UPDATE ON " + TABLE.NOTE +
                    " WHEN new." + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
                    "  WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
                    " END";

    /**
     * 私有构造方法，使用单例模式
     */
    public NotesDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    /**
     * 创建 note 表
     * 包括表结构、触发器和系统文件夹初始化
     */
    public void createNoteTable(SQLiteDatabase db) {
        db.execSQL(CREATE_NOTE_TABLE_SQL);
        reCreateNoteTableTriggers(db);  // 创建触发器
        createSystemFolder(db);          // 初始化系统文件夹
        Log.d(TAG, "note table has been created");
    }

    /**
     * 重新创建 note 表的所有触发器
     * 先删除旧的，再创建新的
     * 用于数据库升级时重建
     */
    private void reCreateNoteTableTriggers(SQLiteDatabase db) {
        // 删除旧触发器
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS delete_data_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS folder_delete_notes_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS folder_move_notes_on_trash");

        // 创建新触发器
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_DELETE_DATA_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER);
        db.execSQL(FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER);
        db.execSQL(FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER);
    }

    /**
     * 初始化系统文件夹
     * 在数据库首次创建时调用，插入系统必需的文件夹
     */
    private void createSystemFolder(SQLiteDatabase db) {
        ContentValues values = new ContentValues();

        /**
         * 通话记录文件夹 (ID = -2)
         * 用于存放自动创建的通话记录笔记
         */
        values.put(NoteColumns.ID, Notes.ID_CALL_RECORD_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        /**
         * 根文件夹 (ID = 0)
         * 默认文件夹，用户笔记默认存放在此
         */
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_ROOT_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        /**
         * 临时文件夹 (ID = -1)
         * 用于移动笔记过程中的临时存放
         */
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TEMPARAY_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        /**
         * 回收站文件夹 (ID = -3)
         * 存放已删除的笔记，用户可恢复
         */
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * 创建 data 表
     * 包括表结构、触发器和索引
     */
    public void createDataTable(SQLiteDatabase db) {
        db.execSQL(CREATE_DATA_TABLE_SQL);
        reCreateDataTableTriggers(db);
        db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL);
        Log.d(TAG, "data table has been created");
    }

    /**
     * 重新创建 data 表的所有触发器
     */
    private void reCreateDataTableTriggers(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_delete");

        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER);
    }

    /**
     * 获取单例实例
     * 线程安全（synchronized）
     */
    static synchronized NotesDatabaseHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new NotesDatabaseHelper(context);
        }
        return mInstance;
    }

    /**
     * 数据库首次创建时调用
     * 创建所有表和触发器
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        createNoteTable(db);
        createDataTable(db);
    }

    /**
     * 数据库升级时调用
     *
     * 升级路径：
     * v1 → v2：重建表（破坏性升级）
     * v2 → v3：添加 GTASK_ID 列，添加回收站文件夹
     * v3 → v4：添加 VERSION 列
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        boolean reCreateTriggers = false;
        boolean skipV2 = false;

        // v1 → v2：重建整个数据库
        if (oldVersion == 1) {
            upgradeToV2(db);
            skipV2 = true;  // 标记已包含 v2→v3 的升级
            oldVersion++;
        }

        // v2 → v3：添加 GTask 支持
        if (oldVersion == 2 && !skipV2) {
            upgradeToV3(db);
            reCreateTriggers = true;
            oldVersion++;
        }

        // v3 → v4：添加版本号字段
        if (oldVersion == 3) {
            upgradeToV4(db);
            oldVersion++;
        }

        // 如果升级过程中重建了触发器，需要重新创建
        if (reCreateTriggers) {
            reCreateNoteTableTriggers(db);
            reCreateDataTableTriggers(db);
        }

        // 检查升级是否完整完成
        if (oldVersion != newVersion) {
            throw new IllegalStateException("Upgrade notes database to version " + newVersion
                    + "fails");
        }
    }

    /**
     * 升级到版本 2
     * 破坏性升级：删除所有表后重建
     */
    private void upgradeToV2(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.NOTE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.DATA);
        createNoteTable(db);
        createDataTable(db);
    }

    /**
     * 升级到版本 3
     * 非破坏性升级：添加新列和新系统文件夹
     */
    private void upgradeToV3(SQLiteDatabase db) {
        // 删除不再使用的触发器
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_update");

        // 添加 GTASK_ID 列，用于存储 Google Tasks 的云端 ID
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_ID
                + " TEXT NOT NULL DEFAULT ''");

        // 添加回收站系统文件夹
        ContentValues values = new ContentValues();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * 升级到版本 4
     * 添加 VERSION 列，用于同步冲突检测
     */
    private void upgradeToV4(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.VERSION
                + " INTEGER NOT NULL DEFAULT 0");
    }
}