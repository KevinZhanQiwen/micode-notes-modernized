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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;

/**
 * 笔记设置界面
 *
 * 功能：
 * 1. 绑定 Google 账号，实现 GTask 任务同步
 * 2. 手动触发同步 / 取消同步
 * 3. 显示最后同步时间
 * 4. 切换/移除同步账号
 * 5. 接收同步服务广播，实时更新界面状态
 */
public class NotesPreferenceActivity extends PreferenceActivity {
    // SharedPreferences 文件名
    public static final String PREFERENCE_NAME = "notes_preferences";

    // 同步账号名称
    public static final String PREFERENCE_SYNC_ACCOUNT_NAME = "pref_key_account_name";

    // 最后同步时间
    public static final String PREFERENCE_LAST_SYNC_TIME = "pref_last_sync_time";

    // 背景颜色设置
    public static final String PREFERENCE_SET_BG_COLOR_KEY = "pref_key_bg_random_appear";

    // 同步账号分类 Key
    private static final String PREFERENCE_SYNC_ACCOUNT_KEY = "pref_sync_account_key";

    // 账号筛选 Key
    private static final String AUTHORITIES_FILTER_KEY = "authorities";

    // 账号分类
    private PreferenceCategory mAccountCategory;

    // 同步状态广播接收器
    private GTaskReceiver mReceiver;

    // 原始账号列表
    private Account[] mOriAccounts;

    // 是否新增了账号
    private boolean mHasAddedAccount;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // 显示返回箭头
        getActionBar().setDisplayHomeAsUpEnabled(true);

        // 加载设置布局
        addPreferencesFromResource(R.xml.preferences);
        mAccountCategory = (PreferenceCategory) findPreference(PREFERENCE_SYNC_ACCOUNT_KEY);

        // 注册同步状态广播
        mReceiver = new GTaskReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(GTaskSyncService.GTASK_SERVICE_BROADCAST_NAME);
        registerReceiver(mReceiver, filter);

        // 添加设置头部布局
        View header = LayoutInflater.from(this).inflate(R.layout.settings_header, null);
        getListView().addHeaderView(header, null, true);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 如果用户新增了账号，自动设置为同步账号
        if (mHasAddedAccount) {
            Account[] accounts = getGoogleAccounts();
            if (mOriAccounts != null && accounts.length > mOriAccounts.length) {
                for (Account accountNew : accounts) {
                    boolean found = false;
                    for (Account accountOld : mOriAccounts) {
                        if (TextUtils.equals(accountOld.name, accountNew.name)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        setSyncAccount(accountNew.name);
                        break;
                    }
                }
            }
        }

        // 刷新UI
        refreshUI();
    }

    @Override
    protected void onDestroy() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        super.onDestroy();
    }

    /**
     * 加载账号设置项
     */
    private void loadAccountPreference() {
        mAccountCategory.removeAll();

        Preference accountPref = new Preference(this);
        final String defaultAccount = getSyncAccountName(this);
        accountPref.setTitle(R.string.preferences_account_title);
        accountPref.setSummary(R.string.preferences_account_summary);
        accountPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                if (!GTaskSyncService.isSyncing()) {
                    if (TextUtils.isEmpty(defaultAccount)) {
                        // 首次设置账号
                        showSelectAccountAlertDialog();
                    } else {
                        // 切换账号，提示风险
                        showChangeAccountConfirmAlertDialog();
                    }
                } else {
                    // 同步中不允许切换
                    Toast.makeText(NotesPreferenceActivity.this,
                                    R.string.preferences_toast_cannot_change_account, Toast.LENGTH_SHORT)
                            .show();
                }
                return true;
            }
        });

        mAccountCategory.addPreference(accountPref);
    }

    /**
     * 加载同步按钮和状态
     */
    private void loadSyncButton() {
        Button syncButton = (Button) findViewById(R.id.preference_sync_button);
        TextView lastSyncTimeView = (TextView) findViewById(R.id.prefenerece_sync_status_textview);

        // 设置按钮文字与点击事件
        if (GTaskSyncService.isSyncing()) {
            syncButton.setText(R.string.preferences_button_sync_cancel);
            syncButton.setOnClickListener(v -> GTaskSyncService.cancelSync(NotesPreferenceActivity.this));
        } else {
            syncButton.setText(R.string.preferences_button_sync_immediately);
            syncButton.setOnClickListener(v -> GTaskSyncService.startSync(NotesPreferenceActivity.this));
        }
        syncButton.setEnabled(!TextUtils.isEmpty(getSyncAccountName(this)));

        // 显示同步进度或最后同步时间
        if (GTaskSyncService.isSyncing()) {
            lastSyncTimeView.setText(GTaskSyncService.getProgressString());
            lastSyncTimeView.setVisibility(View.VISIBLE);
        } else {
            long lastSyncTime = getLastSyncTime(this);
            if (lastSyncTime != 0) {
                lastSyncTimeView.setText(getString(R.string.preferences_last_sync_time,
                        DateFormat.format(getString(R.string.preferences_last_sync_time_format), lastSyncTime)));
                lastSyncTimeView.setVisibility(View.VISIBLE);
            } else {
                lastSyncTimeView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * 刷新整个设置界面
     */
    private void refreshUI() {
        loadAccountPreference();
        loadSyncButton();
    }

    /**
     * 显示选择账号对话框
     */
    private void showSelectAccountAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(R.string.preferences_dialog_select_account_title);
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(R.string.preferences_dialog_select_account_tips);

        dialogBuilder.setCustomTitle(titleView);
        dialogBuilder.setPositiveButton(null, null);

        // 获取所有Google账号
        Account[] accounts = getGoogleAccounts();
        String defAccount = getSyncAccountName(this);

        mOriAccounts = accounts;
        mHasAddedAccount = false;

        if (accounts.length > 0) {
            CharSequence[] items = new CharSequence[accounts.length];
            final CharSequence[] itemMapping = items;
            int checkedItem = -1;
            int index = 0;
            for (Account account : accounts) {
                if (TextUtils.equals(account.name, defAccount)) {
                    checkedItem = index;
                }
                items[index++] = account.name;
            }
            dialogBuilder.setSingleChoiceItems(items, checkedItem,
                    (dialog, which) -> {
                        setSyncAccount(itemMapping[which].toString());
                        dialog.dismiss();
                        refreshUI();
                    });
        }

        // 添加账号入口
        View addAccountView = LayoutInflater.from(this).inflate(R.layout.add_account_text, null);
        dialogBuilder.setView(addAccountView);

        final AlertDialog dialog = dialogBuilder.show();
        addAccountView.setOnClickListener(v -> {
            mHasAddedAccount = true;
            Intent intent = new Intent("android.settings.ADD_ACCOUNT_SETTINGS");
            intent.putExtra(AUTHORITIES_FILTER_KEY, new String[]{"gmail-ls"});
            startActivityForResult(intent, -1);
            dialog.dismiss();
        });
    }

    /**
     * 显示修改/移除账号确认对话框
     */
    private void showChangeAccountConfirmAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_change_account_title, getSyncAccountName(this)));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(R.string.preferences_dialog_change_account_warn_msg);
        dialogBuilder.setCustomTitle(titleView);

        CharSequence[] menuItemArray = new CharSequence[]{
                getString(R.string.preferences_menu_change_account),
                getString(R.string.preferences_menu_remove_account),
                getString(R.string.preferences_menu_cancel)
        };
        dialogBuilder.setItems(menuItemArray, (dialog, which) -> {
            if (which == 0) {
                showSelectAccountAlertDialog();
            } else if (which == 1) {
                removeSyncAccount();
                refreshUI();
            }
        });
        dialogBuilder.show();
    }

    /**
     * 获取设备上所有Google账号
     */
    private Account[] getGoogleAccounts() {
        AccountManager accountManager = AccountManager.get(this);
        return accountManager.getAccountsByType("com.google");
    }

    /**
     * 设置同步账号，并清空原有同步数据
     */
    private void setSyncAccount(String account) {
        if (!getSyncAccountName(this).equals(account)) {
            SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, account);
            editor.commit();

            // 清空最后同步时间
            setLastSyncTime(this, 0);

            // 清空本地笔记的同步相关字段
            new Thread(() -> {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.GTASK_ID, "");
                values.put(NoteColumns.SYNC_ID, 0);
                getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
            }).start();

            Toast.makeText(this, getString(R.string.preferences_toast_success_set_accout, account), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 移除同步账号
     */
    private void removeSyncAccount() {
        SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.remove(PREFERENCE_SYNC_ACCOUNT_NAME);
        editor.remove(PREFERENCE_LAST_SYNC_TIME);
        editor.commit();

        // 清空同步信息
        new Thread(() -> {
            ContentValues values = new ContentValues();
            values.put(NoteColumns.GTASK_ID, "");
            values.put(NoteColumns.SYNC_ID, 0);
            getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
        }).start();
    }

    /**
     * 获取当前同步账号
     */
    public static String getSyncAccountName(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        return settings.getString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
    }

    /**
     * 设置最后同步时间
     */
    public static void setLastSyncTime(Context context, long time) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong(PREFERENCE_LAST_SYNC_TIME, time);
        editor.commit();
    }

    /**
     * 获取最后同步时间
     */
    public static long getLastSyncTime(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        return settings.getLong(PREFERENCE_LAST_SYNC_TIME, 0);
    }

    /**
     * 同步状态广播接收器
     */
    private class GTaskReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUI();
            if (intent.getBooleanExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_IS_SYNCING, false)) {
                TextView syncStatus = (TextView) findViewById(R.id.prefenerece_sync_status_textview);
                syncStatus.setText(intent.getStringExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_PROGRESS_MSG));
            }
        }
    }

    /**
     * ActionBar 返回键
     */
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Intent intent = new Intent(this, NotesListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            return true;
        }
        return false;
    }
}