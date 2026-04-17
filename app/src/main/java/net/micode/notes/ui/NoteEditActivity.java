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

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.model.WorkingNote.NoteSettingChangedListener;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.tool.ResourceParser.TextAppearanceResources;
import net.micode.notes.ui.DateTimePickerDialog.OnDateTimeSetListener;
import net.micode.notes.ui.NoteEditText.OnTextViewChangeListener;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 笔记编辑页面 (UI 层核心 Activity)
 *
 * 核心功能：
 * 1. 新建/查看/编辑普通笔记、通话记录笔记
 * 2. 笔记背景颜色切换（黄/红/蓝/绿/白）
 * 3. 字体大小设置（小/中/大/超大）
 * 4. 普通文本 / 清单模式 切换
 * 5. 设置/取消提醒闹钟
 * 6. 分享笔记、发送到桌面快捷方式
 * 7. 搜索结果高亮显示
 * 8. 自动保存、状态恢复
 *
 * 数据模型：WorkingNote
 * 交互模式：单文本编辑 / 多选清单编辑
 */
public class NoteEditActivity extends Activity implements OnClickListener,
        NoteSettingChangedListener, OnTextViewChangeListener {

    /**
     * 头部视图控件持有者
     * 封装修改时间、提醒图标、提醒时间、背景设置按钮
     */
    private class HeadViewHolder {
        public TextView tvModified;         // 修改时间
        public ImageView ivAlertIcon;       // 提醒小图标
        public TextView tvAlertDate;        // 提醒时间文字
        public ImageView ibSetBgColor;      // 背景颜色设置按钮
    }

    // 背景颜色按钮ID -> 颜色值 映射
    private static final Map<Integer, Integer> sBgSelectorBtnsMap = new HashMap<Integer, Integer>();
    static {
        sBgSelectorBtnsMap.put(R.id.iv_bg_yellow, ResourceParser.YELLOW);
        sBgSelectorBtnsMap.put(R.id.iv_bg_red, ResourceParser.RED);
        sBgSelectorBtnsMap.put(R.id.iv_bg_blue, ResourceParser.BLUE);
        sBgSelectorBtnsMap.put(R.id.iv_bg_green, ResourceParser.GREEN);
        sBgSelectorBtnsMap.put(R.id.iv_bg_white, ResourceParser.WHITE);
    }

    // 颜色值 -> 选中状态指示图 映射
    private static final Map<Integer, Integer> sBgSelectorSelectionMap = new HashMap<Integer, Integer>();
    static {
        sBgSelectorSelectionMap.put(ResourceParser.YELLOW, R.id.iv_bg_yellow_select);
        sBgSelectorSelectionMap.put(ResourceParser.RED, R.id.iv_bg_red_select);
        sBgSelectorSelectionMap.put(ResourceParser.BLUE, R.id.iv_bg_blue_select);
        sBgSelectorSelectionMap.put(ResourceParser.GREEN, R.id.iv_bg_green_select);
        sBgSelectorSelectionMap.put(ResourceParser.WHITE, R.id.iv_bg_white_select);
    }

    // 字体大小按钮ID -> 字体尺寸值 映射
    private static final Map<Integer, Integer> sFontSizeBtnsMap = new HashMap<Integer, Integer>();
    static {
        sFontSizeBtnsMap.put(R.id.ll_font_large, ResourceParser.TEXT_LARGE);
        sFontSizeBtnsMap.put(R.id.ll_font_small, ResourceParser.TEXT_SMALL);
        sFontSizeBtnsMap.put(R.id.ll_font_normal, ResourceParser.TEXT_MEDIUM);
        sFontSizeBtnsMap.put(R.id.ll_font_super, ResourceParser.TEXT_SUPER);
    }

    // 字体尺寸值 -> 选中状态指示图 映射
    private static final Map<Integer, Integer> sFontSelectorSelectionMap = new HashMap<Integer, Integer>();
    static {
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_LARGE, R.id.iv_large_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SMALL, R.id.iv_small_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_MEDIUM, R.id.iv_medium_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SUPER, R.id.iv_super_select);
    }

    private static final String TAG = "NoteEditActivity";

    private HeadViewHolder mNoteHeaderHolder;       // 头部控件
    private View mHeadViewPanel;                    // 头部整体布局
    private View mNoteBgColorSelector;              // 背景颜色选择面板
    private View mFontSizeSelector;                // 字体大小选择面板
    private EditText mNoteEditor;                  // 普通文本编辑器
    private View mNoteEditorPanel;                 // 编辑器外层布局
    private WorkingNote mWorkingNote;              // 笔记数据模型
    private SharedPreferences mSharedPrefs;        // 配置存储
    private int mFontSizeId;                       // 当前字体大小ID

    // 配置项：字体大小存储key
    private static final String PREFERENCE_FONT_SIZE = "pref_font_size";
    // 桌面快捷方式标题最大长度
    private static final int SHORTCUT_ICON_TITLE_MAX_LEN = 10;

    // 清单模式：勾选/未勾选 标记符号
    public static final String TAG_CHECKED = String.valueOf('\u221A');
    public static final String TAG_UNCHECKED = String.valueOf('\u25A1');

    private LinearLayout mEditTextList;            // 清单模式多行编辑器容器
    private String mUserQuery;                     // 搜索关键词（用于高亮）
    private Pattern mPattern;                      // 搜索正则匹配

    /**
     * Activity 创建：初始化布局、状态、资源
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.note_edit);

        // 从Intent或保存状态初始化笔记数据
        if (savedInstanceState == null && !initActivityState(getIntent())) {
            finish();
            return;
        }
        initResources();
    }

    /**
     * 恢复因内存不足被系统杀死的Activity状态
     */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey(Intent.EXTRA_UID)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.putExtra(Intent.EXTRA_UID, savedInstanceState.getLong(Intent.EXTRA_UID));
            if (!initActivityState(intent)) {
                finish();
                return;
            }
            Log.d(TAG, "Restoring from killed activity");
        }
    }

    /**
     * 根据Intent初始化Activity状态
     * 支持：查看已有笔记、新建笔记、通话记录笔记
     */
    private boolean initActivityState(Intent intent) {
        mWorkingNote = null;
        // 查看已有笔记
        if (TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())) {
            long noteId = intent.getLongExtra(Intent.EXTRA_UID, 0);
            mUserQuery = "";

            // 从搜索结果进入
            if (intent.hasExtra(SearchManager.EXTRA_DATA_KEY)) {
                noteId = Long.parseLong(intent.getStringExtra(SearchManager.EXTRA_DATA_KEY));
                mUserQuery = intent.getStringExtra(SearchManager.USER_QUERY);
            }

            // 笔记不存在则返回列表
            if (!DataUtils.visibleInNoteDatabase(getContentResolver(), noteId, Notes.TYPE_NOTE)) {
                Intent jump = new Intent(this, NotesListActivity.class);
                startActivity(jump);
                showToast(R.string.error_note_not_exist);
                finish();
                return false;
            } else {
                mWorkingNote = WorkingNote.load(this, noteId);
                if (mWorkingNote == null) {
                    Log.e(TAG, "load note failed with note id" + noteId);
                    finish();
                    return false;
                }
            }
            // 隐藏软键盘
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        // 新建/编辑笔记
        else if(TextUtils.equals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction())) {
            long folderId = intent.getLongExtra(Notes.INTENT_EXTRA_FOLDER_ID, 0);
            int widgetId = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            int widgetType = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_TYPE,
                    Notes.TYPE_WIDGET_INVALIDE);
            int bgResId = intent.getIntExtra(Notes.INTENT_EXTRA_BACKGROUND_ID,
                    ResourceParser.getDefaultBgId(this));

            // 处理通话记录笔记
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            long callDate = intent.getLongExtra(Notes.INTENT_EXTRA_CALL_DATE, 0);
            if (callDate != 0 && phoneNumber != null) {
                long noteId = DataUtils.getNoteIdByPhoneNumberAndCallDate(getContentResolver(), phoneNumber, callDate);
                if (noteId > 0) {
                    mWorkingNote = WorkingNote.load(this, noteId);
                } else {
                    mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType, bgResId);
                    mWorkingNote.convertToCallNote(phoneNumber, callDate);
                }
            } else {
                // 创建空白笔记
                mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType, bgResId);
            }
            // 自动弹出软键盘
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } else {
            Log.e(TAG, "Intent not specified action, should not support");
            finish();
            return false;
        }
        // 设置笔记状态变化监听
        mWorkingNote.setOnSettingStatusChangedListener(this);
        return true;
    }

    /**
     * 页面可见：初始化笔记显示内容
     */
    @Override
    protected void onResume() {
        super.onResume();
        initNoteScreen();
    }

    /**
     * 初始化笔记编辑界面：文字、背景、时间、提醒
     */
    private void initNoteScreen() {
        // 设置字体
        mNoteEditor.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));

        // 清单模式/普通模式切换显示
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mWorkingNote.getContent());
        } else {
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            mNoteEditor.setSelection(mNoteEditor.getText().length());
        }

        // 隐藏所有背景选中标记
        for (Integer id : sBgSelectorSelectionMap.keySet()) {
            findViewById(sBgSelectorSelectionMap.get(id)).setVisibility(View.GONE);
        }

        // 设置背景
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());

        // 设置修改时间
        mNoteHeaderHolder.tvModified.setText(DateUtils.formatDateTime(this,
                mWorkingNote.getModifiedDate(), DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_TIME
                        | DateUtils.FORMAT_SHOW_YEAR));

        // 显示提醒信息
        showAlertHeader();
    }

    /**
     * 显示/隐藏提醒头部：过期/即将提醒
     */
    private void showAlertHeader() {
        if (mWorkingNote.hasClockAlert()) {
            long time = System.currentTimeMillis();
            if (time > mWorkingNote.getAlertDate()) {
                mNoteHeaderHolder.tvAlertDate.setText(R.string.note_alert_expired);
            } else {
                mNoteHeaderHolder.tvAlertDate.setText(DateUtils.getRelativeTimeSpanString(
                        mWorkingNote.getAlertDate(), time, DateUtils.MINUTE_IN_MILLIS));
            }
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.VISIBLE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.VISIBLE);
        } else {
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.GONE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.GONE);
        }
    }

    /**
     * 新Intent跳转：重新初始化页面
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        initActivityState(intent);
    }

    /**
     * 保存状态：防止被系统杀死后丢失未保存笔记
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        outState.putLong(Intent.EXTRA_UID, mWorkingNote.getNoteId());
    }

    /**
     * 触摸分发：点击外部关闭颜色/字体选择面板
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE && !inRangeOfView(mNoteBgColorSelector, ev)) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        }
        if (mFontSizeSelector.getVisibility() == View.VISIBLE && !inRangeOfView(mFontSizeSelector, ev)) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 判断触摸点是否在指定View范围内
     */
    private boolean inRangeOfView(View view, MotionEvent ev) {
        int []location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        return !(ev.getX() < x || ev.getX() > (x + view.getWidth()) || ev.getY() < y || ev.getY() > (y + view.getHeight()));
    }

    /**
     * 初始化所有控件、事件监听、配置
     */
    private void initResources() {
        mHeadViewPanel = findViewById(R.id.note_title);
        mNoteHeaderHolder = new HeadViewHolder();
        mNoteHeaderHolder.tvModified = findViewById(R.id.tv_modified_date);
        mNoteHeaderHolder.ivAlertIcon = findViewById(R.id.iv_alert_icon);
        mNoteHeaderHolder.tvAlertDate = findViewById(R.id.tv_alert_date);
        mNoteHeaderHolder.ibSetBgColor = findViewById(R.id.btn_set_bg_color);
        mNoteHeaderHolder.ibSetBgColor.setOnClickListener(this);

        mNoteEditor = findViewById(R.id.note_edit_view);
        mNoteEditorPanel = findViewById(R.id.sv_note_edit);
        mNoteBgColorSelector = findViewById(R.id.note_bg_color_selector);

        // 背景颜色按钮监听
        for (int id : sBgSelectorBtnsMap.keySet()) {
            ImageView iv = findViewById(id);
            iv.setOnClickListener(this);
        }

        mFontSizeSelector = findViewById(R.id.font_size_selector);
        // 字体大小按钮监听
        for (int id : sFontSizeBtnsMap.keySet()) {
            View view = findViewById(id);
            view.setOnClickListener(this);
        };

        // 读取字体大小配置
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mFontSizeId = mSharedPrefs.getInt(PREFERENCE_FONT_SIZE, ResourceParser.BG_DEFAULT_FONT_SIZE);
        if(mFontSizeId >= TextAppearanceResources.getResourcesSize()) {
            mFontSizeId = ResourceParser.BG_DEFAULT_FONT_SIZE;
        }

        mEditTextList = findViewById(R.id.note_edit_list);
    }

    /**
     * 页面暂停：自动保存笔记
     */
    @Override
    protected void onPause() {
        super.onPause();
        saveNote();
        clearSettingState();
    }

    /**
     * 更新桌面小部件（笔记修改后同步）
     */
    private void updateWidget() {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }

        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{mWorkingNote.getWidgetId()});
        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    /**
     * 点击事件：背景、字体大小选择
     */
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_set_bg_color) {
            // 打开背景选择面板
            mNoteBgColorSelector.setVisibility(View.VISIBLE);
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(View.VISIBLE);
        } else if (sBgSelectorBtnsMap.containsKey(id)) {
            // 切换背景颜色
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(View.GONE);
            mWorkingNote.setBgColorId(sBgSelectorBtnsMap.get(id));
            mNoteBgColorSelector.setVisibility(View.GONE);
        } else if (sFontSizeBtnsMap.containsKey(id)) {
            // 切换字体大小
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.GONE);
            mFontSizeId = sFontSizeBtnsMap.get(id);
            mSharedPrefs.edit().putInt(PREFERENCE_FONT_SIZE, mFontSizeId).commit();
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);

            // 刷新字体显示
            if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                getWorkingText();
                switchToListMode(mWorkingNote.getContent());
            } else {
                mNoteEditor.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
            }
            mFontSizeSelector.setVisibility(View.GONE);
        }
    }

    /**
     * 返回键：优先关闭面板，再保存退出
     */
    @Override
    public void onBackPressed() {
        if(clearSettingState()) {
            return;
        }
        saveNote();
        super.onBackPressed();
    }

    /**
     * 关闭颜色/字体选择面板
     */
    private boolean clearSettingState() {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        } else if (mFontSizeSelector.getVisibility() == View.VISIBLE) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    /**
     * 背景颜色变化：刷新界面
     */
    public void onBackgroundColorChanged() {
        findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(View.VISIBLE);
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
    }

    /**
     * 准备菜单：根据笔记类型加载不同菜单
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isFinishing()) return true;
        clearSettingState();
        menu.clear();
        if (mWorkingNote.getFolderId() == Notes.ID_CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_note_edit, menu);
        } else {
            getMenuInflater().inflate(R.menu.note_edit, menu);
        }

        // 切换清单/普通模式文字
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_normal_mode);
        } else {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_list_mode);
        }

        // 根据提醒状态显示设置/删除菜单项
        if (mWorkingNote.hasClockAlert()) {
            menu.findItem(R.id.menu_alert).setVisible(false);
            menu.findItem(R.id.menu_delete_remind).setVisible(true);
        } else {
            menu.findItem(R.id.menu_alert).setVisible(true);
            menu.findItem(R.id.menu_delete_remind).setVisible(false);
        }
        return true;
    }

    /**
     * 菜单点击处理
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_new_note) {
            createNewNote();
        } else if (itemId == R.id.menu_delete) {
            // 删除确认
            new AlertDialog.Builder(this)
                    .setTitle(R.string.alert_title_delete)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.alert_message_delete_note)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        deleteCurrentNote();
                        finish();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else if (itemId == R.id.menu_font_size) {
            // 打开字体选择面板
            mFontSizeSelector.setVisibility(View.VISIBLE);
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
        } else if (itemId == R.id.menu_list_mode) {
            // 切换清单模式
            mWorkingNote.setCheckListMode(mWorkingNote.getCheckListMode() == 0 ?
                    TextNote.MODE_CHECK_LIST : 0);
        } else if (itemId == R.id.menu_share) {
            // 分享笔记
            getWorkingText();
            sendTo(this, mWorkingNote.getContent());
        } else if (itemId == R.id.menu_send_to_desktop) {
            // 发送到桌面快捷方式
            sendToDesktop();
        } else if (itemId == R.id.menu_alert) {
            // 设置提醒
            setReminder();
        } else if (itemId == R.id.menu_delete_remind) {
            // 删除提醒
            mWorkingNote.setAlertDate(0, false);
        }
        return true;
    }

    /**
     * 弹出时间选择器设置提醒
     */
    private void setReminder() {
        DateTimePickerDialog d = new DateTimePickerDialog(this, System.currentTimeMillis());
        d.setOnDateTimeSetListener((dialog, date) -> mWorkingNote.setAlertDate(date, true));
        d.show();
    }

    /**
     * 分享笔记文本
     */
    private void sendTo(Context context, String info) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, info);
        intent.setType("text/plain");
        context.startActivity(intent);
    }

    /**
     * 新建笔记：保存当前并重启Activity
     */
    private void createNewNote() {
        saveNote();
        finish();
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mWorkingNote.getFolderId());
        startActivity(intent);
    }

    /**
     * 删除当前笔记（同步模式移入回收站，否则直接删除）
     */
    private void deleteCurrentNote() {
        if (mWorkingNote.existInDatabase()) {
            HashSet<Long> ids = new HashSet<>();
            long id = mWorkingNote.getNoteId();
            if (id != Notes.ID_ROOT_FOLDER) {
                ids.add(id);
            }
            if (!isSyncMode()) {
                DataUtils.batchDeleteNotes(getContentResolver(), ids);
            } else {
                DataUtils.batchMoveToFolder(getContentResolver(), ids, Notes.ID_TRASH_FOLER);
            }
        }
        mWorkingNote.markDeleted(true);
    }

    /**
     * 是否开启Google同步模式
     */
    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    /**
     * 提醒设置变化：更新闹钟服务
     */
    public void onClockAlertChanged(long date, boolean set) {
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        if (mWorkingNote.getNoteId() > 0) {
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mWorkingNote.getNoteId()));
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            showAlertHeader();
            if(!set) {
                alarmManager.cancel(pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, date, pendingIntent);
            }
        } else {
            showToast(R.string.error_note_empty_for_clock);
        }
    }

    /**
     * 小部件变化：更新小部件
     */
    public void onWidgetChanged() {
        updateWidget();
    }

    /**
     * 清单项删除：合并到上一行
     */
    public void onEditTextDelete(int index, String text) {
        int childCount = mEditTextList.getChildCount();
        if (childCount == 1) return;

        for (int i = index + 1; i < childCount; i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text)).setIndex(i - 1);
        }
        mEditTextList.removeViewAt(index);
        NoteEditText edit = (NoteEditText) mEditTextList.getChildAt(index == 0 ? 0 : index - 1).findViewById(R.id.et_edit_text);
        int length = edit.length();
        edit.append(text);
        edit.requestFocus();
        edit.setSelection(length);
    }

    /**
     * 清单项回车：新增一行
     */
    public void onEditTextEnter(int index, String text) {
        View view = getListItem(text, index);
        mEditTextList.addView(view, index);
        NoteEditText edit = view.findViewById(R.id.et_edit_text);
        edit.requestFocus();
        edit.setSelection(0);
        for (int i = index + 1; i < mEditTextList.getChildCount(); i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text)).setIndex(i);
        }
    }

    /**
     * 切换到清单模式：按换行符拆分生成多行编辑器
     */
    private void switchToListMode(String text) {
        mEditTextList.removeAllViews();
        String[] items = text.split("\n");
        int index = 0;
        for (String item : items) {
            if(!TextUtils.isEmpty(item)) {
                mEditTextList.addView(getListItem(item, index++));
            }
        }
        mEditTextList.addView(getListItem("", index));
        mEditTextList.getChildAt(index).findViewById(R.id.et_edit_text).requestFocus();

        mNoteEditor.setVisibility(View.GONE);
        mEditTextList.setVisibility(View.VISIBLE);
    }

    /**
     * 搜索关键词高亮显示
     */
    private Spannable getHighlightQueryResult(String fullText, String userQuery) {
        SpannableString spannable = new SpannableString(fullText == null ? "" : fullText);
        if (!TextUtils.isEmpty(userQuery)) {
            mPattern = Pattern.compile(userQuery);
            Matcher m = mPattern.matcher(fullText);
            int start = 0;
            while (m.find(start)) {
                spannable.setSpan(new BackgroundColorSpan(getResources().getColor(R.color.user_query_highlight)),
                        m.start(), m.end(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                start = m.end();
            }
        }
        return spannable;
    }

    /**
     * 创建清单模式单行编辑器
     */
    private View getListItem(String item, int index) {
        View view = LayoutInflater.from(this).inflate(R.layout.note_edit_list_item, null);
        final NoteEditText edit = view.findViewById(R.id.et_edit_text);
        edit.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
        CheckBox cb = view.findViewById(R.id.cb_edit_item);

        // 勾选框监听：添加删除线
        cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
            }
        });

        // 解析勾选状态
        if (item.startsWith(TAG_CHECKED)) {
            cb.setChecked(true);
            edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            item = item.substring(TAG_CHECKED.length()).trim();
        } else if (item.startsWith(TAG_UNCHECKED)) {
            cb.setChecked(false);
            item = item.substring(TAG_UNCHECKED.length()).trim();
        }

        edit.setOnTextViewChangeListener(this);
        edit.setIndex(index);
        edit.setText(getHighlightQueryResult(item, mUserQuery));
        return view;
    }

    /**
     * 清单文本变化：控制勾选框显示
     */
    public void onTextChange(int index, boolean hasText) {
        if (index >= mEditTextList.getChildCount()) return;
        mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(hasText ? View.VISIBLE : View.GONE);
    }

    /**
     * 清单模式切换回调
     */
    public void onCheckListModeChanged(int oldMode, int newMode) {
        if (newMode == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mNoteEditor.getText().toString());
        } else {
            getWorkingText();
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            mEditTextList.setVisibility(View.GONE);
            mNoteEditor.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 获取编辑器内容并保存到WorkingNote
     */
    private boolean getWorkingText() {
        boolean hasChecked = false;
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                View view = mEditTextList.getChildAt(i);
                NoteEditText edit = view.findViewById(R.id.et_edit_text);
                if (!TextUtils.isEmpty(edit.getText())) {
                    if (((CheckBox) view.findViewById(R.id.cb_edit_item)).isChecked()) {
                        sb.append(TAG_CHECKED).append(" ").append(edit.getText()).append("\n");
                        hasChecked = true;
                    } else {
                        sb.append(TAG_UNCHECKED).append(" ").append(edit.getText()).append("\n");
                    }
                }
            }
            mWorkingNote.setWorkingText(sb.toString());
        } else {
            mWorkingNote.setWorkingText(mNoteEditor.getText().toString());
        }
        return hasChecked;
    }

    /**
     * 保存笔记到数据库
     */
    private boolean saveNote() {
        getWorkingText();
        boolean saved = mWorkingNote.saveNote();
        if (saved) {
            setResult(RESULT_OK);
        }
        return saved;
    }

    /**
     * 创建桌面快捷方式
     */
    private void sendToDesktop() {
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        if (mWorkingNote.getNoteId() > 0) {
            Intent sender = new Intent();
            Intent shortcutIntent = new Intent(this, NoteEditActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.putExtra(Intent.EXTRA_UID, mWorkingNote.getNoteId());
            sender.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            sender.putExtra(Intent.EXTRA_SHORTCUT_NAME, makeShortcutIconTitle(mWorkingNote.getContent()));
            sender.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app));
            sender.putExtra("duplicate", true);
            sender.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            showToast(R.string.info_note_enter_desktop);
            sendBroadcast(sender);
        } else {
            showToast(R.string.error_note_empty_for_send_to_desktop);
        }
    }

    /**
     * 生成快捷方式标题（截取前10字符）
     */
    private String makeShortcutIconTitle(String content) {
        content = content.replace(TAG_CHECKED, "").replace(TAG_UNCHECKED, "");
        return content.length() > SHORTCUT_ICON_TITLE_MAX_LEN ?
                content.substring(0, SHORTCUT_ICON_TITLE_MAX_LEN) : content;
    }

    /**
     * 显示Toast提示
     */
    private void showToast(int resId) {
        showToast(resId, Toast.LENGTH_SHORT);
    }

    private void showToast(int resId, int duration) {
        Toast.makeText(this, resId, duration).show();
    }
}