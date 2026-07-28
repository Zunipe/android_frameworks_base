package com.zunipe.server;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.Environment;
import android.os.UserHandle;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Slog;
import android.zunipe.IZunipePackageManager;
import com.android.server.SystemService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ZunipePackageManagerService extends IZunipePackageManager.Stub {
    public static final String TAG = "ZunipePackageManagerService";
    private final Context mContext;
    private final HashMap<String, List<String>> mCurrentHideAppMap = new HashMap<>();
    private final Object mLock = new Object();
    private static ZunipePackageManagerService sInstance;

    public ZunipePackageManagerService(Context context) {
        mContext = context;
        sInstance = this;
    }

    public static ZunipePackageManagerService getInstance() {
        return sInstance;
    }

    public static class Lifecycle extends SystemService {
        private ZunipePackageManagerService mService;
        private final Object mFileLock = new Object();
        private static final String CONFIG_NAME = "hide_app_config.xml";
        private final Context mContext;

        public Lifecycle(Context context) {
            super(context);
            mContext = context;
        }

        @Override
        public void onUserSwitching(TargetUser from, TargetUser to) {
            if (from != null) {
                saveConfig(from.getUserIdentifier());
            }
            loadConfig(to.getUserIdentifier());
        }

        @Override
        public void onStart() {
            mService = new ZunipePackageManagerService(getContext());
            publishBinderService(Context.ZUNIPE_PACKAGE_SERVICE, mService);

            loadConfig(0);
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SHUTDOWN);
            filter.addAction(Intent.ACTION_REBOOT);

            mContext.registerReceiverAsUser(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    Slog.i(TAG, "Received shutdown intent: " + action + ", preparing to save data...");
                    int currentUserId = ActivityManager.getCurrentUser();
                    saveConfig(currentUserId);
                }
            }, UserHandle.ALL, filter, null, null);
        }

        private void saveConfig(int userId) {
            synchronized (mFileLock) {
                File userSystemDir = Environment.getUserSystemDirectory(userId);
                File config = new File(userSystemDir, CONFIG_NAME);
                if (!mService.mCurrentHideAppMap.isEmpty()) {
                    FileOutputStream fos;
                    try {
                        if (!config.exists()) {
                            if (!config.createNewFile()) {
                                return;
                            }
                        }
                        Log.d(TAG, "onUserSwitching saveConfig userId = " + userId);
                        AtomicFile atomicFile = new AtomicFile(config);
                        fos = atomicFile.startWrite();
                        JSONObject jsonObject = new JSONObject();
                        for (Map.Entry<String, List<String>> entry : mService.mCurrentHideAppMap.entrySet()) {
                            JSONArray jsonArray = new JSONArray();
                            if (entry.getValue() != null) {
                                for (String value : entry.getValue()) {
                                    jsonArray.put(value);
                                }
                            }
                            jsonObject.put(entry.getKey(), jsonArray);
                        }
                        byte[] data = jsonObject.toString().getBytes(StandardCharsets.UTF_8);
                        fos.write(data, 0, data.length);
                        atomicFile.finishWrite(fos);
                    } catch (Exception e) {
                        Slog.e(TAG, "Failed to parse Map to JSON string", e);
                    }
                }
            }
        }

        private void loadConfig(int userId) {
            synchronized (mFileLock) {
                File userSystemDir = Environment.getUserSystemDirectory(userId);
                File config = new File(userSystemDir, CONFIG_NAME);
                mService.mCurrentHideAppMap.clear();
                if (config.exists()) {
                    AtomicFile atomicFile = new AtomicFile(config);
                    try (FileInputStream fis = atomicFile.openRead()) {
                        Log.d(TAG, "onUserSwitching loadConfig userId = " + userId);
                        byte[] buffer = new byte[fis.available()];
                        int bytesRead = fis.read(buffer);
                        if (bytesRead > 0) {
                            String jsonString = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                            JSONObject jsonObject = new JSONObject(jsonString);
                            Iterator<String> keys = jsonObject.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                JSONArray jsonArray = jsonObject.getJSONArray(key);
                                List<String> valueList = new ArrayList<>();
                                for (int i = 0; i < jsonArray.length(); i++) {
                                    valueList.add(jsonArray.getString(i));
                                }
                                mService.mCurrentHideAppMap.put(key, valueList);
                            }
                        }
                    } catch (Exception e) {
                        Slog.e(TAG, "Failed to parse JSON string to Map", e);
                    }
                }
            }
        }
    }

    @Override
    public void startHideActivity(String packageName) {
        final long identity = Binder.clearCallingIdentity();
        try {
            if (mCurrentHideAppMap.containsKey(packageName)) {
                List<String> list = mCurrentHideAppMap.get(packageName);
                if (!list.isEmpty()) {
                    Intent intent = new Intent();
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    String[] ttt = list.getFirst().split("/");
                    if (ttt.length == 2) {
                        ComponentName name = new ComponentName(ttt[0], ttt[1]);
                        intent.setComponent(name);
                        mContext.startActivity(intent);
                    }
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void hideApplication(String packageName) {
        synchronized (mLock) {
            int callingUid = Binder.getCallingUid();
            int userId = UserHandle.getUserId(callingUid);

            if (mCurrentHideAppMap.containsKey(packageName)) {
                return;
            }

            List<String> launcherComponents = getLauncherComponentsForPackage(packageName, userId);
            if (launcherComponents.isEmpty()) {
                return;
            }
            sendHideAppBroadcast();
            mCurrentHideAppMap.put(packageName, launcherComponents);
        }
    }

    private void sendHideAppBroadcast() {
        Intent intent = new Intent("HIDE_APP_NAME");
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        try {
            if (mContext != null){
                mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
            }
        } catch (Exception e) {
        }
    }

    @Override
    public void revealApplication(String packageName) {
        synchronized (mLock) {
            mCurrentHideAppMap.remove(packageName);
            sendHideAppBroadcast();
        }
    }

    @Override
    public List<String> getHideApplicationList() {
        return new ArrayList<>(mCurrentHideAppMap.keySet());
    }

    @Override
    public ResolveInfo getResolveInfo(String packageName) {
        final long identity = Binder.clearCallingIdentity();
        try {
            if (!mCurrentHideAppMap.containsKey(packageName)) {
                return null;
            }
            int callingUid = Binder.getCallingUid();
            int userId = UserHandle.getUserId(callingUid);
            List<ResolveInfo> resolveInfos = getLauncherResolveInfoForPackage(packageName, userId);
            if (!resolveInfos.isEmpty()) {
                return resolveInfos.getFirst();
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return null;
    }

    private List<ResolveInfo> getLauncherResolveInfoForPackage(String packageName, int userId) {
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mainIntent.setPackage(packageName);

        try {
            return android.app.AppGlobals.getPackageManager().queryIntentActivities(
                    mainIntent,
                    mainIntent.resolveTypeIfNeeded(mContext.getContentResolver()),
                    PackageManager.MATCH_DIRECT_BOOT_AWARE | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                    userId
            ).getList();
        } catch (Exception e) {
            Slog.e(TAG, "queryIntentActivities 发生异常，可能包名不存在", e);
        }
        return new ArrayList<>();
    }

    private List<String> getLauncherComponentsForPackage(String packageName, int userId) {
        List<String> componentNames = new ArrayList<>();
        try {
            List<ResolveInfo> apps = getLauncherResolveInfoForPackage(packageName, userId);

            if (apps != null && !apps.isEmpty()) {
                for (ResolveInfo info : apps) {
                    if (info.activityInfo != null) {
                        String compName = info.activityInfo.packageName + "/" + info.activityInfo.name;
                        componentNames.add(compName);
                    }
                }
            }
        } catch (Exception e) {
            Slog.e(TAG, "queryIntentActivities 发生异常，可能包名不存在", e);
        }
        return componentNames;
    }
}
