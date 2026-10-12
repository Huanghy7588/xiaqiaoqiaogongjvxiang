package com.huanghy7588.xiaqiaoqiaogongjvxiang.bead;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 拼豆色板：从 assets/palettes/*.json 加载（数据源 HansBug/pindou-color-data，MIT）。
 * 加载时会过滤掉 unidentified=true 的占位色（如盼盼 289 / 咪小窝 290 各 4 个不可辨认色）。
 */
public class Palette {
    public String id;
    public String title;
    public int declaredCount;   // JSON 里声明的总色数（含不可辨认）
    public int usableCount;     // 过滤后实际可用色数
    public List<BeadColor> colors = new ArrayList<>();

    public static Palette load(Context ctx, String fileName) throws Exception {
        InputStream is = null;
        try {
            is = ctx.getAssets().open("palettes/" + fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONObject root = new JSONObject(sb.toString());
            Palette p = new Palette();
            p.id = root.optString("id", fileName);
            p.title = root.optString("title", root.optString("id", fileName));
            p.declaredCount = root.optInt("count", 0);

            JSONArray arr = root.getJSONArray("colors");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                // 过滤不可辨认色
                if (o.optBoolean("unidentified", false)) continue;
                JSONArray rgbArr = o.optJSONArray("rgb");
                if (rgbArr == null || rgbArr.length() < 3) continue;
                int[] rgb = new int[]{rgbArr.getInt(0), rgbArr.getInt(1), rgbArr.getInt(2)};
                String code = o.optString("code", "?");
                String hex = o.optString("hex", "#000000");
                String group = o.optString("group", "");
                p.colors.add(new BeadColor(code, hex, rgb, group));
            }
            p.usableCount = p.colors.size();
            return p;
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ignored) { }
            }
        }
    }
}
