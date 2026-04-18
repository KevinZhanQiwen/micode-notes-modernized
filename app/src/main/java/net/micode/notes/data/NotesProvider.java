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

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;

/**
 * 笔记应用的 ContentProvider
 *
 * 职责：
 * 1. 作为应用数据的唯一访问入口
 * 2. 封装 SQLite 数据库的 CRUD 操作
 * 3. 支持跨进程访问（Widget、AlarmReceiver 在不同进程）
 * 4. 自动通知 ContentObserver 数据变更
 *
 * URI 设计（RESTful 风格）：
 * - content://micode_notes/note        → 操作所有笔记/文件夹
 * - content://micode_notes/note/#      → 操作单条笔记/文件夹
 * - content://micode_notes/data        → 操作所有数据
 * - content://micode_notes/data/#      → 操作单条数据
 * - content://micode_notes/search      → 搜索笔记
 * - content://micode_notes/search_suggest/* → 搜索建议（系统搜索框）
 *
 * 设计亮点：
 * - 版本号自动递增（increaseNoteVersion）：每次更新 note 表时 VERSION 字段 +1
 * - 数据变更通知：每次修改后调用 notifyChange()，Cursor 自动刷新
 */
public class NotesProvider extends ContentProvider {
    /** URI 匹配器，用于解析传入的 URI 并分发给对应的处理逻辑 */
    private static final UriMatcher mMatcher;

    /** 数据库帮助类实例 */
    private NotesDatabaseHelper mHelper;

    private static final String TAG = "NotesProvider";

    // ==================== URI 匹配码 ====================
    private static final int URI_NOTE            = 1;  // content://micode_notes/note
    private static final int URI_NOTE_ITEM       = 2;  // content://micode_notes/note/#
    private static final int URI_DATA            = 3;  // content://micode_notes/data
    private static final int URI_DATA_ITEM       = 4;  // content://micode_notes/data/#
    private static final int URI_SEARCH          = 5;  // content://micode_notes/search
    private static final int URI_SEARCH_SUGGEST  = 6;  // content://micode_notes/search_suggest/*

    /**
     * 静态初始化块：配置 URI 匹配规则
     */
    static {
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mMatcher.addURI(Notes.AUTHORITY, "note", URI_NOTE);
        mMatcher.addURI(Notes.AUTHORITY, "note/#", URI_NOTE_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "data", URI_DATA);
        mMatcher.addURI(Notes.AUTHORITY, "data/#", URI_DATA_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "search", URI_SEARCH);
        // 添加系统搜索建议的 URI 匹配
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_SEARCH_SUGGEST);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", URI_SEARCH_SUGGEST);
    }

    /**
     * 搜索结果的投影列定义
     * 用于将数据库字段映射到系统搜索框架需要的字段
     *
     * 字段映射：
     * - _id → _id
     * - _id → SUGGEST_COLUMN_INTENT_EXTRA_DATA（点击后传给 Intent 的额外数据）
     * - snippet → SUGGEST_COLUMN_TEXT_1（第一行显示）
     * - snippet → SUGGEST_COLUMN_TEXT_2（第二行显示）
     * - R.drawable.search_result → SUGGEST_COLUMN_ICON_1（图标）
     * - ACTION_VIEW → SUGGEST_COLUMN_INTENT_ACTION（点击后的 Intent Action）
     * - TextNote.CONTENT_TYPE → SUGGEST_COLUMN_INTENT_DATA（Intent Data 的 MIME 类型）
     *
     * 注意：x'0A' 是 SQLite 中的换行符，使用 REPLACE 去除换行使摘要更紧凑
     */
    private static final String NOTES_SEARCH_PROJECTION = NoteColumns.ID + ","
            + NoteColumns.ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA + ","
            + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + ","
            + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2 + ","
            + R.drawable.search_result + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1 + ","
            + "'" + Intent.ACTION_VIEW + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_ACTION + ","
            + "'" + Notes.TextNote.CONTENT_TYPE + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA;

    /**
     * 搜索查询 SQL
     * 条件：
     * - snippet 包含关键词（LIKE）
     * - 不在回收站中（parent_id != -3）
     * - 类型为笔记（type = 0）
     */
    private static String NOTES_SNIPPET_SEARCH_QUERY = "SELECT " + NOTES_SEARCH_PROJECTION
            + " FROM " + TABLE.NOTE
            + " WHERE " + NoteColumns.SNIPPET + " LIKE ?"
            + " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER
            + " AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE;

    /**
     * Provider 创建时调用
     * 初始化数据库帮助类
     */
    @Override
    public boolean onCreate() {
        mHelper = NotesDatabaseHelper.getInstance(getContext());
        return true;
    }

    /**
     * 查询数据
     *
     * @param uri 查询的 URI
     * @param projection 需要返回的列
     * @param selection 查询条件
     * @param selectionArgs 查询条件参数
     * @param sortOrder 排序规则
     * @return 查询结果的 Cursor
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        Cursor c = null;
        SQLiteDatabase db = mHelper.getReadableDatabase();
        String id = null;

        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 查询所有笔记/文件夹
                c = db.query(TABLE.NOTE, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;

            case URI_NOTE_ITEM:
                // 查询单条笔记/文件夹，URI 中提取 ID
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.NOTE, projection, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;

            case URI_DATA:
                // 查询所有数据
                c = db.query(TABLE.DATA, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;

            case URI_DATA_ITEM:
                // 查询单条数据
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.DATA, projection, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;

            case URI_SEARCH:
            case URI_SEARCH_SUGGEST:
                // 搜索处理：不允许指定投影和排序，因为搜索有固定的格式
                if (sortOrder != null || projection != null) {
                    throw new IllegalArgumentException(
                            "do not specify sortOrder, selection, selectionArgs, or projection" + "with this query");
                }

                // 获取搜索关键词
                String searchString = null;
                if (mMatcher.match(uri) == URI_SEARCH_SUGGEST) {
                    // 搜索建议：从 URI 路径中提取
                    if (uri.getPathSegments().size() > 1) {
                        searchString = uri.getPathSegments().get(1);
                    }
                } else {
                    // 普通搜索：从查询参数中提取
                    searchString = uri.getQueryParameter("pattern");
                }

                if (TextUtils.isEmpty(searchString)) {
                    return null;
                }

                // 执行搜索，使用 LIKE '%keyword%' 模式
                try {
                    searchString = String.format("%%%s%%", searchString);
                    c = db.rawQuery(NOTES_SNIPPET_SEARCH_QUERY,
                            new String[] { searchString });
                } catch (IllegalStateException ex) {
                    Log.e(TAG, "got exception: " + ex.toString());
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 设置通知 URI：当数据变化时，该 Cursor 会自动收到通知并刷新
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    /**
     * 插入数据
     *
     * @param uri 插入的目标 URI
     * @param values 要插入的数据
     * @return 新插入数据的 URI
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        long dataId = 0, noteId = 0, insertedId = 0;

        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 插入笔记/文件夹
                insertedId = noteId = db.insert(TABLE.NOTE, null, values);
                break;

            case URI_DATA:
                // 插入数据，需要关联 note_id
                if (values.containsKey(DataColumns.NOTE_ID)) {
                    noteId = values.getAsLong(DataColumns.NOTE_ID);
                } else {
                    Log.d(TAG, "Wrong data format without note id:" + values.toString());
                }
                insertedId = dataId = db.insert(TABLE.DATA, null, values);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 通知 note URI 的观察者：数据已变化
        if (noteId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), null);
        }

        // 通知 data URI 的观察者
        if (dataId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId), null);
        }

        // 返回新插入数据的 URI
        return ContentUris.withAppendedId(uri, insertedId);
    }

    /**
     * 删除数据
     *
     * @param uri 要删除的数据 URI
     * @param selection 删除条件
     * @param selectionArgs 删除条件参数
     * @return 删除的行数
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean deleteData = false;

        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 删除笔记/文件夹，系统文件夹（ID <= 0）不能被删除
                selection = "(" + selection + ") AND " + NoteColumns.ID + ">0 ";
                count = db.delete(TABLE.NOTE, selection, selectionArgs);
                break;

            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                long noteId = Long.valueOf(id);
                // 系统文件夹（ID <= 0）不允许删除
                if (noteId <= 0) {
                    break;
                }
                count = db.delete(TABLE.NOTE,
                        NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;

            case URI_DATA:
                count = db.delete(TABLE.DATA, selection, selectionArgs);
                deleteData = true;
                break;

            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.delete(TABLE.DATA,
                        DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                deleteData = true;
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 通知观察者数据已变化
        if (count > 0) {
            if (deleteData) {
                // 删除数据时，关联的笔记也发生了变化
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * 更新数据
     *
     * @param uri 要更新的数据 URI
     * @param values 更新的值
     * @param selection 更新条件
     * @param selectionArgs 更新条件参数
     * @return 更新的行数
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean updateData = false;

        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 更新笔记前，先增加版本号（用于同步冲突检测）
                increaseNoteVersion(-1, selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, selection, selectionArgs);
                break;

            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                // 更新单条笔记前，增加版本号
                increaseNoteVersion(Long.valueOf(id), selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                break;

            case URI_DATA:
                count = db.update(TABLE.DATA, values, selection, selectionArgs);
                updateData = true;
                break;

            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.update(TABLE.DATA, values, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                updateData = true;
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 通知观察者数据已变化
        if (count > 0) {
            if (updateData) {
                // 更新数据时，关联的笔记也发生了变化
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * 解析并拼接 selection 条件
     * 如果已有 selection，加上 " AND " 前缀
     */
    private String parseSelection(String selection) {
        return (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
    }

    /**
     * 增加笔记的版本号
     *
     * 用途：每次更新笔记时，VERSION 字段 +1
     * GTask 同步时通过比较版本号判断是否有本地修改，避免冲突覆盖
     *
     * @param id 笔记 ID，-1 表示不限制 ID
     * @param selection 额外的 WHERE 条件
     * @param selectionArgs WHERE 条件的参数
     */
    private void increaseNoteVersion(long id, String selection, String[] selectionArgs) {
        StringBuilder sql = new StringBuilder(120);
        sql.append("UPDATE ");
        sql.append(TABLE.NOTE);
        sql.append(" SET ");
        sql.append(NoteColumns.VERSION);
        sql.append("=" + NoteColumns.VERSION + "+1 ");

        if (id > 0 || !TextUtils.isEmpty(selection)) {
            sql.append(" WHERE ");
        }
        if (id > 0) {
            sql.append(NoteColumns.ID + "=" + String.valueOf(id));
        }
        if (!TextUtils.isEmpty(selection)) {
            String selectString = id > 0 ? parseSelection(selection) : selection;
            // 将 ? 占位符替换为实际参数值
            for (String args : selectionArgs) {
                selectString = selectString.replaceFirst("\\?", args);
            }
            sql.append(selectString);
        }

        mHelper.getWritableDatabase().execSQL(sql.toString());
    }

    /**
     * 返回 MIME 类型
     * 本应用未实现，返回 null
     */
    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

}