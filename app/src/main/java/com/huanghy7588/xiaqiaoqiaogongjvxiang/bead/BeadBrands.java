package com.huanghy7588.xiaqiaoqiaogongjvxiang.bead;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置拼豆品牌与可选色数（数据来源 HansBug/pindou-color-data，MIT License）。
 * 不同品牌色号完全不通用，切换品牌必须重新做颜色匹配。
 *
 * 说明：Hama(92) / Perler(103) 属国际数据集（CSV，需额外转换），本期未内置，后续可加。
 */
public class BeadBrands {

    public static class CountOption {
        public final int count;
        public final String file;
        public final boolean recommended;

        public CountOption(int count, String file, boolean recommended) {
            this.count = count;
            this.file = file;
            this.recommended = recommended;
        }
    }

    public static class Brand {
        public final String id;
        public final String name;
        public final List<CountOption> options;

        public Brand(String id, String name, List<CountOption> options) {
            this.id = id;
            this.name = name;
            this.options = options;
        }

        public CountOption getDefault() {
            return options.get(0);
        }

        public CountOption findByCount(int c) {
            for (CountOption o : options) {
                if (o.count == c) return o;
            }
            return options.get(0);
        }
    }

    private static final List<Brand> BRANDS = new ArrayList<>();

    static {
        List<CountOption> mard = new ArrayList<>();
        mard.add(new CountOption(221, "mard_221.json", true));
        mard.add(new CountOption(291, "mard_291.json", false));
        BRANDS.add(new Brand("mard", "MARD 家", mard));

        List<CountOption> artkal = new ArrayList<>();
        artkal.add(new CountOption(197, "artkal_197.json", true));
        artkal.add(new CountOption(221, "artkal_221.json", false));
        artkal.add(new CountOption(418, "artkal_418.json", false));
        BRANDS.add(new Brand("artkal", "优肯 Artkal", artkal));

        BRANDS.add(new Brand("coco", "COCO", single(291, "coco_291.json")));
        BRANDS.add(new Brand("manman", "漫漫家", single(278, "manman_278.json")));
        BRANDS.add(new Brand("panpan", "盼盼家", single(289, "panpan_289.json")));
        BRANDS.add(new Brand("mixiaowo", "咪小窝", single(290, "mixiaowo_290.json")));
    }

    private static List<CountOption> single(int c, String f) {
        List<CountOption> l = new ArrayList<>();
        l.add(new CountOption(c, f, true));
        return l;
    }

    public static List<Brand> all() {
        return BRANDS;
    }

    public static Brand find(String id) {
        if (id != null) {
            for (Brand b : BRANDS) {
                if (b.id.equals(id)) return b;
            }
        }
        return BRANDS.get(0);
    }
}
