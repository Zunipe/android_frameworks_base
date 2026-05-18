package android.zunipe;

import android.service.notification.StatusBarNotification;
import android.zunipe.IVerificationCallback;

/** @hide */
interface IVerificationCodeManager {
    void onPostNotification(in StatusBarNotification sbn);
    void registerCallback(in IVerificationCallback callback);
    void unregisterCallback(in IVerificationCallback callback);
}
