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

import android.net.Uri;

/**
 * Notes 常量定义类
 *
 * 这是整个应用的常量中心，定义了：
 * 1. ContentProvider 的 AUTHORITY 和 URI
 * 2. 笔记/文件夹的类型常量
 * 3. 系统文件夹的 ID
 * 4. Intent Extra 的键名
 * 5. 数据库表结构（通过内部接口）
 *
 * 设计模式：常量接口反模式（Constant Interface Anti-pattern）
 * 但这里使用类 + 内部接口的方式，有一定组织性
 */
public class Notes {
    /** ContentProvider 的唯一标识，与 AndroidManifest.xml 中的 authorities 一致 */
    public static final String AUTHORITY = "micode_notes";

    public static final String TAG = "Notes";

    // ==================== 节点类型常量 ====================
    /** 类型：普通笔记 */
    public static final int TYPE_NOTE     = 0;

    /** 类型：文件夹 */
    public static final int TYPE_FOLDER   = 1;

    /** 类型：系统文件夹（不可删除、不可移动的特殊文件夹） */
    public static final int TYPE_SYSTEM   = 2;

    // ==================== 系统文件夹 ID ====================
    /**
     * 根文件夹 ID
     * 用户创建的所有笔记默认存放在此文件夹下
     */
    public static final int ID_ROOT_FOLDER = 0;

    /**
     * 临时文件夹 ID
     * 用于移动笔记过程中的临时存放
     */
    public static final int ID_TEMPARAY_FOLDER = -1;

    /**
     * 通话记录文件夹 ID
     * 自动保存通话记录的笔记存放在此
     */
    public static final int ID_CALL_RECORD_FOLDER = -2;

    /**
     * 回收站文件夹 ID
     * 被删除的笔记先移入回收站，可恢复
     */
    public static final int ID_TRASH_FOLER = -3;

    // ==================== Intent Extra 键名 ====================
    /** 提醒时间戳（long） */
    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";

    /** 背景颜色 ID（int） */
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id";

    /** 小部件 ID（int） */
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";

    /** 小部件类型（int：0=2x2，1=4x4） */
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";

    /** 文件夹 ID（long） */
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";

    /** 通话时间戳（long） */
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";

    // ==================== 小部件类型常量 ====================
    /** 无效小部件 */
    public static final int TYPE_WIDGET_INVALIDE      = -1;

    /** 2x2 尺寸小部件 */
    public static final int TYPE_WIDGET_2X            = 0;

    /** 4x4 尺寸小部件 */
    public static final int TYPE_WIDGET_4X            = 1;

    /**
     * 数据类型常量
     * 定义了 data 表中支持的数据类型
     */
    public static class DataConstants {
        /** 普通文本笔记的 MIME 类型 */
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;

        /** 通话记录笔记的 MIME 类型 */
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE;
    }

    // ==================== ContentProvider URI ====================
    /**
     * 查询所有笔记和文件夹的 URI
     * 对应表：note
     * 格式：content://micode_notes/note
     */
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note");

    /**
     * 查询数据的 URI
     * 对应表：data（存储笔记的实际内容）
     * 格式：content://micode_notes/data
     */
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data");

    /**
     * note 表的列名定义
     * 存储笔记和文件夹的元数据
     */
    public interface NoteColumns {
        /** 主键 ID，自增 */
        public static final String ID = "_id";

        /** 父文件夹 ID，关联到同一张表的 ID 字段 */
        public static final String PARENT_ID = "parent_id";

        /** 创建时间戳（毫秒） */
        public static final String CREATED_DATE = "created_date";

        /** 最后修改时间戳（毫秒） */
        public static final String MODIFIED_DATE = "modified_date";

        /** 提醒时间戳（毫秒），0 表示无提醒 */
        public static final String ALERTED_DATE = "alert_date";

        /**
         * 摘要内容
         * 对于文件夹：显示文件夹名
         * 对于笔记：显示笔记内容的预览（第一行）
         */
        public static final String SNIPPET = "snippet";

        /** 关联的小部件 ID，0 表示未关联 */
        public static final String WIDGET_ID = "widget_id";

        /** 小部件类型：0=2x2，1=4x4，-1=无效 */
        public static final String WIDGET_TYPE = "widget_type";

        /** 背景颜色 ID：0=黄，1=蓝，2=白，3=绿，4=红 */
        public static final String BG_COLOR_ID = "bg_color_id";

        /** 是否有附件（图片、录音等），暂未使用 */
        public static final String HAS_ATTACHMENT = "has_attachment";

        /** 文件夹内的笔记数量（仅对文件夹有效） */
        public static final String NOTES_COUNT = "notes_count";

        /** 类型：0=笔记，1=文件夹，2=系统文件夹 */
        public static final String TYPE = "type";

        /**
         * 同步 ID
         * 存储云端最后修改时间，用于增量同步
         */
        public static final String SYNC_ID = "sync_id";

        /** 本地是否被修改标记，1=已修改，0=未修改 */
        public static final String LOCAL_MODIFIED = "local_modified";

        /** 移入临时文件夹前的原始父文件夹 ID */
        public static final String ORIGIN_PARENT_ID = "origin_parent_id";

        /** Google Tasks 的云端任务 ID */
        public static final String GTASK_ID = "gtask_id";

        /** 版本号，每次更新时 +1，用于同步冲突检测 */
        public static final String VERSION = "version";
    }

    /**
     * data 表的列名定义
     * 存储笔记的实际内容，支持多种 MIME 类型
     *
     * 设计思路：类似 Android 系统的 MediaStore，使用 MIME 类型区分不同数据
     */
    public interface DataColumns {
        /** 主键 ID */
        public static final String ID = "_id";

        /** MIME 类型，如：vnd.android.cursor.item/text_note */
        public static final String MIME_TYPE = "mime_type";

        /** 关联的笔记 ID，外键指向 note 表 */
        public static final String NOTE_ID = "note_id";

        /** 创建时间戳 */
        public static final String CREATED_DATE = "created_date";

        /** 修改时间戳 */
        public static final String MODIFIED_DATE = "modified_date";

        /** 数据内容（文本内容） */
        public static final String CONTENT = "content";

        /** 通用数据列1，用途由 MIME 类型决定 */
        public static final String DATA1 = "data1";

        /** 通用数据列2 */
        public static final String DATA2 = "data2";

        /** 通用数据列3（TEXT 类型） */
        public static final String DATA3 = "data3";

        /** 通用数据列4（TEXT 类型） */
        public static final String DATA4 = "data4";

        /** 通用数据列5（TEXT 类型） */
        public static final String DATA5 = "data5";
    }

    /**
     * 文本笔记的数据结构定义
     * 继承 DataColumns，添加了文本特有的字段含义
     */
    public static final class TextNote implements DataColumns {
        /**
         * 模式标识
         * 使用 DATA1 字段存储
         * 1 = 清单模式（待办列表），0 = 普通文本模式
         */
        public static final String MODE = DATA1;

        /** 清单模式常量 */
        public static final int MODE_CHECK_LIST = 1;

        /** 目录类型的 MIME 类型 */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";

        /** 单项类型的 MIME 类型 */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note";

        /** 数据 URI */
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");
    }

    /**
     * 通话记录笔记的数据结构定义
     * 继承 DataColumns，添加了通话记录特有的字段含义
     */
    public static final class CallNote implements DataColumns {
        /**
         * 通话时间
         * 使用 DATA1 字段存储
         */
        public static final String CALL_DATE = DATA1;

        /**
         * 电话号码
         * 使用 DATA3 字段存储
         */
        public static final String PHONE_NUMBER = DATA3;

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note";
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");
    }
}