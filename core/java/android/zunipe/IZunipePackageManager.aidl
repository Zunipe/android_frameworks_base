package android.zunipe;

import android.content.ComponentName;

/** @hide */
interface IZunipePackageManager {
    void startHideActivity(in ComponentName name);
    void hideApplication(in String packageName);
    void revealApplication(in String packageName);
    List<String> getHideApplicationList();
}
