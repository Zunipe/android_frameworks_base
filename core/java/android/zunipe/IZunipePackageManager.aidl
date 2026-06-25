package android.zunipe;

import android.content.pm.ResolveInfo;

/** @hide */
interface IZunipePackageManager {
    void startHideActivity(in String packageName);
    void hideApplication(in String packageName);
    void revealApplication(in String packageName);
    List<String> getHideApplicationList();
    ResolveInfo getResolveInfo(in String packageName);
}
