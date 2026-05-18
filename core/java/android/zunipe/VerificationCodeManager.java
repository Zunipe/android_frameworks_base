package android.zunipe;

import android.annotation.SystemService;
import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceManager.ServiceNotFoundException;
import android.service.notification.StatusBarNotification;

@SystemService(Context.VERIFICATION_CODE_SERVICE)
public class VerificationCodeManager {
    private final IVerificationCodeManager mService;
    private final Context mContext;
    static final Object sInstanceSync = new Object();
    private static VerificationCodeManager sInstance;

    VerificationCodeManager(Context context) throws ServiceNotFoundException {
        mContext = context;
        mService = IVerificationCodeManager.Stub.asInterface(
                ServiceManager.getServiceOrThrow(Context.VERIFICATION_CODE_SERVICE));
    }

    /**
     * @hide
     */
    public static VerificationCodeManager getInstance(Context cxt) throws ServiceNotFoundException {
        synchronized (sInstanceSync) {
            if (sInstance == null) {
                sInstance = new VerificationCodeManager(cxt);
            }
        }
        return sInstance;
    }

    /**
     * @hide
     */
    public void onPostNotification(StatusBarNotification sbn) {
        try {
            mService.onPostNotification(sbn);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     */
    public void registerCallback(IVerificationCallback callback) {
        try {
            mService.registerCallback(callback);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     */
    public void unregisterCallback(IVerificationCallback callback) {
        try {
            mService.unregisterCallback(callback);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }
}
