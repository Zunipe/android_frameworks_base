package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.mute.MuteController;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;

import javax.inject.Inject;

public class MuteTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "mute";
    private boolean mEnabled;
    private final MuteController mMuteController;

    @Inject
    public MuteTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            MuteController muteController) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mMuteController = muteController;
        mEnabled = muteController.isEnabled();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick(Expandable expandable) {
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        int currentMode = audioManager.getRingerMode();

        if (currentMode == AudioManager.RINGER_MODE_NORMAL) {
            audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        } else {
            audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        }

        refreshState();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        boolean isNormalMode = (audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT);

        mEnabled = isNormalMode;

        state.value = mEnabled;
        state.label = mContext.getString(R.string.mute_switch_title);

        state.icon = ResourceIcon.get(mEnabled ? R.drawable.ic_alarm : R.drawable.ic_alarm);
        state.state = mEnabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;

    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.mute_switch_title);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_FLASHLIGHT;
    }
}