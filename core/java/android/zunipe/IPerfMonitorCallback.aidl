package android.zunipe;

/** @hide */
interface IPerfMonitorCallback {
    oneway void currentFreqChanging(in int[] value);
}