package com.huanghy7588.xiaqiaoqiaogongjvxiang.bead;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.core.graphics.ColorUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 拼豆图纸生成核心：
 * 1. 把图片降采样到目标网格尺寸；
 * 2. 每个像素在 Lab 空间用 CIEDE2000 找最近色（抖动开启时改用 RGB 误差扩散，观感更平滑）；
 * 3. 可选去背景（边缘泛洪填充近似统一背景）；
 * 4. 限制总用色数（超出时把低频色合并到最近的已用色）。
 *
 * 所有耗时计算都在调用方后台线程执行，progress 回调用于更新 UI。
 */
public class BeadGenerator {

    public interface Progress {
        void onProgress(int done, int total);
    }

    public static BeadColor[][] generate(Bitmap src, BeadConfig cfg, Palette palette, Progress cb) {
        int gw = cfg.gridWidth;
        int gh = cfg.gridHeight;
        if (gw <= 0 || gh <= 0 || src == null || palette == null || palette.colors.isEmpty()) {
            return null;
        }

        Bitmap small = Bitmap.createScaledBitmap(src, gw, gh, true);
        int total = gw * gh;
        int[] pixels = new int[total];
        small.getPixels(pixels, 0, gw, 0, 0, gw, gh);
        if (small != src) {
            small.recycle();
        }

        BeadColor[] pal = palette.colors.toArray(new BeadColor[0]);
        boolean[] bgMask = cfg.removeBackground ? buildBackgroundMask(pixels, gw, gh) : null;

        BeadColor[][] grid = new BeadColor[gh][gw];
        int step = Math.max(1, total / 100);
        int done = 0;

        if (cfg.dither) {
            // Floyd-Steinberg 误差扩散：在 RGB 空间扩散误差，仍匹配最近色
            float[] fr = new float[total];
            float[] fg = new float[total];
            float[] fb = new float[total];
            for (int i = 0; i < total; i++) {
                fr[i] = Color.red(pixels[i]);
                fg[i] = Color.green(pixels[i]);
                fb[i] = Color.blue(pixels[i]);
            }
            for (int y = 0; y < gh; y++) {
                for (int x = 0; x < gw; x++) {
                    int idx = y * gw + x;
                    if (bgMask != null && bgMask[idx]) {
                        grid[y][x] = null;
                        done++;
                        continue;
                    }
                    int or = clamp(Math.round(fr[idx]));
                    int og = clamp(Math.round(fg[idx]));
                    int ob = clamp(Math.round(fb[idx]));
                    int bi = nearestRgb(or, og, ob, pal);
                    BeadColor best = pal[bi];
                    grid[y][x] = best;
                    diffuseError(fr, fg, fb, gw, gh, x, y,
                            or - best.rgb[0], og - best.rgb[1], ob - best.rgb[2]);
                    done++;
                    if (cb != null && (done % step == 0 || done == total)) {
                        cb.onProgress(done, total);
                    }
                }
            }
        } else {
            double[] lab = new double[3];
            for (int y = 0; y < gh; y++) {
                for (int x = 0; x < gw; x++) {
                    int idx = y * gw + x;
                    if (bgMask != null && bgMask[idx]) {
                        grid[y][x] = null;
                        done++;
                        if (cb != null && (done % step == 0 || done == total)) {
                            cb.onProgress(done, total);
                        }
                        continue;
                    }
                    int c = pixels[idx];
                    ColorUtils.RGBToLAB(Color.red(c), Color.green(c), Color.blue(c), lab);
                    grid[y][x] = pal[nearestLab(lab, pal)];
                    done++;
                    if (cb != null && (done % step == 0 || done == total)) {
                        cb.onProgress(done, total);
                    }
                }
            }
        }

        applyMaxColors(grid, cfg.maxColors);
        return grid;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static int nearestLab(double[] lab, BeadColor[] pal) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < pal.length; i++) {
            double d = Ciede2000.distance(lab[0], lab[1], lab[2],
                    pal[i].lab[0], pal[i].lab[1], pal[i].lab[2]);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    private static int nearestRgb(int r, int g, int b, BeadColor[] pal) {
        int best = 0;
        long bd = Long.MAX_VALUE;
        for (int i = 0; i < pal.length; i++) {
            int dr = r - pal[i].rgb[0];
            int dg = g - pal[i].rgb[1];
            int db = b - pal[i].rgb[2];
            long d = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    private static void diffuseError(float[] r, float[] g, float[] b, int gw, int gh,
                                     int x, int y, float er, float eg, float eb) {
        add(r, g, b, gw, gh, x + 1, y, 7f / 16f, er, eg, eb);
        add(r, g, b, gw, gh, x - 1, y + 1, 3f / 16f, er, eg, eb);
        add(r, g, b, gw, gh, x, y + 1, 5f / 16f, er, eg, eb);
        add(r, g, b, gw, gh, x + 1, y + 1, 1f / 16f, er, eg, eb);
    }

    private static void add(float[] r, float[] g, float[] b, int gw, int gh,
                            int x, int y, float k, float er, float eg, float eb) {
        if (x < 0 || x >= gw || y < 0 || y >= gh) return;
        int idx = y * gw + x;
        r[idx] += k * er;
        g[idx] += k * eg;
        b[idx] += k * eb;
    }

    /** 限制总用色数：保留最高频的 maxColors 个，其余重映射到最近的已保留色。 */
    private static void applyMaxColors(BeadColor[][] grid, int maxColors) {
        if (maxColors <= 0) return;
        Map<String, Integer> freq = new HashMap<>();
        Map<String, BeadColor> byCode = new HashMap<>();
        for (BeadColor[] row : grid) {
            for (BeadColor c : row) {
                if (c == null) continue;
                freq.put(c.code, freq.getOrDefault(c.code, 0) + 1);
                byCode.put(c.code, c);
            }
        }
        if (byCode.size() <= maxColors) return;

        List<String> codes = new ArrayList<>(byCode.keySet());
        codes.sort((a, b) -> freq.get(b) - freq.get(a));
        List<BeadColor> kept = new ArrayList<>();
        Map<String, BeadColor> keptMap = new HashMap<>();
        for (int i = 0; i < maxColors; i++) {
            BeadColor k = byCode.get(codes.get(i));
            kept.add(k);
            keptMap.put(k.code, k);
        }
        double[][] kl = new double[kept.size()][3];
        for (int i = 0; i < kept.size(); i++) {
            kl[i][0] = kept.get(i).lab[0];
            kl[i][1] = kept.get(i).lab[1];
            kl[i][2] = kept.get(i).lab[2];
        }
        for (int y = 0; y < grid.length; y++) {
            for (int x = 0; x < grid[y].length; x++) {
                BeadColor c = grid[y][x];
                if (c == null || keptMap.containsKey(c.code)) continue;
                double[] lab = c.lab;
                int bi = 0;
                double bd = Double.MAX_VALUE;
                for (int i = 0; i < kept.size(); i++) {
                    double d = Ciede2000.distance(lab[0], lab[1], lab[2],
                            kl[i][0], kl[i][1], kl[i][2]);
                    if (d < bd) {
                        bd = d;
                        bi = i;
                    }
                }
                grid[y][x] = kept.get(bi);
            }
        }
    }

    /** 去背景：从四条边泛洪填充与四角平均色相近的像素，返回被判定为背景的掩码。 */
    private static boolean[] buildBackgroundMask(int[] pixels, int gw, int gh) {
        int total = gw * gh;
        boolean[] mask = new boolean[total];
        int br = 0, bg = 0, bb = 0;
        int[] corners = {0, gw - 1, (gh - 1) * gw, total - 1};
        for (int ci : corners) {
            br += Color.red(pixels[ci]);
            bg += Color.green(pixels[ci]);
            bb += Color.blue(pixels[ci]);
        }
        br /= 4; bg /= 4; bb /= 4;
        final int T = 48; // 容差

        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int x = 0; x < gw; x++) {
            seed(mask, pixels, q, br, bg, bb, T, x);
            seed(mask, pixels, q, br, bg, bb, T, (gh - 1) * gw + x);
        }
        for (int y = 0; y < gh; y++) {
            seed(mask, pixels, q, br, bg, bb, T, y * gw);
            seed(mask, pixels, q, br, bg, bb, T, y * gw + (gw - 1));
        }
        int[] DX = {1, -1, 0, 0};
        int[] DY = {0, 0, 1, -1};
        while (!q.isEmpty()) {
            int idx = q.poll();
            int x = idx % gw;
            int y = idx / gw;
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d];
                int ny = y + DY[d];
                if (nx < 0 || nx >= gw || ny < 0 || ny >= gh) continue;
                int nidx = ny * gw + nx;
                if (mask[nidx]) continue;
                if (near(pixels[nidx], br, bg, bb, T)) {
                    mask[nidx] = true;
                    q.add(nidx);
                }
            }
        }
        return mask;
    }

    private static void seed(boolean[] mask, int[] pixels, ArrayDeque<Integer> q,
                             int br, int bg, int bb, int t, int idx) {
        if (idx < 0 || idx >= mask.length || mask[idx]) return;
        if (near(pixels[idx], br, bg, bb, t)) {
            mask[idx] = true;
            q.add(idx);
        }
    }

    private static boolean near(int c, int br, int bg, int bb, int t) {
        int dr = Color.red(c) - br;
        int dg = Color.green(c) - bg;
        int db = Color.blue(c) - bb;
        return Math.abs(dr) <= t && Math.abs(dg) <= t && Math.abs(db) <= t;
    }
}
