package com.zunipe.server;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.Environment;
import android.os.UserHandle;
import android.util.AtomicFile;
import android.util.Slog;
import android.zunipe.IZunipePackageManager;

import com.android.server.SystemService;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

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
        private final AtomicFile mAtomicFile;
        private static final File CONFIG_FILE = new File(Environment.getDataSystemDirectory(), "hide_app_config.xml");


        public Lifecycle(Context context) {
            super(context);
            mAtomicFile = new AtomicFile(CONFIG_FILE);
        }

        @Override
        public void onUserStarting(TargetUser user) {
            HashMap<String, List<String>> resultData = mService.mCurrentHideAppMap;
            int userId = user.getUserIdentifier();
            synchronized (mFileLock) {
                File file = mAtomicFile.getBaseFile();
                if (!file.exists() || file.length() == 0) {
                    return;
                }

                try (FileInputStream fis = mAtomicFile.openRead()) {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(fis);
                    Element root = doc.getDocumentElement();

                    // 找到指定 user id 的节点
                    Element userElement = findUserElement(root, userId);
                    if (userElement == null) {
                        return;
                    }

                    // 解析该用户下的 applications
                    NodeList appNodes = userElement.getElementsByTagName("application");
                    for (int i = 0; i < appNodes.getLength(); i++) {
                        Node appNode = appNodes.item(i);
                        if (appNode.getNodeType() == Node.ELEMENT_NODE) {
                            Element appEle = (Element) appNode;
                            String pkgName = appEle.getAttribute("package");

                            List<String> values = new ArrayList<>();
                            NodeList itemNodes = appEle.getElementsByTagName("item");
                            for (int j = 0; j < itemNodes.getLength(); j++) {
                                Node itemNode = itemNodes.item(j);
                                if (itemNode.getNodeType() == Node.ELEMENT_NODE) {
                                    values.add(((Element) itemNode).getAttribute("value"));
                                }
                            }
                            resultData.put(pkgName, values);
                        }
                    }
                } catch (Exception e) {
                    Slog.e(TAG, "Failed to read user data for user: " + userId, e);
                }
            }
        }

        @Override
        public void onUserStopping(TargetUser user) {
            int userId = user.getUserIdentifier();
            HashMap<String, List<String>> currentData = new HashMap<>(mService.mCurrentHideAppMap);
            synchronized (mFileLock) {
                FileOutputStream fos = null;
                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc;

                    File file = mAtomicFile.getBaseFile();
                    if (file.exists() && file.length() > 0) {
                        // 1. 如果文件存在，直接解析为 DOM 树
                        try (FileInputStream fis = mAtomicFile.openRead()) {
                            doc = builder.parse(fis);
                        }
                    } else {
                        // 2. 如果文件不存在，初始化全新的 DOM 树
                        doc = builder.newDocument();
                        Element root = doc.createElement("hide-application");
                        root.setAttribute("version", "1");
                        doc.appendChild(root);
                    }

                    Element root = doc.getDocumentElement();

                    // 3. 查找是否已存在该用户的 <user id="X"> 节点
                    Element userElement = findUserElement(root, userId);
                    if (userElement != null) {
                        // 如果存在旧数据，直接把旧的 <user> 节点删掉
                        root.removeChild(userElement);
                    }

                    // 4. 创建新的 <user id="X"> 节点并挂载你的 HashMap
                    Element newUserElement = doc.createElement("user");
                    newUserElement.setAttribute("id", String.valueOf(userId));

                    for (Map.Entry<String, List<String>> entry : currentData.entrySet()) {
                        String pkgName = entry.getKey();
                        List<String> values = entry.getValue();
                        if (pkgName == null || values == null) continue;

                        // 对应 <application package="xxxx">
                        Element appElement = doc.createElement("application");
                        appElement.setAttribute("package", pkgName);

                        for (String val : values) {
                            if (val == null) continue;
                            Element itemElement = doc.createElement("item");
                            itemElement.setAttribute("value", val);
                            appElement.appendChild(itemElement);
                        }
                        newUserElement.appendChild(appElement);
                    }

                    // 将新组装的当前用户节点放回根节点
                    root.appendChild(newUserElement);

                    // 5. 通过 AtomicFile 安全地写回磁盘
                    fos = mAtomicFile.startWrite();
                    TransformerFactory transformerFactory = TransformerFactory.newInstance();
                    Transformer transformer = transformerFactory.newTransformer();

                    // 规范输出格式（换行和缩进）
                    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

                    DOMSource source = new DOMSource(doc);
                    StreamResult result = new StreamResult(fos);
                    transformer.transform(source, result);

                    mAtomicFile.finishWrite(fos);
                    mService.mCurrentHideAppMap.clear();
                    Slog.i(TAG, "Successfully updated hide-application config for user: " + userId);
                } catch (Exception e) {
                    Slog.e(TAG, "Failed to write user data for user: " + userId, e);
                    if (fos != null) {
                        mAtomicFile.failWrite(fos);
                    }
                }
            }
        }

        @Override
        public void onStart() {
            mService = new ZunipePackageManagerService(getContext());
            publishBinderService(Context.ZUNIPE_PACKAGE_SERVICE, mService);
        }

        private Element findUserElement(Element root, int userId) {
            NodeList userNodes = root.getElementsByTagName("user");
            String targetIdStr = String.valueOf(userId);
            for (int i = 0; i < userNodes.getLength(); i++) {
                Node node = userNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element userEle = (Element) node;
                    if (targetIdStr.equals(userEle.getAttribute("id"))) {
                        return userEle;
                    }
                }
            }
            return null;
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
                    android.util.Log.d("hjyy", "ttt[0] = " + ttt[0] + " ttt[1] = " + ttt[1]);
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

            mCurrentHideAppMap.put(packageName, launcherComponents);
        }
    }

    @Override
    public void revealApplication(String packageName) {
        synchronized (mLock) {
            mCurrentHideAppMap.remove(packageName);
        }
    }

    @Override
    public List<String> getHideApplicationList() {
        return new ArrayList<>(mCurrentHideAppMap.keySet());
    }

    private List<String> getLauncherComponentsForPackage(String packageName, int userId) {
        List<String> componentNames = new ArrayList<>();

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mainIntent.setPackage(packageName);

        try {
            List<ResolveInfo> apps = android.app.AppGlobals.getPackageManager().queryIntentActivities(
                    mainIntent,
                    mainIntent.resolveTypeIfNeeded(mContext.getContentResolver()),
                    PackageManager.MATCH_DIRECT_BOOT_AWARE | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                    userId
            ).getList();

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
