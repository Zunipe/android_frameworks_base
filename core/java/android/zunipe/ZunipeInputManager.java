package android.zunipe;

import android.annotation.SystemService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceManager.ServiceNotFoundException;
import android.view.InputDevice;
import android.view.KeyEvent;

@SystemService(Context.ZUNIPE_INPUT_MANAGER)
public class ZunipeInputManager {
    public static final String TAG = "ZunipeInputManager";
    public static final int TYPE_GESTURE_THREE_FINGER_SLIDE = 1;

    private final Context mContext;
    private final IZunipeInputManager mService;
    private final InputManager mInputManager;
    private final ClipboardManager mClipboardManager;
    static final Object sInstanceSync = new Object();
    private static final Object sInputSync = new Object();
    private static ZunipeInputManager sInstance;

    ZunipeInputManager(Context context) throws ServiceNotFoundException {
        mContext = context;
        mInputManager = mContext.getSystemService(InputManager.class);
        mClipboardManager = mContext.getSystemService(ClipboardManager.class);
        mService = IZunipeInputManager.Stub.asInterface(
                ServiceManager.getServiceOrThrow(Context.ZUNIPE_INPUT_MANAGER));
    }

    /**
     * @hide
     */
    public static ZunipeInputManager getInstance(Context cxt) throws ServiceNotFoundException {
        synchronized (sInstanceSync) {
            if (sInstance == null) {
                sInstance = new ZunipeInputManager(cxt);
            }
        }
        return sInstance;
    }

    /**
     * @hide
     */
    public void registerCallback(IZunipeGestureCallback callback) {
        try {
            mService.registerCallback(callback);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     */
    public void unregisterCallback(IZunipeGestureCallback callback) {
        try {
            mService.unregisterCallback(callback);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     */
    public void pasteString(String text) {
        synchronized (sInputSync) {
            ClipData clip = ClipData.newPlainText("text", text);
            mClipboardManager.setPrimaryClip(clip);

            int mode = InputManager.INJECT_INPUT_EVENT_MODE_ASYNC;
            long t = android.os.SystemClock.uptimeMillis();

            int metaState = KeyEvent.META_CTRL_ON;

            KeyEvent ctrlDown = new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0);
            ctrlDown.setSource(InputDevice.SOURCE_KEYBOARD);

            KeyEvent vDown = new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_V, 0, metaState);
            vDown.setSource(InputDevice.SOURCE_KEYBOARD);

            KeyEvent vUp = new KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_V, 0, metaState);
            vUp.setSource(InputDevice.SOURCE_KEYBOARD);

            KeyEvent ctrlUp = new KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0);
            ctrlUp.setSource(InputDevice.SOURCE_KEYBOARD);

            mInputManager.injectInputEvent(ctrlDown, mode);
            mInputManager.injectInputEvent(vDown, mode);
            mInputManager.injectInputEvent(vUp, mode);
            mInputManager.injectInputEvent(ctrlUp, mode);
        }
    }
}
