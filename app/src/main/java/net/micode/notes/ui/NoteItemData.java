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

package net.micode.notes.ui;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import net.micode.notes.data.Contact;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.DataUtils;

/**
 * 笔记列表单项数据封装类
 *
 * 功能：
 * 1. 从数据库 Cursor 中读取一条笔记/文件夹的所有数据
 * 2. 封装成便于 UI 使用的 Java 对象
 * 3. 自动处理通话记录笔记的联系人姓名、号码
 * 4. 计算列表项位置（是否第一条、最后一条、紧挨着文件夹等）
 *
 * 使用者：NotesListItem、NotesListAdapter
 */
public class NoteItemData {
    /**
     * 数据库查询投影：需要读取的字段
     */
    static final String [] PROJECTION = new String [] {
            NoteColumns.ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.CREATED_DATE,
            NoteColumns.HAS_ATTACHMENT,
            NoteColumns.MODIFIED_DATE,
            NoteColumns.NOTES_COUNT,
            NoteColumns.PARENT_ID,
            NoteColumns.SNIPPET,
            NoteColumns.TYPE,
            NoteColumns.WIDGET_ID,
            NoteColumns.WIDGET_TYPE,
    };

    // 数据库字段下标常量
    private static final int ID_COLUMN                    = 0;
    private static final int ALERTED_DATE_COLUMN          = 1;
    private static final int BG_COLOR_ID_COLUMN           = 2;
    private static final int CREATED_DATE_COLUMN          = 3;
    private static final int HAS_ATTACHMENT_COLUMN        = 4;
    private static final int MODIFIED_DATE_COLUMN         = 5;
    private static final int NOTES_COUNT_COLUMN           = 6;
    private static final int PARENT_ID_COLUMN             = 7;
    private static final int SNIPPET_COLUMN               = 8;
    private static final int TYPE_COLUMN                  = 9;
    private static final int WIDGET_ID_COLUMN             = 10;
    private static final int WIDGET_TYPE_COLUMN           = 11;

    // 基础数据
    private long mId;                  // 笔记ID
    private long mAlertDate;           // 提醒时间
    private int mBgColorId;            // 背景色ID
    private long mCreatedDate;         // 创建时间
    private boolean mHasAttachment;    // 是否有附件
    private long mModifiedDate;        // 修改时间
    private int mNotesCount;           // 子笔记数量（文件夹）
    private long mParentId;            // 父文件夹ID
    private String mSnippet;           // 笔记摘要（预览文字）
    private int mType;                 // 类型：笔记/文件夹/系统文件夹
    private int mWidgetId;             // 小部件ID
    private int mWidgetType;           // 小部件类型

    // 通话记录专用
    private String mName;              // 联系人姓名
    private String mPhoneNumber;       // 电话号码

    // 列表项位置状态
    private boolean mIsLastItem;          // 是否是最后一项
    private boolean mIsFirstItem;         // 是否是第一项
    private boolean mIsOnlyOneItem;       // 列表是否只有这一项
    private boolean mIsOneNoteFollowingFolder;  // 文件夹后只有一个笔记
    private boolean mIsMultiNotesFollowingFolder; // 文件夹后有多个笔记

    /**
     * 构造方法：从 Cursor 读取并封装所有数据
     */
    public NoteItemData(Context context, Cursor cursor) {
        // 读取基础字段
        mId = cursor.getLong(ID_COLUMN);
        mAlertDate = cursor.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = cursor.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = cursor.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = (cursor.getInt(HAS_ATTACHMENT_COLUMN) > 0);
        mModifiedDate = cursor.getLong(MODIFIED_DATE_COLUMN);
        mNotesCount = cursor.getInt(NOTES_COUNT_COLUMN);
        mParentId = cursor.getLong(PARENT_ID_COLUMN);
        mSnippet = cursor.getString(SNIPPET_COLUMN);

        // 清除清单模式的勾选符号，使预览更干净
        mSnippet = mSnippet.replace(NoteEditActivity.TAG_CHECKED, "")
                .replace(NoteEditActivity.TAG_UNCHECKED, "");

        mType = cursor.getInt(TYPE_COLUMN);
        mWidgetId = cursor.getInt(WIDGET_ID_COLUMN);
        mWidgetType = cursor.getInt(WIDGET_TYPE_COLUMN);

        mPhoneNumber = "";
        // 如果是通话记录文件夹，读取电话号码和联系人姓名
        if (mParentId == Notes.ID_CALL_RECORD_FOLDER) {
            mPhoneNumber = DataUtils.getCallNumberByNoteId(context.getContentResolver(), mId);
            if (!TextUtils.isEmpty(mPhoneNumber)) {
                mName = Contact.getContact(context, mPhoneNumber);
                if (mName == null) {
                    mName = mPhoneNumber; // 没有姓名则显示号码
                }
            }
        }

        if (mName == null) {
            mName = "";
        }

        // 检查当前项在列表中的位置
        checkPostion(cursor);
    }

    /**
     * 检查列表项位置：是否第一条、最后一条、是否紧跟在文件夹后面
     */
    private void checkPostion(Cursor cursor) {
        mIsLastItem = cursor.isLast();
        mIsFirstItem = cursor.isFirst();
        mIsOnlyOneItem = (cursor.getCount() == 1);
        mIsMultiNotesFollowingFolder = false;
        mIsOneNoteFollowingFolder = false;

        // 如果是笔记类型，且不是第一条，判断是否紧跟在文件夹后面
        if (mType == Notes.TYPE_NOTE && !mIsFirstItem) {
            int position = cursor.getPosition();
            if (cursor.moveToPrevious()) {
                // 前一项是文件夹/系统文件夹
                if (cursor.getInt(TYPE_COLUMN) == Notes.TYPE_FOLDER
                        || cursor.getInt(TYPE_COLUMN) == Notes.TYPE_SYSTEM) {
                    // 判断后面还有没有更多笔记
                    if (cursor.getCount() > (position + 1)) {
                        mIsMultiNotesFollowingFolder = true;
                    } else {
                        mIsOneNoteFollowingFolder = true;
                    }
                }
                // 移回原位置
                if (!cursor.moveToNext()) {
                    throw new IllegalStateException("cursor move to previous but can't move back");
                }
            }
        }
    }

    // ======================== 公开 Getter 方法 ========================
    // 用于列表项布局根据位置显示不同样式

    public boolean isOneFollowingFolder() {
        return mIsOneNoteFollowingFolder;
    }

    public boolean isMultiFollowingFolder() {
        return mIsMultiNotesFollowingFolder;
    }

    public boolean isLast() {
        return mIsLastItem;
    }

    public String getCallName() {
        return mName;
    }

    public boolean isFirst() {
        return mIsFirstItem;
    }

    public boolean isSingle() {
        return mIsOnlyOneItem;
    }

    public long getId() {
        return mId;
    }

    public long getAlertDate() {
        return mAlertDate;
    }

    public long getCreatedDate() {
        return mCreatedDate;
    }

    public boolean hasAttachment() {
        return mHasAttachment;
    }

    public long getModifiedDate() {
        return mModifiedDate;
    }

    public int getBgColorId() {
        return mBgColorId;
    }

    public long getParentId() {
        return mParentId;
    }

    public int getNotesCount() {
        return mNotesCount;
    }

    public long getFolderId () {
        return mParentId;
    }

    public int getType() {
        return mType;
    }

    public int getWidgetType() {
        return mWidgetType;
    }

    public int getWidgetId() {
        return mWidgetId;
    }

    public String getSnippet() {
        return mSnippet;
    }

    public boolean hasAlert() {
        return (mAlertDate > 0);
    }

    public boolean isCallRecord() {
        return (mParentId == Notes.ID_CALL_RECORD_FOLDER && !TextUtils.isEmpty(mPhoneNumber));
    }

    /**
     * 静态工具方法：直接从Cursor获取笔记类型
     */
    public static int getNoteType(Cursor cursor) {
        return cursor.getInt(TYPE_COLUMN);
    }
}