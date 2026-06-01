package android.zunipe;

/** @hide */
interface IGameModeManager {
    int getCpuFreq(int core);
    int getGpuFreq();
    int getCpuThermal(int core);
    int getGpuThermal();
    int getBatteryThermal();
}
