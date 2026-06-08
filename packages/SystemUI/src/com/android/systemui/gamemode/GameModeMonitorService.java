package com.android.systemui.gamemode;

import android.annotation.NonNull;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.zunipe.GameModeManager;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.systemui.CoreStartable;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.res.R;
import com.android.systemui.util.settings.SecureSettings;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

public class GameModeMonitorService implements CoreStartable {

    public static final String TAG = "GameModeMonitorServices";
    public static final int MSG_UPDATE_FREQ = 1;
    private static final String SETTING_KEY = "show_performance_hud";
    private static final int MASK_CPU_FREQ = 0x0FF;
    private static final int MASK_GPU_FREQ = 0x100;
    private final Context mContext;
    private final Handler mHandler;
    private final GameModeManager mGameModeManager;
    private final SecureSettings mSecureSettings;
    private final DraggableWindowsHelper mDraggableWindowsHelper;
    private boolean mEnable;
    private PerfAdapter mPerfAdapter;

    @Inject
    public GameModeMonitorService(
            @NonNull @Main Context context,
            GameModeManager GameModeManager,
            SecureSettings secureSettings,
            DraggableWindowsHelper draggableWindowsHelper) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_UPDATE_FREQ -> {
                        updateFreq();
                        Message message = mHandler.obtainMessage(MSG_UPDATE_FREQ);
                        mHandler.sendMessageDelayed(message, 1000);
                    }
                }
            }
        };
        mGameModeManager = GameModeManager;
        mSecureSettings = secureSettings;
        mDraggableWindowsHelper = draggableWindowsHelper;
    }

    private void updateState() {
        if (mEnable) {
            showPerfPopup();
        } else {
            dismissPopup();
        }
    }

    @Override
    public void onBootCompleted() {
        mEnable = mSecureSettings.getBool(SETTING_KEY, false);
        updateState();

        ContentObserver contentObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                mEnable = mSecureSettings.getBool(SETTING_KEY, false);
                updateState();
            }
        };

        mSecureSettings.registerContentObserverForUserAsync(
                mSecureSettings.getUriFor(SETTING_KEY),
                false,
                contentObserver,
                UserHandle.USER_ALL
        );
    }

    @Override
    public void start() {

    }

    private View createPerfGridView() {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        RecyclerView recyclerView = (RecyclerView) inflater.inflate(R.layout.view_perf_grid, null);

        GridLayoutManager gridLayoutManager = new GridLayoutManager(mContext, 2);
        recyclerView.setLayoutManager(gridLayoutManager);

        mPerfAdapter = new PerfAdapter();
        recyclerView.setAdapter(mPerfAdapter);

        initTestData();

        return recyclerView;
    }

    private void initTestData() {
        int featureCode = mSecureSettings.getInt("perf_monitor_option", 0);
        List<PerfItem> list = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int bit = 1 << i;
            if ((featureCode & bit) != 0) {
                list.add(new PerfItem("CPU" + i, mGameModeManager.getCpuFreq(i) / 1000 + "Mhz", Color.GREEN));
            }
        }
        if ((featureCode & MASK_GPU_FREQ) != 0) {
            list.add(new PerfItem("GPU", mGameModeManager.getGpuFreq() / 1000 / 1000 + "Mhz", Color.CYAN));
        }
        mPerfAdapter.setData(list);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_UPDATE_FREQ), 1000);
    }

    private void updateFreq() {
        int featureCode = mSecureSettings.getInt("perf_monitor_option", 0);
        for (int i = 0; i < 8; i++) {
            int bit = 1 << i;
            if ((featureCode & bit) != 0) {
                mPerfAdapter.updateItem(i, mGameModeManager.getCpuFreq(i) / 1000 + "Mhz");
            }
        }
        if ((featureCode & MASK_GPU_FREQ) != 0) {
            mPerfAdapter.updateItem(8, mGameModeManager.getGpuFreq() / 1000 / 1000 + "Mhz");
        }
    }

    private void showPerfPopup() {
        if (mDraggableWindowsHelper.isShowing()) return;

        final View popupView = createPerfGridView();

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        mDraggableWindowsHelper.show(params, popupView);
    }

    private void dismissPopup() {
        try {
            if (mDraggableWindowsHelper.dismiss()) {
                mHandler.removeMessages(MSG_UPDATE_FREQ);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }
}
