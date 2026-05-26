package com.android.systemui.mute;

import android.annotation.NonNull;
import android.content.Context;
import android.database.ContentObserver;
import android.os.UserHandle;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.util.settings.SecureSettings;

import javax.inject.Inject;

@SysUISingleton
public class MuteControllerImpl implements MuteController {
    private boolean mEnable;
    private static final String SETTING_KEY = "show_mute_hud";
    private final SecureSettings mSecureSettings;

    @Inject
    public MuteControllerImpl(@NonNull @Main Context context,
                              SecureSettings secureSettings) {
        mSecureSettings = secureSettings;
        mEnable = secureSettings.getBool(SETTING_KEY, false);

        ContentObserver contentObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                mEnable = secureSettings.getBool(SETTING_KEY, false);
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
