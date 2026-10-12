package com.huanghy7588.xiaqiaoqiaogongjvxiang.bead;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.Serializable;

/**
 * 拼豆图纸配置（含导出选项）。用 SharedPreferences 持久化，记住用户上次的选择。
 */
public class BeadConfig implements Serializable {
    public String brandId = "mard";
    public String brandName = "MARD 家";
    public int colorCount = 221;
    public String paletteFile = "mard_221.json";

    public int gridWidth = 50;
    public int gridHeight = 50;
    public int maxColors = 32;

    public boolean dither = false;
    public boolean removeBackground = false;

    // 导出选项
    public boolean showCodes = true;
    public boolean showGrid = true;
    public boolean boldEvery5 = true;
    public boolean paginate = true;
    public String paper = "A4";

    private static final String PREFS = "bead_config";

    public static BeadConfig load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        BeadConfig c = new BeadConfig();
        c.brandId = sp.getString("brandId", c.brandId);
        c.brandName = sp.getString("brandName", c.brandName);
        c.colorCount = sp.getInt("colorCount", c.colorCount);
        c.paletteFile = sp.getString("paletteFile", c.paletteFile);
        c.gridWidth = sp.getInt("gridWidth", c.gridWidth);
        c.gridHeight = sp.getInt("gridHeight", c.gridHeight);
        c.maxColors = sp.getInt("maxColors", c.maxColors);
        c.dither = sp.getBoolean("dither", c.dither);
        c.removeBackground = sp.getBoolean("removeBackground", c.removeBackground);
        c.showCodes = sp.getBoolean("showCodes", c.showCodes);
        c.showGrid = sp.getBoolean("showGrid", c.showGrid);
        c.boldEvery5 = sp.getBoolean("boldEvery5", c.boldEvery5);
        c.paginate = sp.getBoolean("paginate", c.paginate);
        c.paper = sp.getString("paper", c.paper);
        return c;
    }

    public void save(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit()
                .putString("brandId", brandId)
                .putString("brandName", brandName)
                .putInt("colorCount", colorCount)
                .putString("paletteFile", paletteFile)
                .putInt("gridWidth", gridWidth)
                .putInt("gridHeight", gridHeight)
                .putInt("maxColors", maxColors)
                .putBoolean("dither", dither)
                .putBoolean("removeBackground", removeBackground)
                .putBoolean("showCodes", showCodes)
                .putBoolean("showGrid", showGrid)
                .putBoolean("boldEvery5", boldEvery5)
                .putBoolean("paginate", paginate)
                .putString("paper", paper)
                .apply();
    }

    /** 应用某个品牌 + 色数选项（切换品牌时颜色数重置为该品牌默认值由调用方处理）。 */
    public void applyBrand(BeadBrands.Brand brand, BeadBrands.CountOption opt) {
        this.brandId = brand.id;
        this.brandName = brand.name;
        this.colorCount = opt.count;
        this.paletteFile = opt.file;
    }
}
