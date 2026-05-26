package android.zunipe;

import android.zunipe.IZunipeGestureCallback;

/** @hide */
interface IZunipeInputManager {
    void registerCallback(in IZunipeGestureCallback callback);
    void unregisterCallback(in IZunipeGestureCallback callback);
}