package com.zunipe.server;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputMonitor;
import android.view.MotionEvent;
import android.zunipe.IZunipeGestureCallback;
import android.zunipe.IZunipeInputManager;
import android.zunipe.ZunipeInputManager;

import com.android.server.SystemService;

public class ZunipeInputManagerService extends IZunipeInputManager.Stub {
    public static final String TAG = "ZunipeInputManagerService";
    private static final float SWIPE_THRESHOLD = 500F;

    private final Context mContext;

    private boolean mIsThreeFingerTracking = false;
    private float mInitialY1 = 0;
    private float mInitialY2 = 0;
    private float mInitialY3 = 0;
    private final InputManager mInputManager;
    private ZunipeInputEventReceiver mInputEventReceiver;
    private final RemoteCallbackList<IZunipeGestureCallback> mCallbacks;

    public ZunipeInputManagerService(Context context) {
        mContext = context;
        mInputManager = mContext.getSystemService(InputManager.class);
        mCallbacks = new RemoteCallbackList<IZunipeGestureCallback>();
    }

    public static class Lifecycle extends SystemService {
        private ZunipeInputManagerService mService;

        public Lifecycle(Context context) {
            super(context);
        }

        @Override
        public void onStart() {
            mService = new ZunipeInputManagerService(getContext());
            publishBinderService(Context.ZUNIPE_INPUT_SERVICE, mService);
        }

        @Override
        public void onBootPhase(int phase) {
            if (phase == PHASE_BOOT_COMPLETED) {
                mService.onBootCompleted();
            }
        }
    }

    private void onBootCompleted() {
        HandlerThread mInputThread = new HandlerThread("SysInputMonitorThread");
        mInputThread.start();

        InputMonitor inputMonitor = mInputManager.monitorGestureInput(
                "SysGlobalMonitor",
                android.view.Display.DEFAULT_DISPLAY
        );

        if (inputMonitor != null) {
            InputChannel inputChannel = inputMonitor.getInputChannel();

            mInputEventReceiver = new ZunipeInputEventReceiver(inputChannel, mInputThread.getLooper());
        }
    }

    @Override
    public void registerCallback(IZunipeGestureCallback callback) {
        mCallbacks.register(callback);
    }

    @Override
    public void unregisterCallback(IZunipeGestureCallback callback) {
        mCallbacks.unregister(callback);
    }

    private void handleMotionEvent(MotionEvent event) {
        int pointerCount = event.getPointerCount();
        switch (event.getAction()) {
            case (2 << 8 | MotionEvent.ACTION_POINTER_DOWN) -> {
                if (pointerCount == 3) {
                    mInitialY1 = event.getY(0);
                    mInitialY2 = event.getY(1);
                    mInitialY3 = event.getY(2);
                    mIsThreeFingerTracking = true;
                }
            }
            case MotionEvent.ACTION_MOVE -> {
                if (mIsThreeFingerTracking && pointerCount == 3) {
                    float currentY1 = event.getY(0);
                    float currentY2 = event.getY(1);
                    float currentY3 = event.getY(2);

                    float deltaY1 = currentY1 - mInitialY1;
                    float deltaY2 = currentY2 - mInitialY2;
                    float deltaY3 = currentY3 - mInitialY3;
                    float difference = Math.max(Math.abs(deltaY1 - deltaY2), Math.max(Math.abs(deltaY1 - deltaY3), Math.abs(deltaY2 - deltaY3)));

                    if (difference > 200) {
                        mIsThreeFingerTracking = false;
                        break;
                    }

                    if (deltaY1 > SWIPE_THRESHOLD && deltaY2 > SWIPE_THRESHOLD && deltaY3 > SWIPE_THRESHOLD) {
                        dispatchThreeFinger();
                        mIsThreeFingerTracking = false;
                    }
                }
            }
            case MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP,
                 MotionEvent.ACTION_CANCEL -> mIsThreeFingerTracking = false;
        }
    }

    private void dispatchThreeFinger() {
        final int n = mCallbacks.beginBroadcast();
        Log.d(TAG, "dispatchThreeFinger n = " + n);
        try {
            for (int i = 0; i < n; i++) {
                try {
                    IZunipeGestureCallback cb = mCallbacks.getBroadcastItem(i);
                    cb.onGestureTrigger(ZunipeInputManager.TYPE_GESTURE_THREE_FINGER_SLIDE);
                } catch (RemoteException ignored) {

                }
            }
        } finally {
            mCallbacks.finishBroadcast();
        }
    }

    private class ZunipeInputEventReceiver extends InputEventReceiver {
        public ZunipeInputEventReceiver(InputChannel inputChannel, Looper looper) {
            super(inputChannel, looper);
        }

        @Override
        public void onInputEvent(InputEvent event) {
            boolean handled = false;
            try {
                if (event instanceof MotionEvent e) {
                    handleMotionEvent(e);
                }
            } finally {
                finishInputEvent(event, handled);
            }
        }
    }
}
