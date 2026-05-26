package com.android.systemui.qs.tiles;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.gamemode.PerfBoostController;
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

public class PerfBoostTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "perf_boost";
    private boolean mEnabled;
    private final PerfBoostController mPerfBoostController;

    @Inject
    public PerfBoostTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            PerfBoostController perfBoostController) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mPerfBoostController = perfBoostController;
        mEnabled = perfBoostController.isEnabled();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick(Expandable expandable) {
        mPerfBoostController.setEnable(!mEnabled);
        mEnabled = mPerfBoostController.isEnabled();

        refreshState();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = mEnabled;
        state.label = mContext.getString(R.string.perf_boost_switch_title);
        state.secondaryLabel = "";
        state.icon = ResourceIcon.get(R.drawable.qs_perf_boost_icon);

        if (mEnabled) {
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.state = Tile.STATE_INACTIVE;
        }
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.perf_boost_switch_title);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_FLASHLIGHT;
    }
}

