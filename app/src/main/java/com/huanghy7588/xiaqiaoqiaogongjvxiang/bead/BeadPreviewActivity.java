package com.huanghy7588.xiaqiaoqiaogongjvxiang.bead;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 拼豆图纸生成 · 第四步：预览（生成 + 网格查看）与导出（PNG / PDF）。
 */
public class BeadPreviewActivity extends AppCompatActivity {

    private BeadChartView chartView;
    private TextView tvSize, tvColors, tvTotal;
    private LinearLayout progressOverlay;
    private ProgressBar progressBar;
    private TextView tvProgress;

    private BeadConfig config;
    private Bitmap source;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bead_preview);

        findViewById(R.id.btn_back_home).setOnClickListener(v -> finish());
        TextView tvTitle = findViewById(R.id.tv_top_title);
        tvTitle.setText(R.string.bead_preview_title);
        tvTitle.setVisibility(View.VISIBLE);

        config = BeadData.config;
        source = BeadData.source;
        if (config == null || source == null) {
            Toast.makeText(this, R.string.bead_need_image, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        chartView = findViewById(R.id.chart_view);
        tvSize = findViewById(R.id.tv_info_size);
        tvColors = findViewById(R.id.tv_info_colors);
        tvTotal = findViewById(R.id.tv_info_total);
        progressOverlay = findViewById(R.id.progress_overlay);
        progressBar = findViewById(R.id.progress_bar);
        progressBar.setMax(100);
        tvProgress = findViewById(R.id.tv_progress);

        findViewById(R.id.btn_export_png).setOnClickListener(v -> showExportDialog(false));
        findViewById(R.id.btn_export_pdf).setOnClickListener(v -> showExportDialog(true));

        startGenerate();
    }

    private void startGenerate() {
        progressOverlay.setVisibility(View.VISIBLE);
        progressOverlay.bringToFront();
        tvProgress.setText("正在生成图纸…");
        progressBar.setIndeterminate(true);

        final Bitmap srcCopy = source;
        worker.execute(() -> {
            Palette palette = null;
            String err = null;
            try {
                palette = Palette.load(getApplicationContext(), config.paletteFile);
            } catch (Exception e) {
                err = getString(R.string.bead_palette_fail);
            }
            BeadColor[][] grid = null;
            if (palette != null) {
                try {
                    grid = BeadGenerator.generate(srcCopy, config, palette, (done, total) -> {
                        int p = total > 0 ? (int) (100f * done / total) : 0;
                        runOnUiThread(() -> {
                            progressBar.setIndeterminate(false);
                            progressBar.setProgress(p);
                            tvProgress.setText("正在匹配颜色 " + done + "/" + total);
                        });
                    });
                } catch (OutOfMemoryError oom) {
                    err = getString(R.string.bead_generate_fail);
                }
            }
            BeadColor[][] finalGrid = grid;
            String finalErr = err;
            runOnUiThread(() -> onGenerated(finalGrid, finalErr));
        });
    }

    private void onGenerated(BeadColor[][] grid, String err) {
        progressOverlay.setVisibility(View.GONE);
        if (grid == null) {
            Toast.makeText(this, err != null ? err : getString(R.string.bead_generate_fail),
                    Toast.LENGTH_LONG).show();
            return;
        }
        BeadData.grid = grid;
        BeadDraw.DrawOptions opt = new BeadDraw.DrawOptions();
        opt.showCodes = config.showCodes;
        opt.showGrid = config.showGrid;
        opt.boldEvery5 = config.boldEvery5;
        chartView.setGrid(grid, opt);

        int gw = grid[0].length;
        int gh = grid.length;
        int total = 0;
        for (BeadColor[] row : grid) {
            for (BeadColor c : row) {
                if (c != null) total++;
            }
        }
        List<BeadDraw.Usage> usage = BeadDraw.buildUsage(grid);
        tvSize.setText(gw + " × " + gh + " 颗");
        tvColors.setText(usage.size() + " 种");
        tvTotal.setText(total + " 颗");
    }

    private String brandTitle() {
        return config.brandName + " · " + config.colorCount + "色";
    }

    private void showExportDialog(final boolean isPdf) {
        if (BeadData.grid == null) {
            Toast.makeText(this, R.string.bead_generate_first, Toast.LENGTH_SHORT).show();
            return;
        }
        Dialog d = new Dialog(this);
        d.setContentView(R.layout.dialog_bead_export);

        TextView tvSizeInfo = d.findViewById(R.id.exp_size);
        TextView tvPaletteInfo = d.findViewById(R.id.exp_palette);
        TextView tvUsageInfo = d.findViewById(R.id.exp_usage);
        CheckBox cbCodes = d.findViewById(R.id.cb_codes);
        CheckBox cbGrid = d.findViewById(R.id.cb_grid);
        CheckBox cbBold = d.findViewById(R.id.cb_bold);
        CheckBox cbPaginate = d.findViewById(R.id.cb_paginate);
        LinearLayout paperRow = d.findViewById(R.id.exp_paper_row);
        Button btnPng = d.findViewById(R.id.exp_btn_png);
        Button btnPdf = d.findViewById(R.id.exp_btn_pdf);

        int gw = BeadData.grid[0].length;
        int gh = BeadData.grid.length;
        tvSizeInfo.setText("尺寸：" + gw + " × " + gh + " 颗");
        tvPaletteInfo.setText("色板：" + brandTitle());
        int total = 0;
        for (BeadColor[] row : BeadData.grid) {
            for (BeadColor c : row) if (c != null) total++;
        }
        tvUsageInfo.setText("用色：" + BeadDraw.buildUsage(BeadData.grid).size()
                + " 种 / " + total + " 颗");

        cbCodes.setChecked(config.showCodes);
        cbGrid.setChecked(config.showGrid);
        cbBold.setChecked(config.boldEvery5);
        cbPaginate.setChecked(config.paginate);
        cbPaginate.setVisibility(isPdf ? View.VISIBLE : View.GONE);
        paperRow.setVisibility(isPdf ? View.VISIBLE : View.GONE);

        btnPng.setVisibility(isPdf ? View.GONE : View.VISIBLE);
        btnPdf.setVisibility(isPdf ? View.VISIBLE : View.GONE);

        btnPng.setOnClickListener(v -> {
            config.showCodes = cbCodes.isChecked();
            config.showGrid = cbGrid.isChecked();
            config.boldEvery5 = cbBold.isChecked();
            config.save(this);
            d.dismiss();
            doExport(false);
        });
        btnPdf.setOnClickListener(v -> {
            config.showCodes = cbCodes.isChecked();
            config.showGrid = cbGrid.isChecked();
            config.boldEvery5 = cbBold.isChecked();
            config.paginate = cbPaginate.isChecked();
            config.save(this);
            d.dismiss();
            doExport(true);
        });

        d.show();
    }

    private void doExport(boolean isPdf) {
        final Dialog wait = new Dialog(this);
        wait.setContentView(R.layout.dialog_bead_export_wait);
        wait.setCancelable(false);
        wait.show();

        worker.execute(() -> {
            Uri uri;
            String fileName;
            long time = System.currentTimeMillis();
            if (isPdf) {
                byte[] data = BeadExporter.buildPdfBytes(BeadData.grid, config, brandTitle());
                fileName = "拼豆图纸_" + time + ".pdf";
                uri = BeadExporter.savePdfToDownloads(this, data, fileName);
            } else {
                Bitmap bmp = BeadExporter.buildPng(BeadData.grid, config, brandTitle());
                fileName = "拼豆图纸_" + time + ".png";
                uri = BeadExporter.savePngToDownloads(this, bmp, fileName);
                if (bmp != null && !bmp.isRecycled()) bmp.recycle();
            }
            runOnUiThread(() -> {
                wait.dismiss();
                if (uri == null) {
                    Toast.makeText(this, R.string.bead_save_fail, Toast.LENGTH_LONG).show();
                } else {
                    showExportDone(uri, fileName, isPdf);
                }
            });
        });
    }

    private void showExportDone(final Uri uri, final String fileName, final boolean isPdf) {
        Dialog d = new Dialog(this);
        d.setContentView(R.layout.dialog_bead_export_done);
        TextView tvFile = d.findViewById(R.id.done_file);
        tvFile.setText("文件：" + fileName);
        d.findViewById(R.id.done_open).setOnClickListener(v -> {
            String mime = isPdf ? "application/pdf" : "image/png";
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(Intent.createChooser(intent, "打开"));
            } catch (Exception e) {
                Toast.makeText(this, R.string.bead_no_viewer, Toast.LENGTH_SHORT).show();
            }
        });
        d.findViewById(R.id.done_share).setOnClickListener(v -> {
            String mime = isPdf ? "application/pdf" : "image/png";
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mime);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "分享"));
        });
        d.findViewById(R.id.done_close).setOnClickListener(v -> d.dismiss());
        d.show();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
