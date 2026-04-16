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
import android.appwidget.AppWidgetManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;

import java.util.HashSet;

/**
 * Displays all notes that have {@code is_private = 1}.
 *
 * <p>The activity locks the privacy space whenever it stops (goes to background).
 * Long-pressing a note opens the action mode with Restore and Delete options.
 */
public class PrivacySpaceActivity extends Activity implements View.OnClickListener,
        AdapterView.OnItemLongClickListener {

    private static final int QUERY_TOKEN       = 10;
    private static final int REQUEST_OPEN_NOTE = 201;
    private static final int REQUEST_NEW_NOTE  = 202;

    /** Query selects only private notes, ordered by modification date. */
    private static final String PRIVATE_SELECTION =
            NoteColumns.IS_PRIVATE + "=1 AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE;

    private ContentResolver mContentResolver;
    private NotesListAdapter mAdapter;
    private ListView mListView;
    private TextView mEmptyHint;
    private BackgroundQuery mQueryHandler;
    private NoteItemData mFocusItem;
    private ModeCallback mModeCallback;

    /** Timestamp recorded just before opening NoteEditActivity to create a new note. */
    private long mNewNoteTimestamp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_space);

        mContentResolver = getContentResolver();
        mQueryHandler    = new BackgroundQuery(mContentResolver);
        mAdapter         = new NotesListAdapter(this);
        mModeCallback    = new ModeCallback();

        mListView  = findViewById(R.id.private_notes_list);
        mEmptyHint = findViewById(R.id.tv_empty_hint);

        mListView.addFooterView(
                LayoutInflater.from(this).inflate(R.layout.note_list_footer, null), null, false);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this::onItemClick);
        mListView.setOnItemLongClickListener(this);

        ((Button) findViewById(R.id.btn_new_private_note)).setOnClickListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        query();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Lock the privacy space whenever this screen loses focus
        PrivacySpaceManager.getInstance(this).lock();
    }

    private void query() {
        mQueryHandler.startQuery(QUERY_TOKEN, null,
                Notes.CONTENT_NOTE_URI,
                NoteItemData.PROJECTION,
                PRIVATE_SELECTION,
                null,
                NoteColumns.MODIFIED_DATE + " DESC");
    }

    private final class BackgroundQuery extends AsyncQueryHandler {
        BackgroundQuery(ContentResolver cr) { super(cr); }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (token == QUERY_TOKEN) {
                mAdapter.changeCursor(cursor);
                boolean empty = (cursor == null || cursor.getCount() == 0);
                mEmptyHint.setVisibility(empty ? View.VISIBLE : View.GONE);
                mListView.setVisibility(empty ? View.GONE : View.VISIBLE);
            }
        }
    }

    // ---- Item clicks ----

    private void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (!(view instanceof NotesListItem)) return;
        NoteItemData item = ((NotesListItem) view).getItemData();
        if (mAdapter.isInChoiceMode()) {
            int pos = position - mListView.getHeaderViewsCount();
            mModeCallback.onItemCheckedStateChanged(null, pos, id,
                    !mAdapter.isSelectedItem(pos));
            return;
        }
        if (item.getType() == Notes.TYPE_NOTE) {
            openNote(item.getId());
        }
    }

    private void openNote(long id) {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, id);
        startActivityForResult(intent, REQUEST_OPEN_NOTE);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (!(view instanceof NotesListItem)) return false;
        mFocusItem = ((NotesListItem) view).getItemData();
        if (mFocusItem.getType() == Notes.TYPE_NOTE && !mAdapter.isInChoiceMode()) {
            if (mListView.startActionMode(mModeCallback) != null) {
                mModeCallback.onItemCheckedStateChanged(null, position, id, true);
                mListView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        }
        return false;
    }

    // ---- Action mode ----

    private final class ModeCallback implements ListView.MultiChoiceModeListener {
        private ActionMode mActionMode;

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.privacy_note_options, menu);
            mActionMode = mode;
            mAdapter.setChoiceMode(true);
            mListView.setLongClickable(false);
            return true;
        }

        @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (mAdapter.getSelectedCount() == 0) {
                Toast.makeText(PrivacySpaceActivity.this,
                        R.string.menu_select_none, Toast.LENGTH_SHORT).show();
                return true;
            }
            int itemId = item.getItemId();
            if (itemId == R.id.privacy_restore) {
                restoreSelected();
                mode.finish();
            } else if (itemId == R.id.privacy_delete) {
                showDeleteConfirm(mode);
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mAdapter.setChoiceMode(false);
            mListView.setLongClickable(true);
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                boolean checked) {
            mAdapter.setCheckedItem(position, checked);
        }

        void finishActionMode() { if (mActionMode != null) mActionMode.finish(); }
    }

    private void restoreSelected() {
        HashSet<Long> ids = mAdapter.getSelectedItemIds();
        ContentValues values = new ContentValues();
        values.put(NoteColumns.IS_PRIVATE, 0);
        for (long id : ids) {
            mContentResolver.update(Notes.CONTENT_NOTE_URI, values,
                    NoteColumns.ID + "=?", new String[]{String.valueOf(id)});
        }
        Toast.makeText(this, R.string.privacy_move_out_done, Toast.LENGTH_SHORT).show();
        query();
    }

    private void showDeleteConfirm(ActionMode mode) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.alert_title_delete)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(getString(R.string.alert_message_delete_notes,
                        mAdapter.getSelectedCount()))
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    deleteSelected();
                    mode.finish();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteSelected() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... v) {
                DataUtils.batchDeleteNotes(mContentResolver, mAdapter.getSelectedItemIds());
                return null;
            }
            @Override
            protected void onPostExecute(Void v) { query(); }
        }.execute();
    }

    // ---- New note ----

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_new_private_note) {
            createNewPrivateNote();
        }
    }

    /**
     * Creates a new note via the editor. After the editor returns we mark the
     * note as private by timestamp comparison (the note was just created so its
     * {@code created_date} will be very recent).
     */
    private void createNewPrivateNote() {
        mNewNoteTimestamp = System.currentTimeMillis();
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, Notes.ID_ROOT_FOLDER);
        startActivityForResult(intent, REQUEST_NEW_NOTE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_NEW_NOTE && resultCode == RESULT_OK) {
            // Mark any note created during this editor session as private.
            // We use a 5-second window to handle slow saves.
            ContentValues values = new ContentValues();
            values.put(NoteColumns.IS_PRIVATE, 1);
            mContentResolver.update(Notes.CONTENT_NOTE_URI, values,
                    NoteColumns.CREATED_DATE + ">? AND "
                            + NoteColumns.IS_PRIVATE + "=0 AND "
                            + NoteColumns.TYPE + "=?",
                    new String[]{
                            String.valueOf(mNewNoteTimestamp - 5000),
                            String.valueOf(Notes.TYPE_NOTE)
                    });
        }
        if (requestCode == REQUEST_OPEN_NOTE || requestCode == REQUEST_NEW_NOTE) {
            mAdapter.changeCursor(null);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
