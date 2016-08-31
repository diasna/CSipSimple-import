package com.csipsimple.ui;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.csipsimple.R;
import com.csipsimple.api.ISipService;
import com.csipsimple.api.MediaState;
import com.csipsimple.api.SipCallSession;
import com.csipsimple.api.SipManager;
import com.csipsimple.api.SipProfile;
import com.csipsimple.api.SipUri;
import com.csipsimple.db.DBProvider;
import com.csipsimple.models.Filter;
import com.csipsimple.service.SipService;
import com.csipsimple.ui.account.AccountsEditList;
import com.csipsimple.ui.help.Help;
import com.csipsimple.ui.incall.IOnCallActionTrigger;
import com.csipsimple.utils.CallsUtils;
import com.csipsimple.utils.Compatibility;
import com.csipsimple.utils.CustomDistribution;
import com.csipsimple.utils.Log;
import com.csipsimple.utils.PreferencesProviderWrapper;
import com.csipsimple.utils.PreferencesWrapper;
import com.csipsimple.utils.backup.BackupWrapper;
import com.csipsimple.wizards.BasePrefsWizard;
import com.csipsimple.wizards.WizardIface;
import com.csipsimple.wizards.WizardUtils;
import com.csipsimple.wizards.impl.Basic;

import org.w3c.dom.Text;

import java.util.List;
import java.util.UUID;

public class CustomHome extends SherlockFragmentActivity {

    public static final int ACCOUNTS_MENU = Menu.FIRST + 1;
    public static final int PARAMS_MENU = Menu.FIRST + 2;
    public static final int CLOSE_MENU = Menu.FIRST + 3;
    public static final int HELP_MENU = Menu.FIRST + 4;
    public static final int DISTRIB_ACCOUNT_MENU = Menu.FIRST + 5;

    private static final String THIS_FILE = "SIP_HOME";

    private static final int REQUEST_EDIT_DISTRIBUTION_ACCOUNT = 0;

    private PreferencesProviderWrapper prefProviderWrapper;


    protected SipProfile account = null;
    private WizardIface wizard = null;

    private Object callMutex = new Object();
    private SipCallSession[] callsInfo = null;
    private MediaState lastMediaState;

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefProviderWrapper = new PreferencesProviderWrapper(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Log.setLogLevel(4);
        try {
            wizard = Basic.class.newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        account = SipProfile.getProfileFromDbId(this, 1018, DBProvider.ACCOUNT_FULL_PROJECTION);
        Log.d("Account", "Account 1018 IS ["+account+"]");

        //saveAccount();
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    service.makeCallWithOptions("1005", 1006, null);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.button11).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (service != null) {
                    try {
                        service.hangup(callsInfo[0].getCallId(), 0);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        status = (TextView) findViewById(R.id.textView2);

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
                if (service != null) {
                    try {
                        synchronized (callMutex) {
                            callsInfo = service.getCalls();
                            if (callsInfo[0] != null) {
                                Log.d(THIS_FILE, "Active call is xxxxxxx" + callsInfo[0].getCallId());
                                Log.d(THIS_FILE, "Update ui from call xxxxxx" + callsInfo[0].getCallId() + " state "
                                        + CallsUtils.getStringCallState(callsInfo[0], CustomHome.this));
                                int state = callsInfo[0].getCallState();
                                switch (state) {
                                    case SipCallSession.InvState.INCOMING:
                                        status.setText("INCOMING");
                                        return;
                                    case SipCallSession.InvState.EARLY:
                                        status.setText("EARLY");
                                        return;
                                    case SipCallSession.InvState.CALLING:
                                        status.setText("CALLING");
                                        return;
                                    case SipCallSession.InvState.CONNECTING:
                                        status.setText("CONNECTING");
                                        return;
                                    case SipCallSession.InvState.CONFIRMED:
                                        status.setText("CONFIRMED");
                                        return;
                                    case SipCallSession.InvState.NULL:
                                        status.setText("NULL");
                                        return;
                                    case SipCallSession.InvState.DISCONNECTED:
                                        Log.d(THIS_FILE, "Active call session is disconnected or null wait for quit...");
                                        return;
                                }
                            }
                        }
                    } catch (RemoteException e) {
                        Log.e(THIS_FILE, "Not able to retrieve calls");
                    }
                }
            } else if (action.equals(SipManager.ACTION_SIP_MEDIA_CHANGED)) {
                if (service != null) {
                    MediaState mediaState;
                    try {
                        mediaState = service.getCurrentMediaState();
                        Log.d(THIS_FILE, "Media update ...." + mediaState.isSpeakerphoneOn);
                        synchronized (callMutex) {
                            if (!mediaState.equals(lastMediaState)) {
                                lastMediaState = mediaState;
                            }
                        }
                    } catch (RemoteException e) {
                        Log.e(THIS_FILE, "Can't get the media state ", e);
                    }
                }
            }

        }
    };

    private void startSipService() {
        Thread t = new Thread("StartSip") {
            public void run() {
                Intent serviceIntent = new Intent(SipManager.INTENT_SIP_SERVICE);
                // Optional, but here we bundle so just ensure we are using csipsimple package
                serviceIntent.setPackage(CustomHome.this.getPackageName());
                serviceIntent.putExtra(SipManager.EXTRA_OUTGOING_ACTIVITY, new ComponentName(CustomHome.this, CustomHome.class));
                startService(serviceIntent);
            }
        };
        t.start();
    }

    @Override
    protected void onPause() {
        Log.d(THIS_FILE, "On Pause SIPHOME");
        super.onPause();
        unbindService(connection);
    }

    @Override
    protected void onResume() {
        Log.d(THIS_FILE, "On Resume SIPHOME");
        super.onResume();

        prefProviderWrapper.setPreferenceBooleanValue(PreferencesWrapper.HAS_BEEN_QUIT, false);

        Log.d(THIS_FILE, "WE CAN NOW start SIP service");
        startSipService();
        Intent serviceIntent = new Intent(SipManager.INTENT_SIP_SERVICE);

        serviceIntent.setPackage(CustomHome.this.getPackageName());

        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);

        registerReceiver(callStateReceiver, new IntentFilter(SipManager.ACTION_SIP_CALL_CHANGED));
        registerReceiver(callStateReceiver, new IntentFilter(SipManager.ACTION_SIP_MEDIA_CHANGED));
        registerReceiver(callStateReceiver, new IntentFilter(SipManager.ACTION_ZRTP_SHOW_SAS));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    @Override
    protected void onDestroy() {
        disconnect(false);
        super.onDestroy();
        Log.d(THIS_FILE, "---DESTROY SIP HOME END---");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        int actionRoom = getResources().getBoolean(R.bool.menu_in_bar) ? MenuItem.SHOW_AS_ACTION_IF_ROOM : MenuItem.SHOW_AS_ACTION_NEVER;

        WizardUtils.WizardInfo distribWizard = CustomDistribution.getCustomDistributionWizard();
        if (distribWizard != null) {
            menu.add(Menu.NONE, DISTRIB_ACCOUNT_MENU, Menu.NONE, "My " + distribWizard.label)
                    .setIcon(distribWizard.icon)
                    .setShowAsAction(actionRoom);
        }
        if (CustomDistribution.distributionWantsOtherAccounts()) {
            int accountRoom = actionRoom;
            if (Compatibility.isCompatible(13)) {
                accountRoom |= MenuItem.SHOW_AS_ACTION_WITH_TEXT;
            }
            menu.add(Menu.NONE, ACCOUNTS_MENU, Menu.NONE,
                    (distribWizard == null) ? R.string.accounts : R.string.other_accounts)
                    .setIcon(R.drawable.ic_menu_account_list)
                    .setAlphabeticShortcut('a')
                    .setShowAsAction(accountRoom);
        }
        menu.add(Menu.NONE, PARAMS_MENU, Menu.NONE, R.string.prefs)
                .setIcon(android.R.drawable.ic_menu_preferences)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        menu.add(Menu.NONE, HELP_MENU, Menu.NONE, R.string.help)
                .setIcon(android.R.drawable.ic_menu_help)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, CLOSE_MENU, Menu.NONE, R.string.menu_disconnect)
                .setIcon(R.drawable.ic_lock_power_off)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case ACCOUNTS_MENU:
                Log.d("LOL", "WTF");
                startActivity(new Intent(this, AccountsEditList.class));
                return true;
            case PARAMS_MENU:
                startActivityForResult(new Intent(SipManager.ACTION_UI_PREFS_GLOBAL), CHANGE_PREFS);
                return true;
            case CLOSE_MENU:
                Log.d(THIS_FILE, "CLOSE");
                boolean currentlyActiveForIncoming = prefProviderWrapper.isValidConnectionForIncoming();
                boolean futureActiveForIncoming = (prefProviderWrapper.getAllIncomingNetworks().size() > 0);
                if (currentlyActiveForIncoming || futureActiveForIncoming) {
                    // Alert user that we will disable for all incoming calls as
                    // he want to quit
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.warning)
                            .setMessage(
                                    getString(currentlyActiveForIncoming ? R.string.disconnect_and_incoming_explaination
                                            : R.string.disconnect_and_future_incoming_explaination))
                            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    prefProviderWrapper.setPreferenceBooleanValue(PreferencesWrapper.HAS_BEEN_QUIT, true);
                                    disconnect(true);
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                } else {
                    disconnect(true);
                }
                return true;
            case HELP_MENU:
                // Create the fragment and show it as a dialog.
                DialogFragment newFragment = Help.newInstance();
                newFragment.show(getSupportFragmentManager(), "dialog");
                return true;
            case DISTRIB_ACCOUNT_MENU:
                WizardUtils.WizardInfo distribWizard = CustomDistribution.getCustomDistributionWizard();

                Cursor c = getContentResolver().query(SipProfile.ACCOUNT_URI, new String[]{
                        SipProfile.FIELD_ID
                }, SipProfile.FIELD_WIZARD + "=?", new String[]{
                        distribWizard.id
                }, null);

                Intent it = new Intent(this, BasePrefsWizard.class);
                it.putExtra(SipProfile.FIELD_WIZARD, distribWizard.id);
                Long accountId = null;
                if (c != null && c.getCount() > 0) {
                    try {
                        c.moveToFirst();
                        accountId = c.getLong(c.getColumnIndex(SipProfile.FIELD_ID));
                    } catch (Exception e) {
                        Log.e(THIS_FILE, "Error while getting wizard", e);
                    } finally {
                        c.close();
                    }
                }
                if (accountId != null) {
                    it.putExtra(SipProfile.FIELD_ID, accountId);
                }
                startActivityForResult(it, REQUEST_EDIT_DISTRIBUTION_ACCOUNT);

                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private final static int CHANGE_PREFS = 1;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CHANGE_PREFS) {
            sendBroadcast(new Intent(SipManager.ACTION_SIP_REQUEST_RESTART));
            BackupWrapper.getInstance(this).dataChanged();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void disconnect(boolean quit) {
        Log.d(THIS_FILE, "True disconnection...");
        Intent intent = new Intent(SipManager.ACTION_OUTGOING_UNREGISTER);
        intent.putExtra(SipManager.EXTRA_OUTGOING_ACTIVITY, new ComponentName(this, SipHome.class));
        sendBroadcast(intent);
        if (quit) {
            finish();
        }
    }

    private void applyNewAccountDefault(SipProfile account) {
        if(account.use_rfc5626) {
            if(TextUtils.isEmpty(account.rfc5626_instance_id)) {
                String autoInstanceId = (UUID.randomUUID()).toString();
                account.rfc5626_instance_id = "<urn:uuid:"+autoInstanceId+">";
            }
        }
    }

    public SipProfile buildAccount(SipProfile account) {
        Log.d(THIS_FILE, "begin of save ....");
        account.display_name = "diaspinter";

        account.acc_id = "<sip:" + 1006 + "@voip.satuerp.com>";

        String regUri = "sip:voip.satuerp.com";
        account.reg_uri = regUri;
        account.proxies = new String[] { regUri } ;

        account.realm = "*";
        account.username = "1006".trim();
        account.data = "1234";
        account.scheme = SipProfile.CRED_SCHEME_DIGEST;
        account.datatype = SipProfile.CRED_DATA_PLAIN_PASSWD;
        //By default auto transport
        account.transport = SipProfile.TRANSPORT_UDP;
        return account;
    }

    private void saveAccount() {
        PreferencesWrapper prefs = new PreferencesWrapper(getApplicationContext());
        account = buildAccount(account);
        account.wizard = "BASIC";
        // This account does not exists yet
        prefs.startEditing();
        wizard.setDefaultParams(prefs);
        prefs.endEditing();
        applyNewAccountDefault(account);
        Uri uri = getContentResolver().insert(SipProfile.ACCOUNT_URI, account.getDbContentValues());
        Log.d("DefaultAccount", "[" + account.getDbContentValues() + "]");
        // After insert, add filters for this wizard
        account.id = ContentUris.parseId(uri);
        List<Filter> filters = wizard.getDefaultFilters(account);
        if (filters != null) {
            for (Filter filter : filters) {
                // Ensure the correct id if not done by the wizard
                filter.account = (int) account.id;
                getContentResolver().insert(SipManager.FILTER_URI, filter.getDbContentValues());
            }
        }
        Intent intent = new Intent(SipManager.ACTION_SIP_REQUEST_RESTART);
        sendBroadcast(intent);
    }
}
