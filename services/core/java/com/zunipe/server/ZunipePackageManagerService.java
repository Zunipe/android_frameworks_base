package com.zunipe.server;

import android.content.ComponentName;
import android.content.Context;
import android.zunipe.IZunipePackageManager;

import com.android.server.SystemService;

import java.util.List;

public class ZunipePackageManagerService extends IZunipePackageManager.Stub {
    public static final String TAG = "ZunipePackageManagerService";

    private final Context mContext;

    public ZunipePackageManagerService(Context context) {
        mContext = context;
    }

    public static class Lifecycle extends SystemService {
        private ZunipePackageManagerService mService;

        public Lifecycle(Context context) {
            super(context);
        }

        @Override
        public void onStart() {
            mService = new ZunipePackageManagerService(getContext());
            publishBinderService(Context.ZUNIPE_PACKAGE_SERVICE, mService);
        }

        @Override
        public void onBootPhase(int phase) {
            if (phase == PHASE_BOOT_COMPLETED) {
                mService.onBootCompleted();
            }
        }
    }

    @Override
    public void startHideActivity(ComponentName name) {

    }

    @Override
    public void hideApplication(String packageName) {

    }

    @Override
    public void revealApplication(String packageName) {

    }

    @Override
    public List<String> getHideApplicationList() {
        return List.of("test");
    }

    private void onBootCompleted() {

    }
}
