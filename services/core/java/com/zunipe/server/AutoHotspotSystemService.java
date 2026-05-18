package com.zunipe.server;

import static android.net.TetheringManager.TETHERING_WIFI;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLING;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.android.server.SystemService;

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

                mConnectivityManager.startTethering(TETHERING_WIFI, true /* showProvisioningUi */,
                        mOnStartTetheringCallback, new Handler(Looper.getMainLooper()));
            }
        }
    }
}
