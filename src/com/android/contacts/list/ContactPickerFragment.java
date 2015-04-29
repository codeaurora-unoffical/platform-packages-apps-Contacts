/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.contacts.list;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.android.contacts.R;
import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.ContactEntryListFragment;
import com.android.contacts.common.list.ContactListAdapter;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.DirectoryListLoader;
import com.android.contacts.common.list.ShortcutIntentBuilder;
import com.android.contacts.common.list.ShortcutIntentBuilder.OnShortcutIntentCreatedListener;

/**
 * Fragment for the contact list used for browsing contacts (as compared to
 * picking a contact with one of the PICK or SHORTCUT intents).
 */
public class ContactPickerFragment extends ContactEntryListFragment<ContactEntryListAdapter>
        implements OnShortcutIntentCreatedListener {

    private static final String KEY_EDIT_MODE = "editMode";
    private static final String KEY_CREATE_CONTACT_ENABLED = "createContactEnabled";
    private static final String KEY_SHORTCUT_REQUESTED = "shortcutRequested";

    private OnContactPickerActionListener mListener;
    private boolean mCreateContactEnabled;
    private boolean mEditMode;
    private boolean mShortcutRequested;

    public ContactPickerFragment() {
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setVisibleScrollbarEnabled(true);
        setQuickContactEnabled(false);
        setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_CONTACT_SHORTCUT);
    }

    public void setOnContactPickerActionListener(OnContactPickerActionListener listener) {
        mListener = listener;
    }

    public boolean isCreateContactEnabled() {
        return mCreateContactEnabled;
    }

    public void setCreateContactEnabled(boolean flag) {
        this.mCreateContactEnabled = flag;
    }

    public boolean isEditMode() {
        return mEditMode;
    }

    public void setEditMode(boolean flag) {
        mEditMode = flag;
    }

    public void setShortcutRequested(boolean flag) {
        mShortcutRequested = flag;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_EDIT_MODE, mEditMode);
        outState.putBoolean(KEY_CREATE_CONTACT_ENABLED, mCreateContactEnabled);
        outState.putBoolean(KEY_SHORTCUT_REQUESTED, mShortcutRequested);
    }

    @Override
    public void restoreSavedState(Bundle savedState) {
        super.restoreSavedState(savedState);

        if (savedState == null) {
            return;
        }

        mEditMode = savedState.getBoolean(KEY_EDIT_MODE);
        mCreateContactEnabled = savedState.getBoolean(KEY_CREATE_CONTACT_ENABLED);
        mShortcutRequested = savedState.getBoolean(KEY_SHORTCUT_REQUESTED);
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);
        if (mCreateContactEnabled && isLegacyCompatibilityMode()) {
            // Since we are using the legacy adapter setShowCreateContact(true) isn't supported.
            // So we need to add an ugly header above the list.
            getListView().addHeaderView(inflater.inflate(R.layout.create_new_contact, null, false));
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (position == 0 && mCreateContactEnabled) {
            mListener.onCreateNewContactAction();
        } else {
            super.onItemClick(parent, view, position, id);
        }
    }

    @Override
    protected void onItemClick(int position, long id) {
        Uri uri;
        if (isLegacyCompatibilityMode()) {
            uri = ((LegacyContactListAdapter)getAdapter()).getPersonUri(position);
        } else {
            uri = ((ContactListAdapter)getAdapter()).getContactUri(position);
        }
        if (uri == null) {
            return;
        }
        if (mEditMode) {
            editContact(uri);
        } else  if (mShortcutRequested) {
            ShortcutIntentBuilder builder = new ShortcutIntentBuilder(getActivity(), this);
            builder.createContactShortcutIntent(uri);
        } else {
            pickContact(uri);
        }
    }

    public void createNewContact() {
        mListener.onCreateNewContactAction();
    }

    public void editContact(Uri contactUri) {
        mListener.onEditContactAction(contactUri);
    }

    public void pickContact(Uri uri) {
        mListener.onPickContactAction(uri);
    }

    @Override
    protected ContactEntryListAdapter createListAdapter() {
        if (!isLegacyCompatibilityMode()) {
            HeaderEntryContactListAdapter adapter = new HeaderEntryContactListAdapter(
                    getActivity());
            adapter.setFilter(ContactListFilter
                    .createFilterWithType(ContactListFilter.FILTER_TYPE_ALL_WITHOUT_SIM));
            adapter.setSectionHeaderDisplayEnabled(true);
            adapter.setDisplayPhotos(true);
            adapter.setQuickContactEnabled(false);
            adapter.setShowCreateContact(mCreateContactEnabled);
            return adapter;
        } else {
            LegacyContactListAdapter adapter = new LegacyContactListAdapter(getActivity());
            adapter.setSectionHeaderDisplayEnabled(false);
            adapter.setDisplayPhotos(false);
            return adapter;
        }
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();

        ContactEntryListAdapter adapter = getAdapter();

        // If "Create new contact" is shown, don't display the empty list UI
        adapter.setEmptyListEnabled(!isCreateContactEnabled());
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.contact_picker_content, null);
    }

    @Override
    public void onShortcutIntentCreated(Uri uri, Intent shortcutIntent) {
        mListener.onShortcutIntentCreated(shortcutIntent);
    }

    @Override
    public void onPickerResult(Intent data) {
        mListener.onPickContactAction(data.getData());
    }
}
