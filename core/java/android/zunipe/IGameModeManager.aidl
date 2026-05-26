package android.zunipe;

import android.zunipe.IPerfMonitorCallback;

/** @hide */
interface IGameModeManager {
    int getCurrentCpuNodeValue(int core);
    int getCurrentGpuNodeValue();
    boolean setPerfBoost(boolean enable);
    void registerCallback(in IPerfMonitorCallback callback);
    void unregisterCallback(in IPerfMonitorCallback callback);
}
