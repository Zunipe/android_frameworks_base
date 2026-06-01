package android.zunipe;

import android.annotation.SystemService;
import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceManager.ServiceNotFoundException;

@SystemService(Context.GAME_MODE_SERVICE)
public class GameModeManager {
    private final IGameModeManager mService;
    private final Context mContext;
    static final Object sInstanceSync = new Object();
    private static GameModeManager sInstance;

    GameModeManager(Context context) throws ServiceNotFoundException {
        mContext = context;
        mService = IGameModeManager.Stub.asInterface(
                ServiceManager.getServiceOrThrow(Context.GAME_MODE_SERVICE));
    }

    /**
     * @hide
     */
    public static GameModeManager getInstance(Context cxt) throws ServiceNotFoundException {
        synchronized (sInstanceSync) {
            if (sInstance == null) {
                sInstance = new GameModeManager(cxt);
            }
        }
        return sInstance;
    }

    /**
     * @hide
     */
    public int getCpuFreq(int core) {
        try {
            return mService.getCpuFreq(core);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     */
    public int getGpuFreq() {
        try {
            return mService.getGpuFreq();
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }
}
