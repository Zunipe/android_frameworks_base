package com.android.systemui.gamemode;

import android.annotation.NonNull;
import android.content.Context;
import android.database.ContentObserver;
import android.os.UserHandle;
import android.zunipe.GameModeManager;

import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.util.settings.SecureSettings;

import javax.inject.Inject;

public class PerfBoostControllerImpl implements PerfBoostController {
    private boolean mEnable;
    private static final String SETTING_KEY = "enable_perf_boost";
    private final SecureSettings mSecureSettings;
    private final GameModeManager mGameModeManager;

    @Inject
    public PerfBoostControllerImpl(@NonNull @Main Context context,
                                   SecureSettings secureSettings,
                                   GameModeManager gameModeManager) {
        mSecureSettings = secureSettings;
        mGameModeManager = gameModeManager;
        mEnable = secureSettings.getBool(SETTING_KEY, false);

        ContentObserver contentObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                mEnable = secureSettings.getBool(SETTING_KEY, false);
//                mGameModeManager.setPerfBoost(mEnable);
            }
        };

        secureSettings.registerContentObserverForUserAsync(
                secureSettings.getUriFor(SETTING_KEY),
                false,
                contentObserver,
                UserHandle.USER_ALL
        );
    }

    @Override
    public boolean isEnabled() {
        return mEnable;
    }

    @Override
    public void setEnable(boolean enable) {
        mEnable = enable;
        mSecureSettings.putBool(SETTING_KEY, enable);
    }
}
