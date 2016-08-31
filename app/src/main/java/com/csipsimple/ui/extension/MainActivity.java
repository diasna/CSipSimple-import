package com.csipsimple.ui.extension;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.csipsimple.R;
import com.csipsimple.api.ISipService;
import com.csipsimple.api.MediaState;
import com.csipsimple.api.SipCallSession;
import com.csipsimple.api.SipManager;
import com.csipsimple.ui.account.AccountsEditList;
import com.csipsimple.utils.CallsUtils;

/**
 * Created by islamap.Inc on 4/22/15.
 * Copyright (c) 2014 Machikon. All rights reserved.
 */
public class MainActivity extends SherlockListActivity {

    private ContactAdapter adapter;

    private Object callMutex = new Object();
    private SipCallSession[] callsInfo = null;

    private static final String THIS_FILE = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.extension_main_activity);

        SipCallSession initialSession = getIntent().getParcelableExtra(SipManager.EXTRA_CALL_INFO);
        synchronized (callMutex) {
            callsInfo = new SipCallSession[1];
            callsInfo[0] = initialSession;
        }

        Contact c0 = new Contact("push", "push");
        Contact ce = new Contact("echo", "echo");
        Contact c1 = new Contact("1001", "1001");
        Contact c2 = new Contact("1002", "1002");
        Contact c3 = new Contact("1003", "1003");
        Contact c4 = new Contact("1004", "1004");
        Contact c5 = new Contact("1005", "1005");
        Contact c6 = new Contact("1006", "1006");
        adapter = new ContactAdapter(this, new Contact[]{c0, ce, c1, c2, c3, c4, c5, c6});
        setListAdapter(adapter);
    }

    private ISipService service;
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            service = ISipService.Stub.asInterface(arg1);
            try {
                callsInfo = service.getCalls();
            } catch (RemoteException e) {
                Log.e(THIS_FILE, "Can't get back the call", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            callsInfo = null;
        }
    };

    private BroadcastReceiver callStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(SipManager.ACTION_SIP_CALL_CHANGED)) {
                if (service != null) {
                    try {
                        synchronized (callMutex) {
                            callsInfo = service.getCalls();
                            Log.d(THIS_FILE, "Active call is [PICK]" + callsInfo);
                        }
                    } catch (RemoteException e) {
                        com.csipsimple.utils.Log.e(THIS_FILE, "Not able to retrieve calls");
                    }
                }
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        Intent serviceIntent = new Intent(SipManager.INTENT_SIP_SERVICE);
        serviceIntent.setPackage(MainActivity.this.getPackageName());
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);

        registerReceiver(callStateReceiver, new IntentFilter(SipManager.ACTION_SIP_CALL_CHANGED));
        registerReceiver(callStateReceiver, new IntentFilter(SipManager.ACTION_SIP_MEDIA_CHANGED));
        registerReceiver(callStateReceiver, new IntentFilter(SipManager.ACTION_ZRTP_SHOW_SAS));
    }

    @Override
    protected void onPause() {
        unbindService(connection);
        unregisterReceiver(callStateReceiver);
        super.onPause();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        Intent intent = new Intent(this, CallDetail.class);
        intent.putExtra("name", adapter.getItem(position).getName());
        intent.putExtra("number", adapter.getItem(position).getNumber());
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.menu_login, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                startActivity(new Intent(this, AccountsEditList.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
