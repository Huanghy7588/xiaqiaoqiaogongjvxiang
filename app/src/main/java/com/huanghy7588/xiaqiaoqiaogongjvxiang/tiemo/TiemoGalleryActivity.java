package com.huanghy7588.xiaqiaoqiaogongjvxiang.tiemo;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 贴膜水印机：查看某个文件夹内保存的图片，点击可放大查看。
 */
public class TiemoGalleryActivity extends AppCompatActivity {

    public static final String EXTRA_FOLDER = "extra_folder";

    private String folderName;
    private List<File> images = new ArrayList<>();
    private Bitmap fullBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tiemo_gallery);

        folderName = getIntent().getStringExtra(EXTRA_FOLDER);
        if (folderName == null) folderName = "";

        // 统一返回栏
        findViewById(R.id.btn_back_home).setOnClickListener(v -> finish());
        TextView tvTitle = findViewById(R.id.tv_top_title);
        tvTitle.setVisibility(View.VISIBLE);
        tvTitle.setText(folderName);

        File dir = TiemoUtils.getFolderDir(this, folderName);
        if (dir.exists()) {
            File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
            if (files != null) {
                for (File f : files) images.add(f);
            }
            images.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        }

        GridView grid = findViewById(R.id.grid_gallery);
        if (images.isEmpty()) {
            Toast.makeText(this, R.string.tiemo_folder_empty, Toast.LENGTH_SHORT).show();
        }
        grid.setAdapter(new GalleryAdapter());
        grid.setOnItemClickListener((parent, view, position, id) -> showFull(images.get(position)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fullBitmap != null && !fullBitmap.isRecycled()) {
            fullBitmap.recycle();
            fullBitmap = null;
        }
    }

    /** 放大查看单张图片 */
    private void showFull(File file) {
        if (fullBitmap != null && !fullBitmap.isRecycled()) fullBitmap.recycle();
        fullBitmap = TiemoUtils.decodeThumb(file, 1600);
        if (fullBitmap == null) return;
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(fullBitmap);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackgroundColor(0xFF000000);
        new AlertDialog.Builder(this)
                .setView(iv)
                .setPositiveButton(R.string.confirm, null)
                .show();
    }

    private class GalleryAdapter extends ArrayAdapter<File> {
        GalleryAdapter() {
            super(TiemoGalleryActivity.this, 0, images);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_tiemo_gallery, parent, false);
            }
            ImageView iv = convertView.findViewById(R.id.iv_gallery_item);
            Bitmap thumb = TiemoUtils.decodeThumb(getItem(position), 220);
            iv.setImageBitmap(thumb);
            return convertView;
        }
    }
}
