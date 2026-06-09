package android.zunipe;

import android.content.ComponentName;

/** @hide */
interface IZunipePackageManager {
    void startHideActivity(in String packageName);
    void hideApplication(in String packageName);
    void revealApplication(in String packageName);
    List<String> getHideApplicationList();
}
