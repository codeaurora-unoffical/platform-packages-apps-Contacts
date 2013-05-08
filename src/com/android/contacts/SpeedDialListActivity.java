/*
 * Copyright (C) 2011-2012, Code Aurora Forum. All rights reserved.
 * Copyright (c) 2012, The Linux Foundation. All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
	 * Redistributions of source code must retain the above copyright
	   notice, this list of conditions and the following disclaimer.
	 * Redistributions in binary form must reproduce the above
	   copyright notice, this list of conditions and the following
	   disclaimer in the documentation and/or other materials provided
	   with the distribution.
	 * Neither the name of The Linux Foundation, Inc. nor the names of its
	   contributors may be used to endorse or promote products derived
	   from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.contacts;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts.Data;
import android.provider.ContactsContract.RawContacts;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class SpeedDialListActivity extends ListActivity implements OnItemClickListener, OnCreateContextMenuListener{

    private static final String TAG = "SpeedDial";
    private static final String ACTION_ADD_VOICEMAIL = "com.android.phone.CallFeaturesSetting.ADD_VOICEMAIL";

    private static final int PREF_NUM = 8;
    private static final int SPEED_ITEMS = 9;
    //save the contact data id for speed dial number
    private static int[] mContactDataId = new int[PREF_NUM];
    //save the speed list item content, include 1 voice mail and 2-9 speed number
    private static String[] mSpeedListItems = new String[SPEED_ITEMS];

    //speeddialutils class, use to read speed number form preference and update data
    private static SpeedDialUtils mSpeedDialUtils;

    private static int mPosition;

    private static final int MENU_REPLACE = 0;
    private static final int MENU_DELETE = 1;

    private static final int PICK_CONTACT_RESULT = 0;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSpeedDialUtils = new SpeedDialUtils(this);
        //the first item is the "1.voice mail", it doesn't change for ever
        mSpeedListItems[0] = getString(R.string.speed_item, String.valueOf(1), getString(R.string.voicemail));

        //get raw contact id from share preference
        for (int i = 0; i < PREF_NUM; i++) {
            mContactDataId[i] = mSpeedDialUtils.getContactDataId(i);
        }

        ListView listview = getListView();
        listview.setOnItemClickListener(this);
        listview.setOnCreateContextMenuListener(this);
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();

        //when every on resume, should match name from contacts, because if
        //this activity is paused, and the contacts data is changed(eg:contact
        //is edited or deleted...),after it resumes, its data also be updated.
        matchInfoFromContacts();
        setListAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, mSpeedListItems));
    }

    /*
     * use to match number from contacts, if the speed number is in contacts,
     * the speed item show corresponding contact name, else show number.
     */
    private void matchInfoFromContacts() {
        // TODO Auto-generated method stub
        for (int i = 1; i < SPEED_ITEMS; i++) {
            //if there is no speed dial number for number key, show "not set", or lookup in contacts
            //according to number, if exist, show contact name, else show number.
            String contactNum = mSpeedDialUtils.getSpeedDialInfo(mContactDataId[i-1], mSpeedDialUtils.INFO_NUMBER);
            String contactName = mSpeedDialUtils.getSpeedDialInfo(mContactDataId[i-1], mSpeedDialUtils.INFO_NAME);
            if (contactNum == null) {
                mSpeedListItems[i] = getString(R.string.speed_item, String.valueOf(i+1), getString(R.string.not_set));
                mContactDataId[i-1] = 0;
            } else if (contactName == null){
                mSpeedListItems[i] = getString(R.string.speed_item, String.valueOf(i+1), contactNum);
            } else {
                mSpeedListItems[i] = getString(R.string.speed_item, String.valueOf(i+1), contactName);
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // TODO Auto-generated method stub
        if (position == 0) {
            Intent intent = new Intent(ACTION_ADD_VOICEMAIL);
            if (TelephonyManager.getDefault().isMultiSimEnabled()) {
                //if multi sim enable, should show tab activity to let user select which sim to be set.
                intent.setClassName("com.android.settings", "com.android.settings.multisimsettings.MultiSimSettingTab");
                intent.putExtra("PACKAGE", "com.android.phone");
                intent.putExtra("TARGET_CLASS", "com.android.phone.MSimCallFeaturesSubSetting");
            }
            try {
                startActivity(intent);
            } catch(ActivityNotFoundException e) {
                Log.w(TAG, "can not find activity deal with voice mail");
            }
        } else if (position < SPEED_ITEMS) {
            if (mContactDataId[position-1] == 0) {
                goContactsToPick(position);
            } else {
                final String numStr = mSpeedDialUtils.getSpeedDialInfo(mContactDataId[position-1], mSpeedDialUtils.INFO_NUMBER);
                final String nameStr = mSpeedDialUtils.getSpeedDialInfo(mContactDataId[position-1], mSpeedDialUtils.INFO_NAME);
                new AlertDialog.Builder(this).setTitle(nameStr).setMessage(numStr).setPositiveButton(R.string.speed_call,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // TODO Auto-generated method stub
                                Intent callIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED);
                                callIntent.setData(Uri.fromParts("tel", numStr, null));
                                callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(callIntent);
                            }
                        }).setNegativeButton(R.string.speed_sms,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        // TODO Auto-generated method stub
                                        Intent smsIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("sms", numStr, null));
                                        startActivity(smsIntent);
                                    }
                                }).show();
            }
        } else {
            Log.w(TAG,"the invalid item");
        }
    }

    /*
     * goto contacts, used to set or replace speed number
     */
    private void goContactsToPick(int position) {
        // TODO Auto-generated method stub
        mPosition = position;
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        startActivityForResult(intent, PICK_CONTACT_RESULT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        if (requestCode == PICK_CONTACT_RESULT && resultCode == Activity.RESULT_OK && data != null) {
            //the return uri will contain the contact id
            Uri uriRet = data.getData();
            if (uriRet != null) {
                int numId = mPosition - 1;
                mContactDataId[numId] = (int)ContentUris.parseId(uriRet);
                mSpeedDialUtils.storeContactDataId(numId, mContactDataId[numId]);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        // TODO Auto-generated method stub
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
        int pos = info.position;
        if ((pos > 0) && (pos < SPEED_ITEMS)) {
            if (mContactDataId[pos-1] != 0) {
                String contactNum = mSpeedDialUtils.getSpeedDialInfo(mContactDataId[pos-1], mSpeedDialUtils.INFO_NUMBER);
                String contactName = mSpeedDialUtils.getSpeedDialInfo(mContactDataId[pos-1], mSpeedDialUtils.INFO_NAME);
                String hdrTitle = contactName != null ? contactName : contactNum;
                menu.setHeaderTitle(hdrTitle);
                menu.add(0, MENU_REPLACE, 0, R.string.replace);
                menu.add(0, MENU_DELETE, 0, R.string.delete);
            }
        }
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // TODO Auto-generated method stub
        int itemId = item.getItemId();
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        int pos = info.position;
        switch(itemId)
        {
        case MENU_REPLACE:
            goContactsToPick(pos);
            break;
        case MENU_DELETE:
            //delete speed number, only need to set array data to null, and clear speed number in preference.
            mContactDataId[pos-1] = 0;
            mSpeedListItems[pos] = getString(R.string.speed_item, String.valueOf(pos+1), getString(R.string.not_set));
            mSpeedDialUtils.storeContactDataId(pos-1, 0);
            //update listview item
            setListAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, mSpeedListItems));
            break;
        default:
            break;
        }
        return super.onContextItemSelected(item);
    }

}
