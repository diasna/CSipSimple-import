package com.csipsimple.ui.extension;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.csipsimple.R;
import com.csipsimple.api.SipConfigManager;
import com.csipsimple.api.SipManager;
import com.csipsimple.api.SipProfile;
import com.csipsimple.models.Filter;
import com.csipsimple.service.SipService;
import com.csipsimple.ui.account.AccountsEditList;
import com.csipsimple.utils.Log;
import com.csipsimple.utils.PreferencesProviderWrapper;
import com.csipsimple.utils.PreferencesWrapper;
import com.csipsimple.wizards.WizardIface;
import com.csipsimple.wizards.impl.Basic;

import java.util.List;
import java.util.UUID;

/**
 * Created by islamap.Inc on 4/20/15.
 * Copyright (c) 2014 Machikon. All rights reserved.
 */
public class LoginActivity extends SherlockFragmentActivity {

    private EditText aUsername;
    private EditText aPassword;
    private Button aButton;

    protected SipProfile profile = null;
    private WizardIface wizard = null;

    private PreferencesProviderWrapper prefProviderWrapper;

    ProgressDialog dialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.extension_login_activity);

        if (isServiceRunning(SipService.class)) {
            Intent i = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(i);
            finish();
        } else {
            prefProviderWrapper = new PreferencesProviderWrapper(this);
            Log.setLogLevel(4);
            try {
                wizard = Basic.class.newInstance();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

            aUsername = (EditText) findViewById(R.id.editText);
            aPassword = (EditText) findViewById(R.id.editText2);
            aButton = (Button) findViewById(R.id.button12);
            aButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog = new ProgressDialog(LoginActivity.this);
                    dialog.setMessage("Sign in. Please wait...");
                    dialog.setCancelable(false);
                    dialog.show();
                    Log.i("SaveAccount", "pencet");
                    saveAccount(profile, aUsername.getText().toString(), aPassword.getText().toString());
                    startSipService();
                }
            });
        }
    }

    private void startSipService() {
        Thread t = new Thread("StartSip") {
            public void run() {
                Log.d("Login", "Starting Sip Service");
                Intent serviceIntent = new Intent(SipManager.INTENT_SIP_SERVICE);
                serviceIntent.setPackage(getPackageName());
                serviceIntent.putExtra(SipManager.EXTRA_OUTGOING_ACTIVITY, new ComponentName(LoginActivity.this, LoginActivity.class));
                startService(serviceIntent);
            }
        };
        t.start();
        Intent serviceIntent = new Intent(SipManager.INTENT_SIP_SERVICE);
        serviceIntent.setPackage(LoginActivity.this.getPackageName());
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mLoginReceiver, new IntentFilter("com.sipcustom.login.status"));
    }

    @Override
    protected void onPause() {
        unregisterReceiver(mLoginReceiver);
        super.onPause();
    }

    private SipProfile buildingSipProfile(SipProfile profile, String username, String password) {
        if (profile == null) {
            profile = new SipProfile();
        }
        profile.display_name = "demo";
        profile.acc_id = "<sip:" + username + "@voip.okyfirmansyah.net>";
        String regUri = "sip:voip.okyfirmansyah.net";
        profile.reg_uri = regUri;
        profile.proxies = new String[]{regUri};
        profile.realm = "*";
        profile.username = username.trim();
        profile.data = password;
        profile.scheme = SipProfile.CRED_SCHEME_DIGEST;
        profile.datatype = SipProfile.CRED_DATA_PLAIN_PASSWD;
        profile.transport = SipProfile.TRANSPORT_UDP;
        return profile;
    }

    private void saveAccount(SipProfile profile, String username, String password) {
        PreferencesWrapper prefs = new PreferencesWrapper(getApplicationContext());
        profile = buildingSipProfile(profile, username, password);
        profile.wizard = "BASIC";
        prefs.startEditing();
        prefs.setPreferenceBooleanValue(PreferencesWrapper.HAS_BEEN_QUIT, false);
        prefs.setPreferenceBooleanValue(SipConfigManager.USE_3G_IN, true);
        prefs.setPreferenceBooleanValue(SipConfigManager.USE_3G_OUT, true);
        wizard.setDefaultParams(prefs);
        prefs.endEditing();
        applyNewAccountDefault(profile);

        if (!SipProfile.isProfileAlreadyExist(this, Long.parseLong(username))) {
            Log.i("SaveAccount", "Saving...");
            Uri uri = getContentResolver().insert(SipProfile.ACCOUNT_URI, profile.getDbContentValues());
            profile.id = ContentUris.parseId(uri);
            List<Filter> filters = wizard.getDefaultFilters(profile);
            if (filters != null) {
                for (Filter filter : filters) {
                    filter.account = (int) profile.id;
                    getContentResolver().insert(SipManager.FILTER_URI, filter.getDbContentValues());
                }
            }
        } else {
            Log.i("SaveAccount", "Already Exist");
        }
    }

    private void applyNewAccountDefault(SipProfile account) {
        if (account.use_rfc5626) {
            if (TextUtils.isEmpty(account.rfc5626_instance_id)) {
                String autoInstanceId = (UUID.randomUUID()).toString();
                account.rfc5626_instance_id = "<urn:uuid:" + autoInstanceId + ">";
            }
        }
    }

    private BroadcastReceiver mLoginReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean status = intent.getBooleanExtra("status", false);
            Log.d("Login", "Receive broadcast from login [" + status + "]");
            if (dialog.isShowing())
                dialog.dismiss();
            //if (status) {
            Intent i = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(i);
            finish();
//            } else {
//                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
//                        context);
//                AlertDialog dialog = alertDialogBuilder
//                        .setMessage("Login Failed")
//                        .setCancelable(false)
//                        .setPositiveButton("Dismiss", new DialogInterface.OnClickListener() {
//                            public void onClick(DialogInterface dialog, int id) {
//                                dialog.cancel();
//                            }
//                        }).create();
//                dialog.show();
//            }
        }
    };

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

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
