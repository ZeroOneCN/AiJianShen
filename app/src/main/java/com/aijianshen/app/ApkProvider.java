package com.aijianshen.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 把下载好的更新包以 content:// 的形式交给系统安装器。
 *
 * <p>Android 7 起 file:// 不能再跨进程读，装 APK 必须走内容提供者。项目没有引 AndroidX
 * （android.useAndroidX=false），拿不到 FileProvider，所以这里自己实现一个最小的：
 * 只读、只认缓存目录下那一个 update.apk，其它路径一律拒绝。
 */
public class ApkProvider extends ContentProvider {

    /** 唯一允许读出的文件名，同时也是 content:// 的最后一段。 */
    private static final String NAME = "update.apk";

    public static Uri uriFor(Context c) {
        return new Uri.Builder()
                .scheme("content")
                .authority(c.getPackageName() + ".apk")
                .appendPath(NAME)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        // 不看 mode：无论调用方要什么，这里都只给只读句柄
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (uri == null || uri.getPathSegments() == null
                || uri.getPathSegments().size() != 1
                || !NAME.equals(uri.getPathSegments().get(0))) {
            throw new FileNotFoundException("不支持的路径：" + uri);
        }
        File f = new File(UpdateChecker.updateDir(getContext()), NAME);
        if (!f.isFile()) {
            throw new FileNotFoundException("更新包不存在");
        }
        return f;
    }

    // ---- 不提供增删改查，安装器只用到 openFile ----

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
