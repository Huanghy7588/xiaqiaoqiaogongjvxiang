package com.huanghy7588.xiaqiaoqiaogongjvxiang.tiemo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;

/**
 * 贴膜水印机合成核心。
 *
 * 图层顺序（从底到顶）：图片(底图) → 水印 → 底纹 → 小水印。
 * - 水印、底纹：按"短边对齐"平铺铺满整张底图（水印多长，就沿长边重复铺，保证无缝覆盖）。
 * - 小水印：单张，默认居中，可调节 X/Y 偏移。
 * - 每种叠加层均可选择混合模式（标准/覆盖/软光/滤色）与不透明度。
 * - 合成结果尺寸、质量与底图完全一致，仅在原图上叠加，不做任何缩放或压缩重采样。
 */
public class TiemoCompositor {

    /**
     * 混合模式索引，与界面 4 个选项一一对应：
     * 0=标准(SRC_OVER)  1=覆盖(OVERLAY)  2=软光(SOFT_LIGHT)  3=滤色(SCREEN)
     */
    public static final int[] BLEND_INDEX = {0, 1, 2, 3};

    /** 平铺密度：单块水印短边 = 底图短边 * 该系数。越小越密（铺满全图）。 */
    private static final float TILE_DENSITY = 0.3f;

    /** 单个叠加层输入 */
    public static class LayerInput {
        public Bitmap overlay;            // 已解码的水印/底纹/小水印图（可空）
        public int blendIndex = 0;       // 0..3，对应上述模式索引
        public float opacity = 1.0f;     // 0..1
        public boolean tiled = true;     // true=平铺铺满；false=单张（小水印）
        public float xFrac = 0.5f;       // 仅单张有效：中心 X（占底图宽度比例）
        public float yFrac = 0.5f;       // 仅单张有效：中心 Y（占底图高度比例）
        public float scale = 1.0f;       // 仅单张有效：缩放倍数（1=原大小）

        public boolean hasImage() {
            return overlay != null && !overlay.isRecycled();
        }
    }

    /**
     * 合成一张图。
     *
     * @param base  底图（不会被修改，内部会拷贝）
     * @param wm    水印层（可空）
     * @param tex   底纹层（可空）
     * @param small 小水印层（可空）
     * @return 合成后的新 Bitmap（调用方负责 recycle）；失败返回 null
     */
    public static Bitmap composite(Bitmap base, LayerInput wm, LayerInput tex, LayerInput small) {
        if (base == null || base.isRecycled()) return null;
        int W = base.getWidth();
        int H = base.getHeight();
        Bitmap result;
        try {
            result = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
        Canvas canvas = new Canvas(result);
        // 原样绘制底图（保留原尺寸与透明通道）
        canvas.drawBitmap(base, 0, 0, null);

        if (wm != null && wm.hasImage()) drawLayer(canvas, W, H, wm);
        if (tex != null && tex.hasImage()) drawLayer(canvas, W, H, tex);
        if (small != null && small.hasImage()) drawLayer(canvas, W, H, small);

        return result;
    }

    /**
     * 把某一叠加层绘制到 canvas（canvas 当前内容即已合成的底图/前序层）。
     * 流程：先在离屏 Bitmap 上按 opacity 画出该层内容，再以混合模式叠加到主画布。
     */
    private static void drawLayer(Canvas canvas, int W, int H, LayerInput layer) {
        Bitmap overlay = layer.overlay;
        int ovW = overlay.getWidth();
        int ovH = overlay.getHeight();

        Bitmap offscreen;
        try {
            offscreen = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return;
        }
        Canvas off = new Canvas(offscreen);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setAlpha(Math.round(layer.opacity * 255f));

        if (layer.tiled) {
            // 密集平铺：缩放水印，使其短边 = 底图短边 * TILE_DENSITY（保持水印自身比例），
            // 再沿长边重复铺满整张图，保证全图覆盖。
            int baseShort = Math.min(W, H);
            int ovShort = Math.min(ovW, ovH);
            float s = ((float) baseShort / ovShort) * TILE_DENSITY;
            int tw = Math.max(1, Math.round(ovW * s));
            int th = Math.max(1, Math.round(ovH * s));
            // 计算行列数，确保完整覆盖；整网格居中铺放，左右/上下对称
            int cols = (int) Math.ceil((double) W / tw);
            int rows = (int) Math.ceil((double) H / th);
            int startX = (W - cols * tw) / 2;
            int startY = (H - rows * th) / 2;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    off.drawBitmap(overlay, startX + c * tw, startY + r * th, p);
                }
            }
        } else {
            // 单张（小水印）：短边取底图短边的 40%，再按 scale 缩放，居中（或按 X/Y 偏移）放置
            int baseShort = Math.min(W, H);
            int ovShort = Math.min(ovW, ovH);
            float target = baseShort * 0.4f * layer.scale;
            float s = target / ovShort;
            int dw = Math.max(1, Math.round(ovW * s));
            int dh = Math.max(1, Math.round(ovH * s));
            float cx = layer.xFrac * W;
            float cy = layer.yFrac * H;
            RectF dst = new RectF(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f);
            off.drawBitmap(overlay, null, dst, p);
        }

        // 以混合模式把离屏层叠加到主画布
        Paint blendPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        blendPaint.setXfermode(makeXfermode(layer.blendIndex));
        canvas.drawBitmap(offscreen, 0, 0, blendPaint);
        offscreen.recycle();
    }

    /**
     * 根据混合模式索引生成 Xfermode。
     * 软光在 Android 无原生 PorterDuff 常量：API29+ 用 BlendMode.SOFT_LIGHT，
     * 低版本降级为 OVERLAY（视觉近似，均为增强对比的柔和叠加）。
     */
    private static android.graphics.Xfermode makeXfermode(int blendIndex) {
        switch (blendIndex) {
            case 1:  return new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.OVERLAY);
            case 2:  return makeSoftLight();
            case 3:  return new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN);
            default: return new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_OVER);
        }
    }

    private static android.graphics.Xfermode makeSoftLight() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                Class<?> bm = Class.forName("android.graphics.BlendMode");
                Object soft = bm.getField("SOFT_LIGHT").get(null);
                Class<?> bxc = Class.forName("android.graphics.BlendModeXfermode");
                return (android.graphics.Xfermode) bxc.getConstructor(bm).newInstance(soft);
            } catch (Exception ignored) { /* 降级 */ }
        }
        return new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.OVERLAY);
    }
}
