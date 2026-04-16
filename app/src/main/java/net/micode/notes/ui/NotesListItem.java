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
 * See the License for the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser.NoteItemBgResources;

/**
 * 笔记列表单项视图组件
 *
 * 功能：
 * 1. 承载笔记/文件夹/通话记录的单项布局
 * 2. 根据数据类型（笔记/文件夹/通话记录）显示不同样式
 * 3. 处理多选模式下的勾选框显示
 * 4. 根据位置（第一条/最后一条/普通）设置不同背景
 * 5. 显示提醒图标、修改时间、标题、联系人姓名
 *
 * 对应布局：note_item.xml
 * 数据来源：NoteItemData
 * 使用者：NotesListAdapter
 */
public class NotesListItem extends LinearLayout {
    // 提醒图标（闹钟/通话记录）
    private ImageView mAlert;
    // 笔记/文件夹标题
    private TextView mTitle;
    // 修改时间
    private TextView mTime;
    // 通话记录联系人姓名
    private TextView mCallName;
    // 列表项数据
    private NoteItemData mItemData;
    // 多选模式勾选框
    private CheckBox mCheckBox;

    /**
     * 构造方法：加载布局并初始化控件
     */
    public NotesListItem(Context context) {
        super(context);
        // 加载列表项布局
        inflate(context, R.layout.note_item, this);

        // 初始化控件
        mAlert = (ImageView) findViewById(R.id.iv_alert_icon);
        mTitle = (TextView) findViewById(R.id.tv_title);
        mTime = (TextView) findViewById(R.id.tv_time);
        mCallName = (TextView) findViewById(R.id.tv_name);
        mCheckBox = (CheckBox) findViewById(android.R.id.checkbox);
    }

    /**
     * 绑定数据到视图
     *
     * @param context    上下文
     * @param data       单项数据
     * @param choiceMode 是否开启多选模式
     * @param checked    当前项是否选中
     */
    public void bind(Context context, NoteItemData data, boolean choiceMode, boolean checked) {
        // ====================== 处理多选模式勾选框 ======================
        if (choiceMode && data.getType() == Notes.TYPE_NOTE) {
            // 笔记类型才显示勾选框
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setChecked(checked);
        } else {
            mCheckBox.setVisibility(View.GONE);
        }

        mItemData = data;

        // ====================== 根据数据类型显示不同UI ======================
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            // 1. 通话记录系统文件夹
            mCallName.setVisibility(View.GONE);
            mAlert.setVisibility(View.VISIBLE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);
            // 设置文件夹名称 + 笔记数量
            mTitle.setText(context.getString(R.string.call_record_folder_name)
                    + context.getString(R.string.format_folder_files_count, data.getNotesCount()));
            // 设置通话记录图标
            mAlert.setImageResource(R.drawable.call_record);

        } else if (data.getParentId() == Notes.ID_CALL_RECORD_FOLDER) {
            // 2. 通话记录子项
            mCallName.setVisibility(View.VISIBLE);
            mCallName.setText(data.getCallName()); // 显示联系人姓名
            mTitle.setTextAppearance(context, R.style.TextAppearanceSecondaryItem);
            mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet())); // 笔记摘要

            // 有提醒则显示闹钟图标
            if (data.hasAlert()) {
                mAlert.setImageResource(R.drawable.clock);
                mAlert.setVisibility(View.VISIBLE);
            } else {
                mAlert.setVisibility(View.GONE);
            }

        } else {
            // 3. 普通文件夹 / 普通笔记
            mCallName.setVisibility(View.GONE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);

            if (data.getType() == Notes.TYPE_FOLDER) {
                // 普通文件夹：显示名称 + 子项数量
                mTitle.setText(data.getSnippet()
                        + context.getString(R.string.format_folder_files_count, data.getNotesCount()));
                mAlert.setVisibility(View.GONE);
            } else {
                // 普通笔记：显示摘要 + 提醒图标
                mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
                if (data.hasAlert()) {
                    mAlert.setImageResource(R.drawable.clock);
                    mAlert.setVisibility(View.VISIBLE);
                } else {
                    mAlert.setVisibility(View.GONE);
                }
            }
        }

        // 设置修改时间为相对时间（如：5分钟前）
        mTime.setText(DateUtils.getRelativeTimeSpanString(data.getModifiedDate()));

        // 设置背景样式
        setBackground(data);
    }

    /**
     * 根据笔记位置和类型设置不同背景
     * 区分：第一条 / 最后一条 / 单独一条 / 普通项 / 文件夹
     */
    private void setBackground(NoteItemData data) {
        int bgColorId = data.getBgColorId();

        if (data.getType() == Notes.TYPE_NOTE) {
            // 笔记项：根据位置使用不同背景资源
            if (data.isSingle() || data.isOneFollowingFolder()) {
                // 单独一条 / 文件夹后仅一条
                setBackgroundResource(NoteItemBgResources.getNoteBgSingleRes(bgColorId));
            } else if (data.isLast()) {
                // 最后一条
                setBackgroundResource(NoteItemBgResources.getNoteBgLastRes(bgColorId));
            } else if (data.isFirst() || data.isMultiFollowingFolder()) {
                // 第一条 / 文件夹后有多条
                setBackgroundResource(NoteItemBgResources.getNoteBgFirstRes(bgColorId));
            } else {
                // 普通中间项
                setBackgroundResource(NoteItemBgResources.getNoteBgNormalRes(bgColorId));
            }
        } else {
            // 文件夹项：统一背景
            setBackgroundResource(NoteItemBgResources.getFolderBgRes());
        }
    }

    /**
     * 获取当前列表项绑定的数据
     */
    public NoteItemData getItemData() {
        return mItemData;
    }
}