package com.huanghy7588.xiaqiaoqiaogongjvxiang.tiemo;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

import java.io.File;
import java.util.List;

/**
 * 贴膜水印机首页：以"一排一排的文件夹"展示已保存的成果。
 * - 右下角 + 进入编辑主页。
 * - 点击文件夹 → 查看其中的图片。
 * - 长按文件夹 → 重命名 / 删除。
 */
public class TiemoFolderActivity extends AppCompatActivity {

    private GridView gridFolders;
    private TextView tvEmpty;
    private List<TiemoUtils.FolderInfo> folders;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tiemo_folders);

        // 统一返回栏
        findViewById(R.id.btn_back_home).setOnClickListener(v -> finish());
        TextView tvTitle = findViewById(R.id.tv_top_title);
        tvTitle.setVisibility(View.VISIBLE);
        tvTitle.setText(R.string.tiemo_title);

        gridFolders = findViewById(R.id.grid_folders);
        tvEmpty = findViewById(R.id.tv_folder_empty);

        // 右下角 + 进入编辑主页
        findViewById(R.id.fab_add).setOnClickListener(v ->
                startActivity(new Intent(this, TiemoEditorActivity.class)));

        gridFolders.setOnItemClickListener((parent, view, position, id) -> {
            TiemoUtils.FolderInfo info = folders.get(position);
            Intent intent = new Intent(this, TiemoGalleryActivity.class);
            intent.putExtra(TiemoGalleryActivity.EXTRA_FOLDER, info.name);
            startActivity(intent);
        });

        gridFolders.setOnItemLongClickListener((parent, view, position, id) -> {
            showFolderActions(folders.get(position));
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** 刷新文件夹列表 */
    private void refresh() {
        folders = TiemoUtils.listFolders(this);
        if (folders.isEmpty()) {
            gridFolders.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        gridFolders.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        gridFolders.setAdapter(new FolderAdapter());
    }

    /** 文件夹长按：重命名 / 删除 */
    private void showFolderActions(TiemoUtils.FolderInfo info) {
        String[] items = {getString(R.string.tiemo_rename), getString(R.string.tiemo_delete)};
        new AlertDialog.Builder(this)
                .setTitle(info.name)
                .setItems(items, (d, which) -> {
                    if (which == 0) showRenameDialog(info);
                    else showDeleteConfirm(info);
                })
                .show();
    }

    private void showRenameDialog(TiemoUtils.FolderInfo info) {
        EditText et = new EditText(this);
        et.setText(info.name);
        et.setSelection(info.name.length());
        new AlertDialog.Builder(this)
                .setTitle(R.string.tiemo_rename)
                .setView(et)
                .setPositiveButton(R.string.confirm, (d, w) -> {
                    String newName = et.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(this, R.string.tiemo_folder_name_hint, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newName.equals(info.name)) return;
                    if (TiemoUtils.getFolderDir(this, newName).exists()) {
                        Toast.makeText(this, R.string.tiemo_folder_exists, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TiemoUtils.renameFolder(this, info.name, newName)) {
                        refresh();
                    } else {
                        Toast.makeText(this, R.string.update_fail_browser, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDeleteConfirm(TiemoUtils.FolderInfo info) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.tiemo_delete)
                .setMessage(getString(R.string.tiemo_delete_confirm, info.name))
                .setPositiveButton(R.string.tiemo_delete, (d, w) -> {
                    TiemoUtils.deleteFolder(this, info.name);
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 文件夹卡片适配器 */
    private class FolderAdapter extends ArrayAdapter<TiemoUtils.FolderInfo> {
        FolderAdapter() {
            super(TiemoFolderActivity.this, 0, folders);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_tiemo_folder, parent, false);
            }
            TiemoUtils.FolderInfo info = getItem(position);
            TextView name = convertView.findViewById(R.id.tv_folder_name);
            TextView count = convertView.findViewById(R.id.tv_folder_count);
            ImageView thumb = convertView.findViewById(R.id.iv_folder_thumb);
            if (info != null) {
                name.setText(info.name);
                count.setText(getString(R.string.tiemo_images_count, info.count));
                if (info.firstImage != null) {
                    thumb.setImageBitmap(TiemoUtils.decodeThumb(info.firstImage, 220));
                } else {
                    thumb.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            }
            return convertView;
        }
    }
}
