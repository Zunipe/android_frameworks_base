package com.zunipe.server;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;
import android.zunipe.IGameModeManager;
import android.zunipe.IPerfMonitorCallback;
import com.android.server.SystemService;
import vendor.zunipe.perf.IPerf;

public class GameModeManagerService extends IGameModeManager.Stub {
    public static final int[] PERF_BUF = {1086324736, 1, 1082146816, 4095, 1082130432, 4095, 1082147072, 4095, 1082130688, 4095, 1082147328, 4095, 1082130944, 4095, 1082147584, 4095, 1082131200, 4095, 1115701248, 0, 1115684864, 1};
    public static IPerf sIPerf;
    public final Context mContext;
    public boolean mPerfBoostEnable;
    public int mPerfBoostHandler;
    public final Handler mHandler = new Handler(Looper.getMainLooper());
    public final int[] mFreqArray = new int[9];
    public final Runnable mMonitorRunnable = new Runnable() {
        @Override
        public void run() {
            int iBeginBroadcast = GameModeManagerService.this.mCallbacks.beginBroadcast();
            GameModeManagerService.this.mCallbacks.finishBroadcast();
            if (iBeginBroadcast > 0) {
                int currentGpuNodeValue = GameModeManagerService.this.getCurrentGpuNodeValue();
                boolean z = currentGpuNodeValue != GameModeManagerService.this.mFreqArray[8];
                GameModeManagerService.this.mFreqArray[8] = currentGpuNodeValue;
                for (int i = 0; i < 8; i++) {
                    int currentCpuNodeValue = GameModeManagerService.this.getCurrentCpuNodeValue(i);
                    if (currentCpuNodeValue != GameModeManagerService.this.mFreqArray[i]) {
                        GameModeManagerService.this.mFreqArray[i] = currentCpuNodeValue;
                        z = true;
                    }
                }
                if (z) {
                    GameModeManagerService.this.dispatchFreqChange();
                }
            }
            GameModeManagerService.this.mHandler.postDelayed(this, 500L);
        }
    };
    private final RemoteCallbackList<IPerfMonitorCallback> mCallbacks = new RemoteCallbackList<IPerfMonitorCallback>();

    public static IPerf getIPerf() {
        if (sIPerf == null) {
            sIPerf = IPerf.Stub.asInterface(ServiceManager.waitForDeclaredService("vendor.zunipe.perf.IPerf/default"));
        }
        return sIPerf;
    }

    public GameModeManagerService(Context context) {
        this.mContext = context;
    }

    public final void onBootCompleted() {
        Slog.d("GameModeManagerService", "onBootCompleted start monitor looper");
        this.mHandler.post(this.mMonitorRunnable);
    }

    public boolean setPerfBoost(boolean z) {
        if (z && !this.mPerfBoostEnable) {
            try {
                IPerf iPerf = getIPerf();
                int i = this.mPerfBoostHandler;
                int iPerfLockAcq = iPerf.perfLockAcq(i, 0, PERF_BUF, PERF_BUF.length);
                this.mPerfBoostHandler = iPerfLockAcq;
                this.mPerfBoostEnable = true;
                return iPerfLockAcq > 0;
            } catch (RemoteException e) {
                Slog.d("GameModeManagerService", "failed to invoke perfHal", e);
            }
        } else if (!z && this.mPerfBoostEnable) {
            try {
                int iPerfLockRel = getIPerf().perfLockRel(this.mPerfBoostHandler);
                this.mPerfBoostHandler = 0;
                this.mPerfBoostEnable = false;
                return iPerfLockRel > 0;
            } catch (RemoteException e2) {
                Slog.d("GameModeManagerService", "failed to invoke perfHal", e2);
            }
        }
        return false;
    }

    public int getCurrentCpuNodeValue(int i) {
        if (i >= 0 && i <= 7) {
            try {
                return getIPerf().getCpuFreq(i);
            } catch (RemoteException e) {
                Slog.d("GameModeManagerService", "failed to invoke perfHal", e);
            }
        }
        return -1;
    }

    public int getCurrentGpuNodeValue() {
        try {
            return getIPerf().getGpuFreq();
        } catch (RemoteException e) {
            Slog.d("GameModeManagerService", "failed to invoke perfHal", e);
            return -1;
        }
    }

    public final void dispatchFreqChange() {
        int iBeginBroadcast = this.mCallbacks.beginBroadcast();
        for (int i = 0; i < iBeginBroadcast; i++) {
            try {
                this.mCallbacks.getBroadcastItem(i).currentFreqChanging(this.mFreqArray);
            } catch (RemoteException unused) {
            } catch (Throwable th) {
                this.mCallbacks.finishBroadcast();
                throw th;
            }
        }
        this.mCallbacks.finishBroadcast();
    }

    public void registerCallback(IPerfMonitorCallback iPerfMonitorCallback) {
        this.mCallbacks.register(iPerfMonitorCallback);
    }

    public void unregisterCallback(IPerfMonitorCallback iPerfMonitorCallback) {
        this.mCallbacks.unregister(iPerfMonitorCallback);
    }

    public static class Lifecycle extends SystemService {
        public GameModeManagerService mService;

        public Lifecycle(Context context) {
            super(context);
        }

        @Override
        public void onStart() {
            this.mService = new GameModeManagerService(getContext());
            publishBinderService(Context.GAME_MODE_SERVICE, mService);
        }

        @Override
        public void onBootPhase(int i) {
            if (i == PHASE_BOOT_COMPLETED) {
                this.mService.onBootCompleted();
            }
        }
    }
}