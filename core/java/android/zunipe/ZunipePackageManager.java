package android.zunipe;

import android.annotation.SystemService;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceManager.ServiceNotFoundException;

import java.util.List;

@SystemService(Context.ZUNIPE_PACKAGE_SERVICE)
public class ZunipePackageManager {
    private final Context mContext;
    private final IZunipePackageManager mService;
    static final Object sInstanceSync = new Object();
    private static ZunipePackageManager sInstance;

    ZunipePackageManager(Context context) throws ServiceNotFoundException {
        mContext = context;
        mService = IZunipePackageManager.Stub.asInterface(
                ServiceManager.getServiceOrThrow(Context.ZUNIPE_PACKAGE_SERVICE));
    }

    /**
     * @hide
     */
    public static ZunipePackageManager getInstance(Context cxt) throws ServiceNotFoundException {
        synchronized (sInstanceSync) {
            if (sInstance == null) {
                sInstance = new ZunipePackageManager(cxt);
            }
        }
        return sInstance;
    }

    public void startHideActivity(String packageName) {
        try {
            mService.startHideActivity(packageName);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    public void hideApplication(String packageName) {
        try {
            mService.hideApplication(packageName);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    public void revealApplication(String packageName) {
        try {
            mService.revealApplication(packageName);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    public List<String> getHideApplicationList() {
        try {
            return mService.getHideApplicationList();
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    public ResolveInfo getResolveInfo(String packageName) {
        try {
            return mService.getResolveInfo(packageName);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }
}
