/*
 * Copyright (c) 2012, The Linux Foundation. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.contacts.group.local;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.LocalGroup;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.LocalGroups.Group;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.editor.MultiPickContactActivity;
import com.android.contacts.list.AccountFilterActivity;
import com.android.contacts.list.ContactListFilter;
import com.android.contacts.model.account.PhoneAccountType;

public class GroupEditActivity extends PreferenceActivity implements OnPreferenceChangeListener,
        OnPreferenceClickListener, TextWatcher {

    private static final String TAG = GroupEditActivity.class.getSimpleName();

    private static final String KEY_TITLE = "group_title";

    private static final String KEY_MEMBER = "group_member";

    private EditTextPreference titleView;

    private Group group;

    private PreferenceScreen addMemberView;

    private static final int CODE_PICK_MEMBER = 1;

    private AddMembersTask mAddMembersTask;

    private boolean mIsAddMembersTaskCanceled; //indicate whether the task is canceled.

    private static final int MSG_CANCEL = 1; //used to show a toast indicating task is canceled

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.addPreferencesFromResource(R.xml.group_edit);
        titleView = (EditTextPreference) this.findPreference(KEY_TITLE);
        titleView.getEditText().addTextChangedListener(this);
        titleView.setOnPreferenceChangeListener(this);
        addMemberView = (PreferenceScreen) this.findPreference(KEY_MEMBER);
        addMemberView.setOnPreferenceClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        group = Group.restoreGroupById(getContentResolver(),
                Long.parseLong(getIntent().getData().getLastPathSegment()));
        initView();
    }

    private void initView() {
        //avoid group is null, only for Monkey test.
        if (group != null) {
            // If the length of group name is more than GROUP_NAME_MAX_LENGTH, the
            // name will be limited here.
            String groupName = group.getTitle();
            if(groupName.length() > AddLocalGroupDialog.GROUP_NAME_MAX_LENGTH) {
                groupName = groupName.substring(0, AddLocalGroupDialog.GROUP_NAME_MAX_LENGTH);
            }
            titleView.setTitle(groupName);
            titleView.setText(groupName);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference arg0, Object arg1) {
        if (titleView == arg0 && !group.getTitle().equals(arg1) && ((String) arg1).length() > 0) {
            group.setTitle((String) arg1);
            if (group.update(getContentResolver()))
                initView();
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (addMemberView == preference) {
            pickMembers();
        }
        return false;
    }

    private void pickMembers() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setClass(this, MultiPickContactActivity.class);
        ContactListFilter filter = new ContactListFilter(ContactListFilter.FILTER_TYPE_ACCOUNT,
                PhoneAccountType.ACCOUNT_TYPE, "PHONE", null, null);
        intent.putExtra(AccountFilterActivity.KEY_EXTRA_CONTACT_LIST_FILTER, filter);
        startActivityForResult(intent, CODE_PICK_MEMBER);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 0, 0, R.string.menu_option_delete);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 0:
                if (group.delete(getContentResolver())) {
                    finish();
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK)
            switch (requestCode) {
                case CODE_PICK_MEMBER:
                    Bundle result = data.getExtras().getBundle("result");

                    // define member object mAddMembersTask to use later.
                    mAddMembersTask = new AddMembersTask(result);
                    mAddMembersTask.execute();
            }
    }

    class AddMembersTask extends AsyncTask<Object, Object, Object> {
        private ProgressDialog mProgressDialog;

        private Handler handler;

        private Handler alertHandler = new Handler() {
            @Override
            public void dispatchMessage(Message msg) {
                if(msg.what == MSG_CANCEL) {
                    Toast.makeText(GroupEditActivity.this, R.string.add_member_task_canceled, Toast.LENGTH_LONG).show();
                } else if(msg.what == 0) {
                    Toast.makeText(GroupEditActivity.this, R.string.toast_not_add, Toast.LENGTH_LONG)
                    .show();
                }
            }
        };

        private Bundle result;

        private int size;

        AddMembersTask(Bundle result) {
            size = result.size();
            this.result = result;
            HandlerThread thread = new HandlerThread("DownloadTask");
            thread.start();
            handler = new Handler(thread.getLooper()) {
                public void dispatchMessage(Message msg) {
                    if (mProgressDialog != null && msg.what > 0) {
                        mProgressDialog.incrementProgressBy(msg.what);
                    } else if (mProgressDialog != null && mProgressDialog.isShowing()) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                    }
                }
            };
        }

        protected void onPostExecute(Object result) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        }

        @Override
        protected void onPreExecute() {
            mIsAddMembersTaskCanceled = false;
            mProgressDialog = new ProgressDialog(GroupEditActivity.this);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setProgress(0);
            mProgressDialog.setMax(size);
            mProgressDialog.show();
            mProgressDialog.setOnCancelListener(new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {

                    // if dialog is canceled, cancel the task also.
                        mIsAddMembersTaskCanceled = true;
                    }
            });
        }

        @Override
        protected Bundle doInBackground(Object... params) {
            proccess();
            return null;
        }

        public void proccess() {
            boolean hasInvalide = false;
            int progressIncrement = 0;
            ContentValues values = new ContentValues();
            // add Non-null protection of group for monkey test
            if (null != group) {
                values.put(LocalGroup.DATA1, group.getId());
            }

            Set<String> keySet = result.keySet();
            Iterator<String> it = keySet.iterator();

            // add a ContentProviderOperation update list.
            final ArrayList<ContentProviderOperation> updateList = new ArrayList<ContentProviderOperation>();
            ContentProviderOperation.Builder builder = null;
            mIsAddMembersTaskCanceled = false;
            while (it.hasNext() ) {
                if (mIsAddMembersTaskCanceled) {
                    alertHandler.sendEmptyMessage(MSG_CANCEL);
                    break;
                }
                if (progressIncrement++ % 2 == 0) {
                    handler.obtainMessage(1).sendToTarget();
                }
                String id = it.next();
                Cursor c = null;
                try {
                    c = getContentResolver().query(RawContacts.CONTENT_URI, new String[] {
                            RawContacts._ID, RawContacts.ACCOUNT_TYPE
                    }, RawContacts.CONTACT_ID + "=?", new String[] {
                            id
                    }, null);
                    if (c.moveToNext()) {
                        String rawId = String.valueOf(c.getLong(0));

                        if (!PhoneAccountType.ACCOUNT_TYPE.equals(c.getString(1))) {
                            hasInvalide = true;
                            continue;
                        }

                        builder = ContentProviderOperation.newDelete(Data.CONTENT_URI);
                        builder.withSelection(Data.RAW_CONTACT_ID + "=? and " + Data.MIMETYPE
                                + "=?", new String[] {
                                rawId, LocalGroup.CONTENT_ITEM_TYPE
                        });

                        // add the delete operation to the update list.
                        updateList.add(builder.build());

                        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                        // add Non-null protection of group for monkey test
                        if (null != group) {
                            builder.withValue(LocalGroup.DATA1, group.getId());
                        }
                        builder.withValue(Data.RAW_CONTACT_ID, rawId);
                        builder.withValue(Data.MIMETYPE, LocalGroup.CONTENT_ITEM_TYPE);

                        // add the insert operation to the update list.
                        updateList.add(builder.build());
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
            }

            // if task is canceled ,still update the database with the data in updateList.

            // apply batch to execute the delete and insert operation.
            if (updateList.size() > 0) {
                addMembersApplyBatchByBuffer(updateList, getContentResolver());
            }
            if (hasInvalide) {
                alertHandler.sendEmptyMessage(0);
            }
        }

        private void addMembersApplyBatchByBuffer(ArrayList<ContentProviderOperation> list, ContentResolver cr) {
            final ArrayList<ContentProviderOperation> temp = new ArrayList<ContentProviderOperation>(
                    BUFFER_LENGTH);
            int bufferSize = list.size() / BUFFER_LENGTH;
            for (int index = 0; index <= bufferSize; index++) {
                temp.clear();
                if (index == bufferSize) {
                    for (int i = index * BUFFER_LENGTH; i < list.size(); i++) {
                        temp.add(list.get(i));
                    }
                } else {
                    for (int i = index * BUFFER_LENGTH; i < index * BUFFER_LENGTH + BUFFER_LENGTH; i++) {
                        temp.add(list.get(i));
                    }
                }
                if (!temp.isEmpty()) {
                    try {
                        cr.applyBatch(ContactsContract.AUTHORITY, temp);
                        handler.obtainMessage(temp.size() / 4).sendToTarget();
                    } catch (Exception e) {
                        Log.e(TAG, "apply batch by buffer error:" + e);
                    }
                }
            }
        }
    }

    /**
     * the max length of applyBatch is 500
     */
    private static final int BUFFER_LENGTH = 499;

    public static void applyBatchByBuffer(ArrayList<ContentProviderOperation> list, ContentResolver cr) {
        final ArrayList<ContentProviderOperation> temp = new ArrayList<ContentProviderOperation>(
                BUFFER_LENGTH);
        int bufferSize = list.size() / BUFFER_LENGTH;
        for (int index = 0; index <= bufferSize; index++) {
            temp.clear();
            if (index == bufferSize) {
                for (int i = index * BUFFER_LENGTH; i < list.size(); i++) {
                    temp.add(list.get(i));
                }
            } else {
                for (int i = index * BUFFER_LENGTH; i < index * BUFFER_LENGTH + BUFFER_LENGTH; i++) {
                    temp.add(list.get(i));
                }
            }
            if (!temp.isEmpty()) {
                try {
                    cr.applyBatch(ContactsContract.AUTHORITY, temp);
                } catch (Exception e) {
                    Log.e(TAG, "apply batch by buffer error:" + e);
                }
            }
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // TODO Auto-generated method stub

    }

    @Override
    public void afterTextChanged(Editable s) {
        String name = s.toString();
        int len = 0;
        // Add nSelStart and nSelEnd to record the current cursor position,
        // then used them to delete the new input characters when the text
        // size reached MAX.
        int nSelStart = titleView.getEditText().getSelectionStart();
        int nSelEnd = titleView.getEditText().getSelectionEnd();
        for (int i = 0; i < s.length(); i++) {
            int ch = Character.codePointAt(name, i);
            // to make sure no matter the language is English or Chinese the
            // group name is displayed in single line
            if (ch >= 0x00 && ch <= 0xFF)
                len++;
            else
                len += 2;
            if (len > AddLocalGroupDialog.GROUP_NAME_MAX_LENGTH || ch == 10) {
                // If the content length of edittext is more than GROUP_NAME_MAX_LENGTH,
                // the new inputted charactor will be deleted to keep the content not
                // be changed.
                s.delete(nSelStart - 1, nSelEnd);
                // Use setTextKeepState to keep the cursor position.
                titleView.getEditText().setTextKeepState(s);
                break;
            }
        }
    }

    @Override
    protected void onStop() {

        // dismiss the dialog and cancel the task in order to avoid a case that GroupEditActivity does not exist,
        // but task is going on in background, then onPostExecute() will cause Exception.
        if (mAddMembersTask != null && mAddMembersTask.mProgressDialog != null) {
           if (mAddMembersTask.mProgressDialog.isShowing()) {
               mAddMembersTask.mProgressDialog.dismiss();
                   mIsAddMembersTaskCanceled = true;
               }
           }
       super.onStop();
    }
}
