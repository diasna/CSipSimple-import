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
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.csipsimple.R;
import com.csipsimple.api.ISipService;
import com.csipsimple.api.MediaState;
import com.csipsimple.api.SipCallSession;
import com.csipsimple.api.SipManager;
import com.csipsimple.api.SipProfile;
import com.csipsimple.utils.CallsUtils;

import java.util.List;

/**
 * Created by islamap.Inc on 4/24/15.
 * Copyright (c) 2014 Machikon. All rights reserved.
 */
public class CallDetail extends SherlockFragmentActivity {

    private static final String THIS_FILE = "CallDetail";

    String name = "";
    String number = "";

    Button mCallButton;
    TextView mStatusTextView;

    private Object callMutex = new Object();
    private SipCallSession[] callsInfo = null;

    int callActiveId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.extension_calldetail_activity);
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            name = bundle.getString("name");
            number = bundle.getString("number");
        }

        mCallButton = (Button) findViewById(R.id.button13);
        mStatusTextView = (TextView) findViewById(R.id.textView4);

        mCallButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch ( event.getAction() ) {
                    case MotionEvent.ACTION_DOWN:
                        try {
                            List<SipProfile> activeProfiles = SipProfile.getAllProfiles(CallDetail.this, false);
                            if (!activeProfiles.isEmpty()) {
                                SipProfile profile = activeProfiles.get(0);
                                Log.d(THIS_FILE, "calling [" + number + "] with [" + profile.getSipUserName() + "]");
                                service.makeCall(number, (int) profile.id);
                            } else {
                                Log.d(THIS_FILE, "profile empty");
                            }
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (service != null) {
                            try {
                                service.hangup(callActiveId, SipCallSession.StatusCode.BUSY_HERE);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                        break;
                }
                return true;

            }
        });
        SipCallSession initialSession = getIntent().getParcelableExtra(SipManager.EXTRA_CALL_INFO);
        synchronized (callMutex) {
            callsInfo = new SipCallSession[1];
            callsInfo[0] = initialSession;
        }
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
                try {
                    callsInfo = service.getCalls();
                    for (SipCallSession call : callsInfo) {
                        if (call.getCallId() == callActiveId) {
                            mStatusTextView.setText(CallsUtils.getStringCallState(call, CallDetail.this));
                        }

                        if (call.isActive()) {
                            callActiveId = call.getCallId();
                            Log.i("Active id", "[" + callActiveId + "]");
                        }
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        Intent serviceIntent = new Intent(SipManager.INTENT_SIP_SERVICE);
        serviceIntent.setPackage(CallDetail.this.getPackageName());
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
        registerReceiver(callStateReceiver, new IntentFilter(SipManager.ACTION_SIP_CALL_CHANGED));
    }

    @Override
    protected void onPause() {
        unbindService(connection);
        super.onPause();
    }
}
