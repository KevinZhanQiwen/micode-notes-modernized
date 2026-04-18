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

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * 备份导出工具类（单例）
 *
 * 功能：将笔记导出为 TXT 文件到 SD 卡
 *
 * 导出格式：
 * [文件夹名称]
 * 修改时间: 2024-01-15 14:30
 * 笔记内容第一行
 * 笔记内容第二行
 *
 * 修改时间: 2024-01-15 10:20
 * 另一条笔记内容
 *
 * 设计模式：单例模式 + 内部类封装导出逻辑
 */
public class BackupUtils {
    private static final String TAG = "BackupUtils";

    /** 单例实例 */
    private static BackupUtils sInstance;

    /**
     * 获取单例实例（线程安全）
     */
    public static synchronized BackupUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BackupUtils(context);
        }
        return sInstance;
    }

    // ==================== 导出状态常量 ====================
    /** SD 卡未挂载 */
    public static final int STATE_SD_CARD_UNMOUONTED           = 0;
    /** 备份文件不存在 */
    public static final int STATE_BACKUP_FILE_NOT_EXIST        = 1;
    /** 数据被破坏（格式错误） */
    public static final int STATE_DATA_DESTROIED               = 2;
    /** 系统错误 */
    public static final int STATE_SYSTEM_ERROR                 = 3;
    /** 导出成功 */
    public static final int STATE_SUCCESS                      = 4;

    /** 实际的导出逻辑实现类 */
    private TextExport mTextExport;

    private BackupUtils(Context context) {
        mTextExport = new TextExport(context);
    }

    /**
     * 检查外部存储（SD 卡）是否可用
     */
    private static boolean externalStorageAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * 导出笔记到文本文件
     * @return 状态码（STATE_*）
     */
    public int exportToText() {
        return mTextExport.exportToText();
    }

    /**
     * 获取导出的文件名
     */
    public String getExportedTextFileName() {
        return mTextExport.mFileName;
    }

    /**
     * 获取导出的文件目录
     */
    public String getExportedTextFileDir() {
        return mTextExport.mFileDirectory;
    }

    /**
     * 内部类：文本导出逻辑实现
     */
    private static class TextExport {
        // ==================== 投影列定义 ====================
        /** note 表查询投影列 */
        private static final String[] NOTE_PROJECTION = {
                NoteColumns.ID,
                NoteColumns.MODIFIED_DATE,
                NoteColumns.SNIPPET,
                NoteColumns.TYPE
        };

        private static final int NOTE_COLUMN_ID = 0;
        private static final int NOTE_COLUMN_MODIFIED_DATE = 1;
        private static final int NOTE_COLUMN_SNIPPET = 2;

        /** data 表查询投影列 */
        private static final String[] DATA_PROJECTION = {
                DataColumns.CONTENT,
                DataColumns.MIME_TYPE,
                DataColumns.DATA1,   // 通话时间
                DataColumns.DATA2,
                DataColumns.DATA3,   // 电话号码
                DataColumns.DATA4,
        };

        private static final int DATA_COLUMN_CONTENT = 0;
        private static final int DATA_COLUMN_MIME_TYPE = 1;
        private static final int DATA_COLUMN_CALL_DATE = 2;
        private static final int DATA_COLUMN_PHONE_NUMBER = 4;

        // ==================== 格式化字符串索引 ====================
        private final String [] TEXT_FORMAT;
        private static final int FORMAT_FOLDER_NAME          = 0;  // 文件夹名称格式
        private static final int FORMAT_NOTE_DATE            = 1;  // 笔记日期格式
        private static final int FORMAT_NOTE_CONTENT         = 2;  // 笔记内容格式

        private Context mContext;
        private String mFileName;      // 导出文件名
        private String mFileDirectory; // 导出目录

        public TextExport(Context context) {
            TEXT_FORMAT = context.getResources().getStringArray(R.array.format_for_exported_note);
            mContext = context;
            mFileName = "";
            mFileDirectory = "";
        }

        private String getFormat(int id) {
            return TEXT_FORMAT[id];
        }

        /**
         * 导出指定文件夹下的所有笔记到 PrintStream
         *
         * @param folderId 文件夹 ID
         * @param ps 输出流
         */
        private void exportFolderToText(String folderId, PrintStream ps) {
            // 查询该文件夹下的所有笔记
            Cursor notesCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    NoteColumns.PARENT_ID + "=?",
                    new String[] { folderId },
                    null);

            if (notesCursor != null) {
                if (notesCursor.moveToFirst()) {
                    do {
                        // 打印笔记的修改时间
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE),
                                DateFormat.format(
                                        mContext.getString(R.string.format_datetime_mdhm),
                                        notesCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // 打印笔记内容
                        String noteId = notesCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (notesCursor.moveToNext());
                }
                notesCursor.close();
            }
        }

        /**
         * 导出单条笔记到 PrintStream
         *
         * @param noteId 笔记 ID
         * @param ps 输出流
         */
        private void exportNoteToText(String noteId, PrintStream ps) {
            Cursor dataCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_DATA_URI,
                    DATA_PROJECTION,
                    DataColumns.NOTE_ID + "=?",
                    new String[] { noteId },
                    null);

            if (dataCursor != null) {
                if (dataCursor.moveToFirst()) {
                    do {
                        String mimeType = dataCursor.getString(DATA_COLUMN_MIME_TYPE);

                        if (DataConstants.CALL_NOTE.equals(mimeType)) {
                            // 通话记录笔记：输出电话号码、通话时间、位置
                            String phoneNumber = dataCursor.getString(DATA_COLUMN_PHONE_NUMBER);
                            long callDate = dataCursor.getLong(DATA_COLUMN_CALL_DATE);
                            String location = dataCursor.getString(DATA_COLUMN_CONTENT);

                            if (!TextUtils.isEmpty(phoneNumber)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        phoneNumber));
                            }
                            ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                    DateFormat.format(
                                            mContext.getString(R.string.format_datetime_mdhm),
                                            callDate)));
                            if (!TextUtils.isEmpty(location)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        location));
                            }
                        } else if (DataConstants.NOTE.equals(mimeType)) {
                            // 普通文本笔记：直接输出内容
                            String content = dataCursor.getString(DATA_COLUMN_CONTENT);
                            if (!TextUtils.isEmpty(content)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        content));
                            }
                        }
                    } while (dataCursor.moveToNext());
                }
                dataCursor.close();
            }

            // 打印换行分隔符，区分不同的笔记
            try {
                ps.write(new byte[] {
                        Character.LINE_SEPARATOR, Character.LETTER_NUMBER
                });
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

        /**
         * 主导出方法
         *
         * 导出顺序：
         * 1. 所有用户文件夹及其下的笔记
         * 2. 根目录下的笔记（parent_id = 0）
         *
         * @return 状态码
         */
        public int exportToText() {
            // 检查 SD 卡状态
            if (!externalStorageAvailable()) {
                Log.d(TAG, "Media was not mounted");
                return STATE_SD_CARD_UNMOUONTED;
            }

            // 创建输出流
            PrintStream ps = getExportToTextPrintStream();
            if (ps == null) {
                Log.e(TAG, "get print stream error");
                return STATE_SYSTEM_ERROR;
            }

            // ========== 导出文件夹及其笔记 ==========
            // 查询条件：类型为文件夹且不在回收站，或者通话记录文件夹（ID=-2）
            Cursor folderCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    "(" + NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER + " AND "
                            + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER + ") OR "
                            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER,
                    null, null);

            if (folderCursor != null) {
                if (folderCursor.moveToFirst()) {
                    do {
                        // 获取文件夹名称
                        String folderName = "";
                        if(folderCursor.getLong(NOTE_COLUMN_ID) == Notes.ID_CALL_RECORD_FOLDER) {
                            // 通话记录文件夹使用特殊名称
                            folderName = mContext.getString(R.string.call_record_folder_name);
                        } else {
                            folderName = folderCursor.getString(NOTE_COLUMN_SNIPPET);
                        }

                        if (!TextUtils.isEmpty(folderName)) {
                            ps.println(String.format(getFormat(FORMAT_FOLDER_NAME), folderName));
                        }

                        String folderId = folderCursor.getString(NOTE_COLUMN_ID);
                        exportFolderToText(folderId, ps);
                    } while (folderCursor.moveToNext());
                }
                folderCursor.close();
            }

            // ========== 导出根目录下的笔记 ==========
            // 查询条件：类型为笔记，且父文件夹为根文件夹（parent_id = 0）
            Cursor noteCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    NoteColumns.TYPE + "=" + +Notes.TYPE_NOTE + " AND " + NoteColumns.PARENT_ID
                            + "=0",
                    null, null);

            if (noteCursor != null) {
                if (noteCursor.moveToFirst()) {
                    do {
                        // 打印修改时间
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE),
                                DateFormat.format(
                                        mContext.getString(R.string.format_datetime_mdhm),
                                        noteCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // 打印笔记内容
                        String noteId = noteCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (noteCursor.moveToNext());
                }
                noteCursor.close();
            }

            ps.close();
            return STATE_SUCCESS;
        }

        /**
         * 创建导出文件并返回 PrintStream
         *
         * 文件路径：/sdcard/MiCodeNotes/note_20240115.txt
         *
         * @return PrintStream，失败返回 null
         */
        private PrintStream getExportToTextPrintStream() {
            File file = generateFileMountedOnSDcard(mContext, R.string.file_path,
                    R.string.file_name_txt_format);
            if (file == null) {
                Log.e(TAG, "create file to exported failed");
                return null;
            }
            mFileName = file.getName();
            mFileDirectory = mContext.getString(R.string.file_path);

            PrintStream ps = null;
            try {
                FileOutputStream fos = new FileOutputStream(file);
                ps = new PrintStream(fos);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            } catch (NullPointerException e) {
                e.printStackTrace();
                return null;
            }
            return ps;
        }
    }

    /**
     * 在 SD 卡上创建导出文件
     *
     * @param context 上下文
     * @param filePathResId 目录路径的资源 ID（如 R.string.file_path）
     * @param fileNameFormatResId 文件名格式的资源 ID
     * @return File 对象，失败返回 null
     */
    private static File generateFileMountedOnSDcard(Context context, int filePathResId,
                                                    int fileNameFormatResId) {
        StringBuilder sb = new StringBuilder();
        sb.append(Environment.getExternalStorageDirectory());
        sb.append(context.getString(filePathResId));
        File filedir = new File(sb.toString());

        sb.append(context.getString(
                fileNameFormatResId,
                DateFormat.format(context.getString(R.string.format_date_ymd),
                        System.currentTimeMillis())));
        File file = new File(sb.toString());

        try {
            if (!filedir.exists()) {
                filedir.mkdir();  // 创建目录
            }
            if (!file.exists()) {
                file.createNewFile();  // 创建文件
            }
            return file;
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}