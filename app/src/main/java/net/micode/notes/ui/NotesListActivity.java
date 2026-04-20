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
import android.app.AlertDialog;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class NotesListActivity extends Activity implements OnClickListener, OnItemLongClickListener {
    private static final int FOLDER_NOTE_LIST_QUERY_TOKEN = 0;

    private static final int FOLDER_LIST_QUERY_TOKEN      = 1;

    private static final int MENU_FOLDER_DELETE = 0;

    private static final int MENU_FOLDER_VIEW = 1;

    private static final int MENU_FOLDER_CHANGE_NAME = 2;

    private static final String PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction";

    private enum ListEditState {
        NOTE_LIST, SUB_FOLDER, CALL_RECORD_FOLDER
    };

    private ListEditState mState;

    private BackgroundQueryHandler mBackgroundQueryHandler;

    private NotesListAdapter mNotesListAdapter;

    private ListView mNotesListView;

    private Button mAddNewNote;

    private boolean mDispatch;

    private int mOriginY;

    private int mDispatchY;

    private TextView mTitleBar;

    private long mCurrentFolderId;

    private ContentResolver mContentResolver;

    private ModeCallback mModeCallBack;

    private static final String TAG = "NotesListActivity";

    public static final int NOTES_LISTVIEW_SCROLL_RATE = 30;

    private NoteItemData mFocusNoteDataItem;

    // Color filter: -1 = all, 0-4 = specific bg_color_id
    private static final int COLOR_FILTER_ALL = -1;
    private int mColorFilter = COLOR_FILTER_ALL;
    private View mColorFilterBar;
    private boolean mColorBarVisible = false;
    private float mSwipeStartY = 0f;
    private final List<View> mColorChips = new ArrayList<>();

    // Approximate chip fill colors to match note tile backgrounds
    private static final int[] NOTE_CHIP_COLORS = {
        0xFFF5C518, // YELLOW (0)
        0xFF5588BB, // BLUE   (1)
        0xFFF0F0F0, // WHITE  (2)
        0xFF7EC850, // GREEN  (3)
        0xFFE55B5B  // RED    (4)
    };

    private static final String NORMAL_SELECTION =
            NoteColumns.PARENT_ID + "=? AND " + NoteColumns.IS_PRIVATE + "=0";

    private static final String ROOT_FOLDER_SELECTION = "(" + NoteColumns.TYPE + "<>"
            + Notes.TYPE_SYSTEM + " AND " + NoteColumns.PARENT_ID + "=?"
            + " AND " + NoteColumns.IS_PRIVATE + "=0)" + " OR ("
            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER + " AND "
            + NoteColumns.NOTES_COUNT + ">0)";

    // Used when a colour filter is active (hides folders and call-record row)
    private static final String COLOR_FILTER_SELECTION =
            NoteColumns.TYPE + "=" + Notes.TYPE_NOTE
            + " AND " + NoteColumns.PARENT_ID + "=?"
            + " AND " + NoteColumns.IS_PRIVATE + "=0"
            + " AND " + NoteColumns.BG_COLOR_ID + "=?";

    private final static int REQUEST_CODE_OPEN_NODE       = 102;
    private final static int REQUEST_CODE_NEW_NODE        = 103;
    private final static int REQUEST_CODE_PRIVACY_SETUP   = 104;
    private final static int REQUEST_CODE_PRIVACY_UNLOCK  = 105;
    private final static int REQUEST_CODE_PICK_BG_IMAGE   = 106;

    // List background preferences
    private static final String PREF_LIST_BG_TYPE  = "pref_list_bg_type";
    private static final String PREF_LIST_BG_VALUE = "pref_list_bg_value";
    private static final String BG_TYPE_DEFAULT = "default";
    private static final String BG_TYPE_COLOR   = "color";
    private static final String BG_TYPE_IMAGE   = "image";

    private View mListRoot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_list);
        initResources();
        applyListBackground();

        /**
         * Insert an introduction when user firstly use this application
         */
        setAppInfoFromRawRes();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK
                && (requestCode == REQUEST_CODE_OPEN_NODE || requestCode == REQUEST_CODE_NEW_NODE)) {
            mNotesListAdapter.changeCursor(null);
        } else if (resultCode == RESULT_OK && requestCode == REQUEST_CODE_PRIVACY_SETUP) {
            // Setup completed – open the space right away
            startActivity(new Intent(this, PrivacySpaceActivity.class));
        } else if (resultCode == RESULT_OK && requestCode == REQUEST_CODE_PRIVACY_UNLOCK) {
            // Unlocked – open the space
            startActivity(new Intent(this, PrivacySpaceActivity.class));
        } else if (resultCode == RESULT_OK && requestCode == REQUEST_CODE_PICK_BG_IMAGE
                && data != null && data.getData() != null) {
            handlePickedBackgroundImage(data);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void handlePickedBackgroundImage(Intent data) {
        Uri uri = data.getData();
        // Try to acquire long-term permission for SAF URIs so the background
        // survives reboots and process restarts.
        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (flags == 0) {
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            }
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (SecurityException ignored) {
            // Some pickers (e.g. classic ACTION_PICK) don't grant persistable perms.
        }

        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putString(PREF_LIST_BG_TYPE, BG_TYPE_IMAGE)
                .putString(PREF_LIST_BG_VALUE, uri.toString())
                .apply();

        if (applyListBackground()) {
            Toast.makeText(this, R.string.bg_applied, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.bg_apply_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void setAppInfoFromRawRes() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sp.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) {
            StringBuilder sb = new StringBuilder();
            InputStream in = null;
            try {
                 in = getResources().openRawResource(R.raw.introduction);
                if (in != null) {
                    InputStreamReader isr = new InputStreamReader(in);
                    BufferedReader br = new BufferedReader(isr);
                    char [] buf = new char[1024];
                    int len = 0;
                    while ((len = br.read(buf)) > 0) {
                        sb.append(buf, 0, len);
                    }
                } else {
                    Log.e(TAG, "Read introduction file error");
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } finally {
                if(in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }

            WorkingNote note = WorkingNote.createEmptyNote(this, Notes.ID_ROOT_FOLDER,
                    AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                    ResourceParser.RED);
            note.setWorkingText(sb.toString());
            if (note.saveNote()) {
                sp.edit().putBoolean(PREFERENCE_ADD_INTRODUCTION, true).commit();
            } else {
                Log.e(TAG, "Save introduction note error");
                return;
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        startAsyncNotesListQuery();
    }

    private void initResources() {
        mContentResolver = this.getContentResolver();
        mBackgroundQueryHandler = new BackgroundQueryHandler(this.getContentResolver());
        mCurrentFolderId = Notes.ID_ROOT_FOLDER;
        mListRoot = findViewById(R.id.list_root);
        mNotesListView = (ListView) findViewById(R.id.notes_list);
        mNotesListView.addFooterView(LayoutInflater.from(this).inflate(R.layout.note_list_footer, null),
                null, false);
        mNotesListView.setOnItemClickListener(new OnListItemClickListener());
        mNotesListView.setOnItemLongClickListener(this);
        mNotesListAdapter = new NotesListAdapter(this);
        mNotesListView.setAdapter(mNotesListAdapter);
        mAddNewNote = (Button) findViewById(R.id.btn_new_note);
        mAddNewNote.setOnClickListener(this);
        mAddNewNote.setOnTouchListener(new NewNoteOnTouchListener());
        mDispatch = false;
        mDispatchY = 0;
        mOriginY = 0;
        mTitleBar = (TextView) findViewById(R.id.tv_title_bar);
        mState = ListEditState.NOTE_LIST;
        mModeCallBack = new ModeCallback();
        setupColorFilterBar();
    }

    private void setupColorFilterBar() {
        mColorFilterBar = findViewById(R.id.hsv_color_filter);
        LinearLayout container = (LinearLayout)
                ((HorizontalScrollView) mColorFilterBar).getChildAt(0);

        // "全部" chip — always index 0 in mColorChips, tagged -1
        addColorChip(container, COLOR_FILTER_ALL, 0xFFBBBBBB, getString(R.string.color_filter_all));
        for (int i = 0; i < NOTE_CHIP_COLORS.length; i++) {
            addColorChip(container, i, NOTE_CHIP_COLORS[i], null);
        }
        refreshChipStates();

        // Hidden above the top edge by default
        mColorFilterBar.setVisibility(View.GONE);
        mColorFilterBar.setAlpha(0f);
        mColorFilterBar.setTranslationY(-dpToPx(52));
        mColorBarVisible = false;

        // Detect swipe direction directly on the list — works even when list is short
        mNotesListView.setOnTouchListener((v, event) -> {
            if (mState != ListEditState.NOTE_LIST) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mSwipeStartY = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dy = event.getY() - mSwipeStartY;
                    if (dy > dpToPx(40) && !mColorBarVisible) {
                        showColorFilterBar();
                    } else if (dy < -dpToPx(30) && mColorBarVisible) {
                        hideColorFilterBar();
                    }
                    break;
            }
            return false; // let ListView handle clicks / scrolling normally
        });
    }

    private void showColorFilterBar() {
        if (mColorBarVisible) return;
        mColorBarVisible = true;
        mColorFilterBar.setTranslationY(-dpToPx(52));
        mColorFilterBar.setAlpha(0f);
        mColorFilterBar.setVisibility(View.VISIBLE);
        mColorFilterBar.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private void hideColorFilterBar() {
        if (!mColorBarVisible) return;
        mColorBarVisible = false;
        mColorFilterBar.animate()
                .translationY(-dpToPx(52))
                .alpha(0f)
                .setDuration(180)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    mColorFilterBar.setVisibility(View.GONE);
                    mColorFilterBar.setTranslationY(-dpToPx(52));
                })
                .start();
    }

    private void addColorChip(LinearLayout parent, final int colorId,
                               int fillColor, String label) {
        // chip (44dp) > ring (40dp, oval outline, shown when selected) > dot (28dp, solid fill)
        int chipSz = dpToPx(44);
        int ringSz = dpToPx(40);
        int dotSz  = dpToPx(28);
        int margin = dpToPx(3);

        FrameLayout chip = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(chipSz, chipSz);
        lp.setMargins(margin, 0, margin, 0);
        chip.setLayoutParams(lp);

        // Selection ring — independent oval view so its outline is never clipped by a rect
        View ring = new View(this);
        GradientDrawable ringShape = new GradientDrawable();
        ringShape.setShape(GradientDrawable.OVAL);
        ringShape.setColor(Color.TRANSPARENT);
        ringShape.setStroke(dpToPx(3), Color.WHITE);
        ring.setBackground(ringShape);
        ring.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams ringLp = new FrameLayout.LayoutParams(ringSz, ringSz);
        ringLp.gravity = Gravity.CENTER;
        ring.setLayoutParams(ringLp);
        chip.addView(ring);

        // Coloured dot
        View dot = new View(this);
        GradientDrawable dotShape = new GradientDrawable();
        dotShape.setShape(GradientDrawable.OVAL);
        dotShape.setColor(fillColor);
        if (fillColor == 0xFFF0F0F0) {
            dotShape.setStroke(dpToPx(1), 0xFFCCCCCC);
        }
        dot.setBackground(dotShape);
        FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dotSz, dotSz);
        dotLp.gravity = Gravity.CENTER;
        dot.setLayoutParams(dotLp);
        chip.addView(dot);

        // Label ("全部")
        if (label != null && !label.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(label);
            tv.setTextColor(0xFF444444);
            tv.setTextSize(9);
            tv.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams tvLp = new FrameLayout.LayoutParams(dotSz, dotSz);
            tvLp.gravity = Gravity.CENTER;
            tv.setLayoutParams(tvLp);
            chip.addView(tv);
        }

        chip.setTag(colorId);
        chip.setOnClickListener(v -> {
            int id = (int) chip.getTag();
            mColorFilter = (mColorFilter == id) ? COLOR_FILTER_ALL : id;
            refreshChipStates();
            startAsyncNotesListQuery();
        });

        parent.addView(chip);
        mColorChips.add(chip);
    }

    private void refreshChipStates() {
        for (View chip : mColorChips) {
            int id = (int) chip.getTag();
            boolean selected = (id == mColorFilter)
                    || (mColorFilter == COLOR_FILTER_ALL && id == COLOR_FILTER_ALL);
            // ring is always childAt(0)
            View ring = ((FrameLayout) chip).getChildAt(0);
            ring.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private class ModeCallback implements ListView.MultiChoiceModeListener, OnMenuItemClickListener {
        private DropdownMenu mDropDownMenu;
        private ActionMode mActionMode;
        private MenuItem mMoveMenu;

        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.note_list_options, menu);
            menu.findItem(R.id.delete).setOnMenuItemClickListener(this);
            mMoveMenu = menu.findItem(R.id.move);
            if (mFocusNoteDataItem.getParentId() == Notes.ID_CALL_RECORD_FOLDER
                    || DataUtils.getUserFolderCount(mContentResolver) == 0) {
                mMoveMenu.setVisible(false);
            } else {
                mMoveMenu.setVisible(true);
                mMoveMenu.setOnMenuItemClickListener(this);
            }
            mActionMode = mode;
            mNotesListAdapter.setChoiceMode(true);
            mNotesListView.setLongClickable(false);
            mAddNewNote.setVisibility(View.GONE);

            View customView = LayoutInflater.from(NotesListActivity.this).inflate(
                    R.layout.note_list_dropdown_menu, null);
            mode.setCustomView(customView);
            mDropDownMenu = new DropdownMenu(NotesListActivity.this,
                    (Button) customView.findViewById(R.id.selection_menu),
                    R.menu.note_list_dropdown);
            mDropDownMenu.setOnDropdownMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){
                public boolean onMenuItemClick(MenuItem item) {
                    mNotesListAdapter.selectAll(!mNotesListAdapter.isAllSelected());
                    updateMenu();
                    return true;
                }

            });
            return true;
        }

        private void updateMenu() {
            int selectedCount = mNotesListAdapter.getSelectedCount();
            // Update dropdown menu
            String format = getResources().getString(R.string.menu_select_title, selectedCount);
            mDropDownMenu.setTitle(format);
            MenuItem item = mDropDownMenu.findItem(R.id.action_select_all);
            if (item != null) {
                if (mNotesListAdapter.isAllSelected()) {
                    item.setChecked(true);
                    item.setTitle(R.string.menu_deselect_all);
                } else {
                    item.setChecked(false);
                    item.setTitle(R.string.menu_select_all);
                }
            }
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            // TODO Auto-generated method stub
            return false;
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            // TODO Auto-generated method stub
            return false;
        }

        public void onDestroyActionMode(ActionMode mode) {
            mNotesListAdapter.setChoiceMode(false);
            mNotesListView.setLongClickable(true);
            mAddNewNote.setVisibility(View.VISIBLE);
        }

        public void finishActionMode() {
            mActionMode.finish();
        }

        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                boolean checked) {
            mNotesListAdapter.setCheckedItem(position, checked);
            updateMenu();
        }

        public boolean onMenuItemClick(MenuItem item) {
            if (mNotesListAdapter.getSelectedCount() == 0) {
                Toast.makeText(NotesListActivity.this, getString(R.string.menu_select_none),
                        Toast.LENGTH_SHORT).show();
                return true;
            }

            int itemId = item.getItemId();
            if (itemId == R.id.delete) {
                AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                builder.setTitle(getString(R.string.alert_title_delete));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(getString(R.string.alert_message_delete_notes,
                        mNotesListAdapter.getSelectedCount()));
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                batchDelete();
                            }
                        });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
            } else if (itemId == R.id.move) {
                startQueryDestinationFolders();
            } else if (itemId == R.id.move_to_privacy) {
                moveSelectedToPrivacySpace();
            } else {
                return false;
            }
            return true;
        }
    }

    private void moveSelectedToPrivacySpace() {
        HashSet<Long> ids = mNotesListAdapter.getSelectedItemIds();
        ContentValues values = new ContentValues();
        values.put(NoteColumns.IS_PRIVATE, 1);
        for (long id : ids) {
            mContentResolver.update(Notes.CONTENT_NOTE_URI, values,
                    NoteColumns.ID + "=?", new String[]{String.valueOf(id)});
        }
        Toast.makeText(this, R.string.privacy_move_in_done, Toast.LENGTH_SHORT).show();
        mModeCallBack.finishActionMode();
    }

    private class NewNoteOnTouchListener implements OnTouchListener {

        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    Display display = getWindowManager().getDefaultDisplay();
                    int screenHeight = display.getHeight();
                    int newNoteViewHeight = mAddNewNote.getHeight();
                    int start = screenHeight - newNoteViewHeight;
                    int eventY = start + (int) event.getY();
                    /**
                     * Minus TitleBar's height
                     */
                    if (mState == ListEditState.SUB_FOLDER) {
                        eventY -= mTitleBar.getHeight();
                        start -= mTitleBar.getHeight();
                    }
                    /**
                     * HACKME:When click the transparent part of "New Note" button, dispatch
                     * the event to the list view behind this button. The transparent part of
                     * "New Note" button could be expressed by formula y=-0.12x+94（Unit:pixel）
                     * and the line top of the button. The coordinate based on left of the "New
                     * Note" button. The 94 represents maximum height of the transparent part.
                     * Notice that, if the background of the button changes, the formula should
                     * also change. This is very bad, just for the UI designer's strong requirement.
                     */
                    if (event.getY() < (event.getX() * (-0.12) + 94)) {
                        View view = mNotesListView.getChildAt(mNotesListView.getChildCount() - 1
                                - mNotesListView.getFooterViewsCount());
                        if (view != null && view.getBottom() > start
                                && (view.getTop() < (start + 94))) {
                            mOriginY = (int) event.getY();
                            mDispatchY = eventY;
                            event.setLocation(event.getX(), mDispatchY);
                            mDispatch = true;
                            return mNotesListView.dispatchTouchEvent(event);
                        }
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (mDispatch) {
                        mDispatchY += (int) event.getY() - mOriginY;
                        event.setLocation(event.getX(), mDispatchY);
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                    break;
                }
                default: {
                    if (mDispatch) {
                        event.setLocation(event.getX(), mDispatchY);
                        mDispatch = false;
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                    break;
                }
            }
            return false;
        }

    };

    private void startAsyncNotesListQuery() {
        String selection;
        String[] args;
        if (mColorFilter != COLOR_FILTER_ALL && mState == ListEditState.NOTE_LIST) {
            selection = COLOR_FILTER_SELECTION;
            args = new String[]{
                String.valueOf(Notes.ID_ROOT_FOLDER),
                String.valueOf(mColorFilter)
            };
        } else {
            selection = (mCurrentFolderId == Notes.ID_ROOT_FOLDER)
                    ? ROOT_FOLDER_SELECTION : NORMAL_SELECTION;
            args = new String[]{String.valueOf(mCurrentFolderId)};
        }
        mBackgroundQueryHandler.startQuery(FOLDER_NOTE_LIST_QUERY_TOKEN, null,
                Notes.CONTENT_NOTE_URI, NoteItemData.PROJECTION, selection, args,
                NoteColumns.TYPE + " DESC," + NoteColumns.MODIFIED_DATE + " DESC");
    }

    private final class BackgroundQueryHandler extends AsyncQueryHandler {
        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            switch (token) {
                case FOLDER_NOTE_LIST_QUERY_TOKEN:
                    mNotesListAdapter.changeCursor(cursor);
                    break;
                case FOLDER_LIST_QUERY_TOKEN:
                    if (cursor != null && cursor.getCount() > 0) {
                        showFolderListMenu(cursor);
                    } else {
                        Log.e(TAG, "Query folder failed");
                    }
                    break;
                default:
                    return;
            }
        }
    }

    private void showFolderListMenu(Cursor cursor) {
        AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
        builder.setTitle(R.string.menu_title_select_folder);
        final FoldersListAdapter adapter = new FoldersListAdapter(this, cursor);
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                DataUtils.batchMoveToFolder(mContentResolver,
                        mNotesListAdapter.getSelectedItemIds(), adapter.getItemId(which));
                Toast.makeText(
                        NotesListActivity.this,
                        getString(R.string.format_move_notes_to_folder,
                                mNotesListAdapter.getSelectedCount(),
                                adapter.getFolderName(NotesListActivity.this, which)),
                        Toast.LENGTH_SHORT).show();
                mModeCallBack.finishActionMode();
            }
        });
        builder.show();
    }

    private void createNewNote() {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mCurrentFolderId);
        this.startActivityForResult(intent, REQUEST_CODE_NEW_NODE);
    }

    private void batchDelete() {
        new AsyncTask<Void, Void, HashSet<AppWidgetAttribute>>() {
            protected HashSet<AppWidgetAttribute> doInBackground(Void... unused) {
                HashSet<AppWidgetAttribute> widgets = mNotesListAdapter.getSelectedWidget();
                if (!isSyncMode()) {
                    // if not synced, delete notes directly
                    if (DataUtils.batchDeleteNotes(mContentResolver, mNotesListAdapter
                            .getSelectedItemIds())) {
                    } else {
                        Log.e(TAG, "Delete notes error, should not happens");
                    }
                } else {
                    // in sync mode, we'll move the deleted note into the trash
                    // folder
                    if (!DataUtils.batchMoveToFolder(mContentResolver, mNotesListAdapter
                            .getSelectedItemIds(), Notes.ID_TRASH_FOLER)) {
                        Log.e(TAG, "Move notes to trash folder error, should not happens");
                    }
                }
                return widgets;
            }

            @Override
            protected void onPostExecute(HashSet<AppWidgetAttribute> widgets) {
                if (widgets != null) {
                    for (AppWidgetAttribute widget : widgets) {
                        if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                                && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                            updateWidget(widget.widgetId, widget.widgetType);
                        }
                    }
                }
                mModeCallBack.finishActionMode();
            }
        }.execute();
    }

    private void deleteFolder(long folderId) {
        if (folderId == Notes.ID_ROOT_FOLDER) {
            Log.e(TAG, "Wrong folder id, should not happen " + folderId);
            return;
        }

        HashSet<Long> ids = new HashSet<Long>();
        ids.add(folderId);
        HashSet<AppWidgetAttribute> widgets = DataUtils.getFolderNoteWidget(mContentResolver,
                folderId);
        if (!isSyncMode()) {
            // if not synced, delete folder directly
            DataUtils.batchDeleteNotes(mContentResolver, ids);
        } else {
            // in sync mode, we'll move the deleted folder into the trash folder
            DataUtils.batchMoveToFolder(mContentResolver, ids, Notes.ID_TRASH_FOLER);
        }
        if (widgets != null) {
            for (AppWidgetAttribute widget : widgets) {
                if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                        && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                    updateWidget(widget.widgetId, widget.widgetType);
                }
            }
        }
    }

    private void openNode(NoteItemData data) {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, data.getId());
        this.startActivityForResult(intent, REQUEST_CODE_OPEN_NODE);
    }

    private void openFolder(NoteItemData data) {
        mCurrentFolderId = data.getId();
        // Reset color filter and hide bar immediately when entering any subfolder
        mColorFilter = COLOR_FILTER_ALL;
        mColorFilterBar.animate().cancel();
        mColorFilterBar.setVisibility(View.GONE);
        mColorFilterBar.setAlpha(0f);
        mColorFilterBar.setTranslationY(0f);
        mColorBarVisible = false;
        startAsyncNotesListQuery();
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mState = ListEditState.CALL_RECORD_FOLDER;
            mAddNewNote.setVisibility(View.GONE);
        } else {
            mState = ListEditState.SUB_FOLDER;
        }
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mTitleBar.setText(R.string.call_record_folder_name);
        } else {
            mTitleBar.setText(data.getSnippet());
        }
        mTitleBar.setVisibility(View.VISIBLE);
    }

    public void onClick(View v) {
        if (v.getId() == R.id.btn_new_note) {
            createNewNote();
        }
    }

    private void showSoftInput() {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }

    private void hideSoftInput(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showCreateOrModifyFolderDialog(final boolean create) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        final EditText etName = (EditText) view.findViewById(R.id.et_foler_name);
        showSoftInput();
        if (!create) {
            if (mFocusNoteDataItem != null) {
                etName.setText(mFocusNoteDataItem.getSnippet());
                builder.setTitle(getString(R.string.menu_folder_change_name));
            } else {
                Log.e(TAG, "The long click data item is null");
                return;
            }
        } else {
            etName.setText("");
            builder.setTitle(this.getString(R.string.menu_create_folder));
        }

        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                hideSoftInput(etName);
            }
        });

        final Dialog dialog = builder.setView(view).show();
        final Button positive = (Button)dialog.findViewById(android.R.id.button1);
        positive.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                hideSoftInput(etName);
                String name = etName.getText().toString();
                if (DataUtils.checkVisibleFolderName(mContentResolver, name)) {
                    Toast.makeText(NotesListActivity.this, getString(R.string.folder_exist, name),
                            Toast.LENGTH_LONG).show();
                    etName.setSelection(0, etName.length());
                    return;
                }
                if (!create) {
                    if (!TextUtils.isEmpty(name)) {
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SNIPPET, name);
                        values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                        values.put(NoteColumns.LOCAL_MODIFIED, 1);
                        mContentResolver.update(Notes.CONTENT_NOTE_URI, values, NoteColumns.ID
                                + "=?", new String[] {
                            String.valueOf(mFocusNoteDataItem.getId())
                        });
                    }
                } else if (!TextUtils.isEmpty(name)) {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.SNIPPET, name);
                    values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                    mContentResolver.insert(Notes.CONTENT_NOTE_URI, values);
                }
                dialog.dismiss();
            }
        });

        if (TextUtils.isEmpty(etName.getText())) {
            positive.setEnabled(false);
        }
        /**
         * When the name edit text is null, disable the positive button
         */
        etName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // TODO Auto-generated method stub

            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (TextUtils.isEmpty(etName.getText())) {
                    positive.setEnabled(false);
                } else {
                    positive.setEnabled(true);
                }
            }

            public void afterTextChanged(Editable s) {
                // TODO Auto-generated method stub

            }
        });
    }

    @Override
    public void onBackPressed() {
        switch (mState) {
            case SUB_FOLDER:
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                mState = ListEditState.NOTE_LIST;
                startAsyncNotesListQuery();
                mTitleBar.setVisibility(View.GONE);
                break;
            case CALL_RECORD_FOLDER:
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                mState = ListEditState.NOTE_LIST;
                mAddNewNote.setVisibility(View.VISIBLE);
                mTitleBar.setVisibility(View.GONE);
                startAsyncNotesListQuery();
                break;
            case NOTE_LIST:
                super.onBackPressed();
                break;
            default:
                break;
        }
    }

    private void updateWidget(int appWidgetId, int appWidgetType) {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (appWidgetType == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (appWidgetType == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }

        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {
            appWidgetId
        });

        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    private final OnCreateContextMenuListener mFolderOnCreateContextMenuListener = new OnCreateContextMenuListener() {
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            if (mFocusNoteDataItem != null) {
                menu.setHeaderTitle(mFocusNoteDataItem.getSnippet());
                menu.add(0, MENU_FOLDER_VIEW, 0, R.string.menu_folder_view);
                menu.add(0, MENU_FOLDER_DELETE, 0, R.string.menu_folder_delete);
                menu.add(0, MENU_FOLDER_CHANGE_NAME, 0, R.string.menu_folder_change_name);
            }
        }
    };

    @Override
    public void onContextMenuClosed(Menu menu) {
        if (mNotesListView != null) {
            mNotesListView.setOnCreateContextMenuListener(null);
        }
        super.onContextMenuClosed(menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mFocusNoteDataItem == null) {
            Log.e(TAG, "The long click data item is null");
            return false;
        }
        switch (item.getItemId()) {
            case MENU_FOLDER_VIEW:
                openFolder(mFocusNoteDataItem);
                break;
            case MENU_FOLDER_DELETE:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.alert_title_delete));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(getString(R.string.alert_message_delete_folder));
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                deleteFolder(mFocusNoteDataItem.getId());
                            }
                        });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
                break;
            case MENU_FOLDER_CHANGE_NAME:
                showCreateOrModifyFolderDialog(false);
                break;
            default:
                break;
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        if (mState == ListEditState.NOTE_LIST) {
            getMenuInflater().inflate(R.menu.note_list, menu);
            // set sync or sync_cancel
            menu.findItem(R.id.menu_sync).setTitle(
                    GTaskSyncService.isSyncing() ? R.string.menu_sync_cancel : R.string.menu_sync);
        } else if (mState == ListEditState.SUB_FOLDER) {
            getMenuInflater().inflate(R.menu.sub_folder, menu);
        } else if (mState == ListEditState.CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_record_folder, menu);
        } else {
            Log.e(TAG, "Wrong state:" + mState);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_new_folder) {
            showCreateOrModifyFolderDialog(true);
        } else if (itemId == R.id.menu_export_text) {
            exportNoteToText();
        } else if (itemId == R.id.menu_sync) {
            if (isSyncMode()) {
                if (TextUtils.equals(item.getTitle(), getString(R.string.menu_sync))) {
                    GTaskSyncService.startSync(this);
                } else {
                    GTaskSyncService.cancelSync(this);
                }
            } else {
                startPreferenceActivity();
            }
        } else if (itemId == R.id.menu_setting) {
            startPreferenceActivity();
        } else if (itemId == R.id.menu_new_note) {
            createNewNote();
        } else if (itemId == R.id.menu_search) {
            onSearchRequested();
        } else if (itemId == R.id.menu_privacy_space) {
            openPrivacySpace();
        } else if (itemId == R.id.menu_change_bg) {
            showChangeBackgroundDialog();
        }
        return true;
    }

    /**
     * Shows a chooser that lets the user switch the notes list background.
     * Options: default drawable, a few built-in solid colors, or an image
     * picked from the gallery. The choice is persisted in SharedPreferences.
     */
    private void showChangeBackgroundDialog() {
        final String[] items = new String[] {
                getString(R.string.bg_default),
                getString(R.string.bg_blue),
                getString(R.string.bg_green),
                getString(R.string.bg_beige),
                getString(R.string.bg_pink),
                getString(R.string.bg_pick_image),
        };
        // Index aligned with `items`; null entries are not color presets.
        final String[] colorValues = {
                null,
                "#FFE3F2FD",
                "#FFE8F5E9",
                "#FFFFF8E1",
                "#FFFCE4EC",
                null,
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.bg_dialog_title)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            PreferenceManager.getDefaultSharedPreferences(NotesListActivity.this)
                                    .edit()
                                    .putString(PREF_LIST_BG_TYPE, BG_TYPE_DEFAULT)
                                    .remove(PREF_LIST_BG_VALUE)
                                    .apply();
                            applyListBackground();
                            Toast.makeText(NotesListActivity.this, R.string.bg_applied,
                                    Toast.LENGTH_SHORT).show();
                        } else if (which == items.length - 1) {
                            pickBackgroundImage();
                        } else {
                            PreferenceManager.getDefaultSharedPreferences(NotesListActivity.this)
                                    .edit()
                                    .putString(PREF_LIST_BG_TYPE, BG_TYPE_COLOR)
                                    .putString(PREF_LIST_BG_VALUE, colorValues[which])
                                    .apply();
                            applyListBackground();
                            Toast.makeText(NotesListActivity.this, R.string.bg_applied,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickBackgroundImage() {
        // ACTION_OPEN_DOCUMENT lets us request a persistable URI permission so
        // the chosen wallpaper survives across launches.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_BG_IMAGE);
        } catch (Exception e) {
            // Fall back to the legacy gallery picker if SAF isn't available.
            try {
                Intent fallback = new Intent(Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(fallback, REQUEST_CODE_PICK_BG_IMAGE);
            } catch (Exception ex) {
                Toast.makeText(this, R.string.bg_pick_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Applies the persisted background choice to the root view.
     * Returns true on success, false if the saved value couldn't be applied
     * (in which case we fall back to the default drawable).
     */
    private boolean applyListBackground() {
        if (mListRoot == null) {
            mListRoot = findViewById(R.id.list_root);
        }
        if (mListRoot == null) {
            return false;
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String type = sp.getString(PREF_LIST_BG_TYPE, BG_TYPE_DEFAULT);
        String value = sp.getString(PREF_LIST_BG_VALUE, "");

        try {
            if (BG_TYPE_COLOR.equals(type) && !TextUtils.isEmpty(value)) {
                mListRoot.setBackgroundColor(Color.parseColor(value));
                return true;
            }
            if (BG_TYPE_IMAGE.equals(type) && !TextUtils.isEmpty(value)) {
                Bitmap bmp = decodeScaledBitmap(Uri.parse(value));
                if (bmp != null) {
                    BitmapDrawable bd = new BitmapDrawable(getResources(), bmp);
                    // CENTER_CROP-like behaviour: stretch to fill while keeping aspect.
                    bd.setGravity(Gravity.FILL);
                    mListRoot.setBackground(bd);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "applyListBackground failed", e);
        }
        // Default / fallback path.
        mListRoot.setBackgroundResource(R.drawable.list_background);
        return BG_TYPE_DEFAULT.equals(type);
    }

    /**
     * Decode the picked image with sub-sampling so we don't blow up memory on
     * large photos (the list view can be reused as a giant wallpaper canvas).
     */
    private Bitmap decodeScaledBitmap(Uri uri) {
        InputStream in = null;
        InputStream in2 = null;
        try {
            // First pass: bounds only
            in = getContentResolver().openInputStream(uri);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, bounds);
            if (in != null) in.close();

            int targetW = getResources().getDisplayMetrics().widthPixels;
            int targetH = getResources().getDisplayMetrics().heightPixels;
            int sample = 1;
            while ((bounds.outWidth / sample) > targetW * 2
                    && (bounds.outHeight / sample) > targetH * 2) {
                sample *= 2;
            }

            // Second pass: actual decode
            in2 = getContentResolver().openInputStream(uri);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeStream(in2, null, opts);
        } catch (Exception e) {
            Log.e(TAG, "decodeScaledBitmap failed: " + e.getMessage());
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (IOException ignored) {}
            try { if (in2 != null) in2.close(); } catch (IOException ignored) {}
        }
    }

    private void openPrivacySpace() {
        PrivacySpaceManager manager = PrivacySpaceManager.getInstance(this);
        if (!manager.isSetup()) {
            startActivityForResult(new Intent(this, PrivacySetupActivity.class),
                    REQUEST_CODE_PRIVACY_SETUP);
        } else if (!manager.isUnlocked()) {
            startActivityForResult(new Intent(this, PrivacyLockActivity.class),
                    REQUEST_CODE_PRIVACY_UNLOCK);
        } else {
            startActivity(new Intent(this, PrivacySpaceActivity.class));
        }
    }

    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null /* appData */, false);
        return true;
    }

    private void exportNoteToText() {
        final BackupUtils backup = BackupUtils.getInstance(NotesListActivity.this);
        new AsyncTask<Void, Void, Integer>() {

            @Override
            protected Integer doInBackground(Void... unused) {
                return backup.exportToText();
            }

            @Override
            protected void onPostExecute(Integer result) {
                if (result == BackupUtils.STATE_SD_CARD_UNMOUONTED) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this
                            .getString(R.string.error_sdcard_unmounted));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                } else if (result == BackupUtils.STATE_SUCCESS) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.success_sdcard_export));
                    builder.setMessage(NotesListActivity.this.getString(
                            R.string.format_exported_file_location, backup
                                    .getExportedTextFileName(), backup.getExportedTextFileDir()));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                } else if (result == BackupUtils.STATE_SYSTEM_ERROR) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this
                            .getString(R.string.error_sdcard_export));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                }
            }

        }.execute();
    }

    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    private void startPreferenceActivity() {
        Activity from = getParent() != null ? getParent() : this;
        Intent intent = new Intent(from, NotesPreferenceActivity.class);
        from.startActivityIfNeeded(intent, -1);
    }

    private class OnListItemClickListener implements OnItemClickListener {

        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (view instanceof NotesListItem) {
                NoteItemData item = ((NotesListItem) view).getItemData();
                if (mNotesListAdapter.isInChoiceMode()) {
                    if (item.getType() == Notes.TYPE_NOTE) {
                        position = position - mNotesListView.getHeaderViewsCount();
                        mModeCallBack.onItemCheckedStateChanged(null, position, id,
                                !mNotesListAdapter.isSelectedItem(position));
                    }
                    return;
                }

                switch (mState) {
                    case NOTE_LIST:
                        if (item.getType() == Notes.TYPE_FOLDER
                                || item.getType() == Notes.TYPE_SYSTEM) {
                            openFolder(item);
                        } else if (item.getType() == Notes.TYPE_NOTE) {
                            openNode(item);
                        } else {
                            Log.e(TAG, "Wrong note type in NOTE_LIST");
                        }
                        break;
                    case SUB_FOLDER:
                    case CALL_RECORD_FOLDER:
                        if (item.getType() == Notes.TYPE_NOTE) {
                            openNode(item);
                        } else {
                            Log.e(TAG, "Wrong note type in SUB_FOLDER");
                        }
                        break;
                    default:
                        break;
                }
            }
        }

    }

    private void startQueryDestinationFolders() {
        String selection = NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>? AND " + NoteColumns.ID + "<>?";
        selection = (mState == ListEditState.NOTE_LIST) ? selection:
            "(" + selection + ") OR (" + NoteColumns.ID + "=" + Notes.ID_ROOT_FOLDER + ")";

        mBackgroundQueryHandler.startQuery(FOLDER_LIST_QUERY_TOKEN,
                null,
                Notes.CONTENT_NOTE_URI,
                FoldersListAdapter.PROJECTION,
                selection,
                new String[] {
                        String.valueOf(Notes.TYPE_FOLDER),
                        String.valueOf(Notes.ID_TRASH_FOLER),
                        String.valueOf(mCurrentFolderId)
                },
                NoteColumns.MODIFIED_DATE + " DESC");
    }

    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (view instanceof NotesListItem) {
            mFocusNoteDataItem = ((NotesListItem) view).getItemData();
            if (mFocusNoteDataItem.getType() == Notes.TYPE_NOTE && !mNotesListAdapter.isInChoiceMode()) {
                if (mNotesListView.startActionMode(mModeCallBack) != null) {
                    mModeCallBack.onItemCheckedStateChanged(null, position, id, true);
                    mNotesListView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } else {
                    Log.e(TAG, "startActionMode fails");
                }
            } else if (mFocusNoteDataItem.getType() == Notes.TYPE_FOLDER) {
                mNotesListView.setOnCreateContextMenuListener(mFolderOnCreateContextMenuListener);
            }
        }
        return false;
    }
}
