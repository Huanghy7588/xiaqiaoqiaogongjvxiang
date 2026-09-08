package com.huanghy7588.xiaqiaoqiaogongjvxiang.tiemo;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 贴膜水印机存储与工具类。
 * 文件夹保存在应用私有外部存储：<外部存储>/Android/data/<pkg>/files/tiemo/folders/<文件夹名>/
 * 该路径无需任何存储权限，且随应用卸载清除，适合存放"贴膜水印机"的工作成果。
 */
public class TiemoUtils {

    /** 相册保存目录名（Pictures 下） */
    private static final String ALBUM_DIR = "Pictures/夏乔乔工具箱";

    /** 应用私有根目录下的贴膜水印机文件夹根 */
    public static File getFoldersRoot(Context ctx) {
        File root = new File(ctx.getExternalFilesDir(null), "tiemo/folders");
        if (!root.exists()) root.mkdirs();
        return root;
    }

    /** 文件夹名 → 安全的目录名（去掉非法字符） */
    private static String safeName(String name) {
        String s = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return s.isEmpty() ? "未命名" : s;
    }

    public static File getFolderDir(Context ctx, String folderName) {
        return new File(getFoldersRoot(ctx), safeName(folderName));
    }

    /** 文件夹信息 */
    public static class FolderInfo {
        public String name;
        public File dir;
        public int count;
        public File firstImage;

        public FolderInfo(String name, File dir, int count, File firstImage) {
            this.name = name;
            this.dir = dir;
            this.count = count;
            this.firstImage = firstImage;
        }
    }

    /** 列出所有文件夹（按名称排序） */
    public static List<FolderInfo> listFolders(Context ctx) {
        List<FolderInfo> list = new ArrayList<>();
        File root = getFoldersRoot(ctx);
        File[] dirs = root.listFiles(f -> f.isDirectory());
        if (dirs == null) return list;
        for (File d : dirs) {
            File[] imgs = d.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
            int count = (imgs == null) ? 0 : imgs.length;
            File first = (imgs != null && imgs.length > 0) ? imgs[0] : null;
            list.add(new FolderInfo(d.getName(), d, count, first));
        }
        list.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return list;
    }

    /** 重命名文件夹 */
    public static boolean renameFolder(Context ctx, String oldName, String newName) {
        File oldDir = getFolderDir(ctx, oldName);
        File newDir = getFolderDir(ctx, newName);
        if (!oldDir.exists()) return false;
        if (newDir.exists()) return false; // 目标已存在，避免覆盖
        return oldDir.renameTo(newDir);
    }

    /** 删除文件夹及其所有内容 */
    public static boolean deleteFolder(Context ctx, String name) {
        File dir = getFolderDir(ctx, name);
        return deleteRecursively(dir);
    }

    private static boolean deleteRecursively(File f) {
        if (f == null) return false;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursively(c);
        }
        return f.delete();
    }

    /** 拷贝单个文件 */
    public static boolean copyFile(File src, File dst) {
        if (src == null || !src.exists()) return false;
        try {
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** 保存一个 Bitmap 到相册（始终 PNG 保留透明），返回是否成功 */
    public static boolean saveBitmapToGallery(Context ctx, Bitmap bmp) {
        ContentValues values = new ContentValues();
        String name = "tiemo_" + System.currentTimeMillis() + ".png";
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, ALBUM_DIR);
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }
        ContentResolver resolver = ctx.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return false;
        OutputStream os = null;
        try {
            os = resolver.openOutputStream(uri);
            if (os == null) return false;
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.close();
            os = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (os != null) try { os.close(); } catch (IOException ignored) {}
        }
    }

    /** 把已生成的 PNG 文件保存到相册（直接搬运字节，零质量损失） */
    public static boolean saveFileToGallery(Context ctx, File pngFile) {
        ContentValues values = new ContentValues();
        String name = "tiemo_" + System.currentTimeMillis() + ".png";
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, ALBUM_DIR);
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }
        ContentResolver resolver = ctx.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return false;
        OutputStream os = null;
        try {
            os = resolver.openOutputStream(uri);
            if (os == null) return false;
            try (InputStream in = new FileInputStream(pngFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) os.write(buf, 0, len);
            }
            os.close();
            os = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (os != null) try { os.close(); } catch (IOException ignored) {}
        }
    }

    /** 生成结果临时目录（cache 下，可随时清理） */
    public static File getCacheGenDir(Context ctx) {
        File dir = new File(ctx.getCacheDir(), "tiemo_gen");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** 解码缩略图（用于文件夹/结果展示），降采样到 maxSize 以内 */
    public static Bitmap decodeThumb(File file, int maxSize) {
        if (file == null || !file.exists()) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        int s = 1;
        while (opts.outWidth / s > maxSize || opts.outHeight / s > maxSize) s *= 2;
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = s;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    }

    /** 解码 Uri 图片并降采样到 reqWidth 以内（防止大图 OOM） */
    public static Bitmap decodeUri(Context ctx, Uri uri, int reqWidth) {
        InputStream is = null;
        try {
            is = ctx.getContentResolver().openInputStream(uri);
            if (is == null) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opts);
            is.close();
            is = null;
            int sample = 1;
            long total = (long) opts.outWidth * opts.outHeight;
            while (opts.outWidth / sample > reqWidth || total / (sample * sample) > 24_000_000) {
                sample *= 2;
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            is = ctx.getContentResolver().openInputStream(uri);
            if (is == null) return null;
            return BitmapFactory.decodeStream(is, null, opts);
        } catch (IOException | OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        } finally {
            if (is != null) try { is.close(); } catch (IOException ignored) {}
        }
    }
}
