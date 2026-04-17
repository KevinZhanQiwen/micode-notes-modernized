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
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 文件夹列表适配器
 *
 * 功能：
 * 1. 用于“移动到文件夹”弹窗的列表展示
 * 2. 绑定文件夹数据（ID + 名称）
 * 3. 特殊处理：根文件夹显示为“返回上级”
 * 4. 继承 CursorAdapter，直接从数据库加载文件夹
 */
public class FoldersListAdapter extends CursorAdapter {
    // 查询文件夹所需字段：ID、名称（snippet）
    public static final String [] PROJECTION = {
            NoteColumns.ID,
            NoteColumns.SNIPPET
    };

    // 列索引
    public static final int ID_COLUMN   = 0;
    public static final int NAME_COLUMN = 1;

    /**
     * 构造方法
     */
    public FoldersListAdapter(Context context, Cursor c) {
        super(context, c);
    }

    /**
     * 创建列表项视图
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new FolderListItem(context);
    }

    /**
     * 绑定数据到列表项
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof FolderListItem) {
            String folderName;
            // 如果是根文件夹 → 显示“返回上级”
            if (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) {
                folderName = context.getString(R.string.menu_move_parent_folder);
            } else {
                // 普通文件夹 → 显示文件夹名
                folderName = cursor.getString(NAME_COLUMN);
            }
            ((FolderListItem) view).bind(folderName);
        }
    }

    /**
     * 获取指定位置的文件夹名称（外部调用）
     */
    public String getFolderName(Context context, int position) {
        Cursor cursor = (Cursor) getItem(position);
        if (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) {
            return context.getString(R.string.menu_move_parent_folder);
        } else {
            return cursor.getString(NAME_COLUMN);
        }
    }

    // ======================== 文件夹列表项 ========================
    private class FolderListItem extends LinearLayout {
        private TextView mName;

        public FolderListItem(Context context) {
            super(context);
            // 加载文件夹项布局
            inflate(context, R.layout.folder_list_item, this);
            mName = (TextView) findViewById(R.id.tv_folder_name);
        }

        /**
         * 绑定文件夹名称
         */
        public void bind(String name) {
            mName.setText(name);
        }
    }
}