package com.zunipe.server;

import static android.net.TetheringManager.TETHERING_WIFI;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLING;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.android.server.SystemService;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalTime;

public class AutoHotspotSystemService extends SystemService {
    public static final String TAG = "AutoHotspotSystemService";
    private final Context mContext;
    private final WifiManager mWifiManager;
    private final ConnectivityManager mConnectivityManager;
    private static final String MAIN_SWITCH_PREF_KEY = "wifi_hotspot_auto_enable";
    private static final String PERIODIC_CLOSE_SWITCH_PREF_KEY = "periodic_close";
    private static final String PERIODIC_CLOSE_START_PREF_KEY = "periodic_close_start_time";
    private static final String PERIODIC_CLOSE_END_PREF_KEY = "periodic_close_end_time";
    private static final int DEFAULT_START_TIME = LocalTime.of(23, 0).toSecondOfDay();
    private static final int DEFAULT_END_TIME = LocalTime.of(9, 0).toSecondOfDay();
    private boolean mIsCloseByService = false;

    final ConnectivityManager.OnStartTetheringCallback mOnStartTetheringCallback =
            new ConnectivityManager.OnStartTetheringCallback() {
                @Override
                public void onTetheringFailed() {
                    super.onTetheringFailed();
                    Log.e(TAG, "Failed to start Wi-Fi Tethering.");
                }
            };

    final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_TIME_TICK.equals(intent.getAction())) {
                if (isEnablePeriodicClose()) {
                    LocalTime localTime = LocalTime.now();
                    LocalTime startTime = getPeriodicStartTime();
                    LocalTime endTime = getPeriodicEndTime();
                    if (isSameTime(localTime, startTime)) {
                        Log.d(TAG, "Now the time is start time, close the hot spot");
                        setHotspotStatus(false);
                        mIsCloseByService = true;
                    }

                    if (mIsCloseByService && isSameTime(localTime, endTime)) {
                        Log.d(TAG, "Now the time is the end time, open the hot spot");
                        setHotspotStatus(true);
                        mIsCloseByService = false;
                    }
                }
            }
        }
    };

    public AutoHotspotSystemService(Context context) {
        super(context);
        mContext = context;
        mConnectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    @Override
    public void onStart() {

    }

    private boolean isSameTime(LocalTime a, LocalTime b) {
        if (a == b) return true;
        return a.getHour() == b.getHour()
                && a.getMinute() == b.getMinute();
    }

    private LocalTime getPeriodicStartTime() {
        int time = Settings.System.getInt(mContext.getContentResolver(), PERIODIC_CLOSE_START_PREF_KEY, DEFAULT_START_TIME);
        return LocalTime.ofSecondOfDay(time);
    }

    private LocalTime getPeriodicEndTime() {
        int time = Settings.System.getInt(mContext.getContentResolver(), PERIODIC_CLOSE_END_PREF_KEY, DEFAULT_END_TIME);
        return LocalTime.ofSecondOfDay(time);
    }

    private boolean isEnablePeriodicClose() {
        return Settings.System.getInt(mContext.getContentResolver(), MAIN_SWITCH_PREF_KEY, 0) == 1 &&
                Settings.System.getInt(mContext.getContentResolver(), PERIODIC_CLOSE_SWITCH_PREF_KEY, 0) == 1;
    }

    private boolean isWifiApActivated() {
        final int wifiApState = mWifiManager.getWifiApState();
        if (wifiApState == WIFI_AP_STATE_ENABLED || wifiApState == WIFI_AP_STATE_ENABLING) {
            return true;
        }
        return false;
    }

    private void setHotspotStatus(boolean enable) {
        if (enable) {
            mConnectivityManager.startTethering(TETHERING_WIFI, true /* showProvisioningUi */,
                    mOnStartTetheringCallback, new Handler(Looper.getMainLooper()));
        } else {
            mConnectivityManager.stopTethering(TETHERING_WIFI);
        }
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_BOOT_COMPLETED) {
            boolean enable = Settings.System.getInt(mContext.getContentResolver(), MAIN_SWITCH_PREF_KEY, 0) != 0;
            if (enable) {
                SoftApConfiguration.Builder configBuilder =
                        new SoftApConfiguration.Builder(mWifiManager.getSoftApConfiguration());
                configBuilder.setAutoShutdownEnabled(false);
                mWifiManager.setSoftApConfiguration(configBuilder.build());
                setHotspotStatus(true);
            }
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_TIME_TICK);
            mContext.registerReceiver(mBroadcastReceiver, intentFilter);

            try {
                if (mContext.getResources().getBoolean(com.android.internal.R.bool.config_enableAutoConnectWifi)) {
                    connectWifi();
                }
            } catch (CertificateException | IOException | PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Automatic WiFi connection failed!", e);
            }
        }
    }

    private void connectWifi() throws CertificateException, IOException, PackageManager.NameNotFoundException {
        Context systemContext = mContext.createPackageContext(
                "android",
                Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY
        );
        AssetManager assetManager = systemContext.getAssets();
        InputStream is = assetManager.open("NCTLCer.cer");
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate caCertificate = (X509Certificate) cf.generateCertificate(is);
        is.close();
        Log.d(TAG, "load certificate");

        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = String.format("\"%s\"", mContext.getResources().getString(com.android.internal.R.string.auto_connect_wifi_ssid));
        wifiConfig.status = WifiConfiguration.Status.ENABLED;

        wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);
        wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X);

        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.PEAP);
        enterpriseConfig.setPhase2Method(WifiEnterpriseConfig.Phase2.MSCHAPV2);
        enterpriseConfig.setDomainSuffixMatch(mContext.getResources().getString(com.android.internal.R.string.auto_connect_wifi_domain_suffix));
        enterpriseConfig.setCaCertificate(caCertificate);
        enterpriseConfig.setIdentity(mContext.getResources().getString(com.android.internal.R.string.auto_connect_wifi_identity));
        enterpriseConfig.setAnonymousIdentity("");
        enterpriseConfig.setPassword(mContext.getResources().getString(com.android.internal.R.string.auto_connect_wifi_password));

        wifiConfig.enterpriseConfig = enterpriseConfig;
        Log.d(TAG, "set wifi config");

        int netId = mWifiManager.addNetwork(wifiConfig);
        Log.d(TAG, "addNetwork netId = " + netId);

        if (netId != -1) {
            mWifiManager.enableNetwork(netId, true);
            boolean reconnected = mWifiManager.reconnect();
            Log.d(TAG, "reconnect result = " + reconnected);
        } else {
            Log.e(TAG, "Failed to add enterprise network config");
        }
    }
}
