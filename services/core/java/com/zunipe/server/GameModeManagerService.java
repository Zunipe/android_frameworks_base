package com.zunipe.server;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;
import android.zunipe.IGameModeManager;

import com.android.server.SystemService;

import vendor.zunipe.perf.IPerf;

public class GameModeManagerService extends IGameModeManager.Stub {
    public static IPerf sIPerf;
    public final Context mContext;

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
    }

    @Override
    public void setPerfBoost(boolean enable) {
        try {
            if (enable) {
                getIPerf().perfLockAcq(0, 0, new int[]{1}, 0);
            } else {
                getIPerf().perfLockRel(0);
            }
        } catch (RemoteException e) {
            Slog.d("GameModeManagerService", "failed to invoke perfHal", e);
        }
    }

    @Override
    public int getCpuFreq(int i) {
        if (i >= 0 && i <= 7) {
            try {
                return getIPerf().getCpuFreq(i);
            } catch (RemoteException e) {
                Slog.d("GameModeManagerService", "failed to invoke perfHal", e);
            }
        }
        return -1;
    }

    @Override
    public int getGpuFreq() {
        try {
            return getIPerf().getGpuFreq();
        } catch (RemoteException e) {
            Slog.d("GameModeManagerService", "failed to invoke perfHal", e);
            return -1;
        }
    }

    @Override
    public int getCpuThermal(int core){
        return 0;
    }

    @Override
    public int getGpuThermal() {
        return 0;
    }

    @Override
    public int getBatteryThermal() {
        return 0;
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
