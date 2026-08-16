package com.huanghy7588.xiaqiaoqiaogongjvxiang.wuzhong;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片选项选择器：从 assets 指定文件夹加载图片到网格，点击选中返回 asset 路径。
 */
public class ImagePickerDialog extends Dialog {

    public interface PickerCallback {
        void onPicked(String assetPath, String fileName);
    }

    private final Context ctx;
    private final String folder;
    private final String[] filterPrefixes; // 仅保留文件名以这些前缀开头的图片（用于巅峰角标新版/旧版）
    private final PickerCallback cb;

    public ImagePickerDialog(Context context, String folder, String title,
                             String[] filterPrefixes, PickerCallback callback) {
        super(context);
        this.ctx = context;
        this.folder = folder;
        this.filterPrefixes = filterPrefixes;
        this.cb = callback;
        setContentView(R.layout.dialog_image_picker);
        TextView tvTitle = findViewById(R.id.tv_picker_title);
        tvTitle.setText(title);
        findViewById(R.id.btn_picker_cancel).setOnClickListener(v -> dismiss());

        List<String> names = listAssets();
        GridView grid = findViewById(R.id.grid_images);
        grid.setAdapter(new ImgAdapter(names));
    }

    private List<String> listAssets() {
        List<String> out = new ArrayList<>();
        try {
            String[] files = ctx.getAssets().list(folder);
            if (files != null) {
                for (String f : files) {
                    if (f.toLowerCase().endsWith(".png") || f.toLowerCase().endsWith(".jpg")
                            || f.toLowerCase().endsWith(".jpeg")) {
                        if (filterPrefixes == null || matchPrefix(f)) out.add(f);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out;
    }

    private boolean matchPrefix(String name) {
        for (String p : filterPrefixes) {
            if (name.startsWith(p)) return true;
        }
        return false;
    }

    private Bitmap thumb(String name) {
        try {
            InputStream is = ctx.getAssets().open(folder + "/" + name);
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = 4;
            Bitmap b = BitmapFactory.decodeStream(is, null, opt);
            is.close();
            return b;
        } catch (IOException e) {
            return null;
        }
    }

    private class ImgAdapter extends BaseAdapter {
        private final List<String> names;
        ImgAdapter(List<String> n) { names = n; }

        @Override public int getCount() { return names.size(); }
        @Override public Object getItem(int i) { return names.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            ImageView iv;
            if (convertView instanceof ImageView) iv = (ImageView) convertView;
            else iv = new ImageView(ctx);
            final String name = names.get(i);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(6, 6, 6, 6);
            iv.setAdjustViewBounds(true);
            iv.setImageBitmap(thumb(name));
            iv.setOnClickListener(v -> {
                cb.onPicked(folder + "/" + name, name);
                dismiss();
            });
            return iv;
        }
    }
}
