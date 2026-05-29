package com.zunipe.server;

import static android.net.TetheringManager.TETHERING_WIFI;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLING;

import android.content.Context;
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

public class AutoHotspotSystemService extends SystemService {
    public static final String TAG = "AutoHotspotSystemService";
    private final Context mContext;
    private final WifiManager mWifiManager;
    private final ConnectivityManager mConnectivityManager;
    private static final String PREF_KEY = "wifi_hotspot_auto_enable";

    final ConnectivityManager.OnStartTetheringCallback mOnStartTetheringCallback =
            new ConnectivityManager.OnStartTetheringCallback() {
                @Override
                public void onTetheringFailed() {
                    super.onTetheringFailed();
                    Log.e(TAG, "Failed to start Wi-Fi Tethering.");
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

    private boolean isWifiApActivated() {
        final int wifiApState = mWifiManager.getWifiApState();
        if (wifiApState == WIFI_AP_STATE_ENABLED || wifiApState == WIFI_AP_STATE_ENABLING) {
            return true;
        }
        return false;
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_BOOT_COMPLETED) {
            boolean enable = Settings.System.getInt(mContext.getContentResolver(), PREF_KEY, 0) != 0;
            if (enable) {
                if (isWifiApActivated()) return;

                SoftApConfiguration.Builder configBuilder =
                        new SoftApConfiguration.Builder(mWifiManager.getSoftApConfiguration());
                configBuilder.setAutoShutdownEnabled(false);
                mWifiManager.setSoftApConfiguration(configBuilder.build());

                mConnectivityManager.startTethering(TETHERING_WIFI, true /* showProvisioningUi */,
                        mOnStartTetheringCallback, new Handler(Looper.getMainLooper()));
            }

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
