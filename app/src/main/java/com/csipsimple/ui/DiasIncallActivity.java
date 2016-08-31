package com.csipsimple.ui;

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

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.csipsimple.R;
import com.csipsimple.api.ISipService;
import com.csipsimple.api.SipCallSession;
import com.csipsimple.api.SipManager;
import com.csipsimple.service.SipService;
import com.csipsimple.ui.incall.DtmfDialogFragment;
import com.csipsimple.utils.CallsUtils;

/**
 * Created by islamap.Inc on 4/20/15.
 * Copyright (c) 2014 Machikon. All rights reserved.
 */
public class DiasIncallActivity extends SherlockFragmentActivity implements
        DtmfDialogFragment.OnDtmfListener {

    private final static String THIS_FILE = "DiasInCallActivity";

    private Object callMutex = new Object();
    private SipCallSession[] callsInfo = null;
    int callActiveId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.extension_receive_voice);

        SipCallSession initialSession = getIntent().getParcelableExtra(SipManager.EXTRA_CALL_INFO);
        synchronized (callMutex) {
            callsInfo = new SipCallSession[1];
            callsInfo[0] = initialSession;
        }

        bindService(new Intent(this, SipService.class), connection, Context.BIND_AUTO_CREATE);
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
                    Log.i("Calls Count", "[" + callsInfo.length + "]");
                    for (SipCallSession call : callsInfo) {
                        if (call.getCallId() == callActiveId && call.isAfterEnded()) {
                            finish();
                        }
                        Log.i("STATE", "CALL " + call.getCallId() + " [" + CallsUtils.getStringCallState(call, DiasIncallActivity.this) + "]");
                        if (call.isActive()) {
                            callActiveId = call.getCallId();
                        }
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    @Override
    public void OnDtmf(int callId, int keyCode, int dialTone) {
        if (service != null) {
            if (callId != SipCallSession.INVALID_CALL_ID) {
                try {
                    service.sendDtmf(callId, keyCode);
                } catch (RemoteException e) {
                    Log.e(THIS_FILE, "Was not able to send dtmf tone", e);
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(callStateReceiver, new IntentFilter(SipManager.ACTION_SIP_CALL_CHANGED));
    }

    @Override
    protected void onPause() {
        unregisterReceiver(callStateReceiver);
        super.onPause();
    }

}
