# -*- coding: utf-8 -*-
"""
一键生成 Android 应用图标的多密度 PNG 资源。

用法：
    python make_icons.py

说明：
- 输入：drawable/ic_launcher_image.jpg（1280×1280 方形原图）
- 输出 1（传统图标，Android 7 及以下使用）：
    mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png
    mipmap-{...}/ic_launcher_round.png
    内容缩放到画布 84% 居中，四周留透明边距，视觉大小与其他应用一致。
- 输出 2（自适应图标前景，Android 8+ 使用）：
    mipmap-{...}/ic_launcher_fg.png
    内容铺满 108dp 画布（432px @xxxhdpi），由桌面按形状裁剪。

尺寸标准：
- 传统图标基准 48dp：mdpi 48 / hdpi 72 / xhdpi 96 / xxhdpi 144 / xxxhdpi 192（像素）
- 自适应图标画布 108dp：mdpi 108 / hdpi 162 / xhdpi 216 / xxhdpi 324 / xxxhdpi 432（像素）
"""
import os
from PIL import Image

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(BASE_DIR, "icon_source.jpg")
RES_DIR = os.path.join(BASE_DIR, "app", "src", "main", "res")

# 密度倍率
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
# 传统图标基准 48dp，自适应画布基准 108dp
LEGACY_BASE_DP = 48
ADAPTIVE_BASE_DP = 108
# 传统图标内容占画布比例（四周留透明边）
LEGACY_CONTENT_RATIO = 0.84


def make_icon(dst_path, canvas_px, content_ratio):
    """把原图缩放居中放进透明画布并保存 PNG"""
    src = Image.open(SRC).convert("RGBA")
    canvas = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    content_px = round(canvas_px * content_ratio)
    scaled = src.resize((content_px, content_px), Image.LANCZOS)
    offset = (canvas_px - content_px) // 2
    canvas.paste(scaled, (offset, offset))
    canvas.save(dst_path, "PNG")
    print("生成", os.path.relpath(dst_path, BASE_DIR), f"({canvas_px}x{canvas_px})")


def main():
    if not os.path.exists(SRC):
        raise SystemExit("找不到原图：" + SRC)

    for dpi, scale in DENSITIES.items():
        folder = os.path.join(RES_DIR, f"mipmap-{dpi}")
        os.makedirs(folder, exist_ok=True)

        legacy_px = round(LEGACY_BASE_DP * scale)
        make_icon(os.path.join(folder, "ic_launcher.png"), legacy_px, LEGACY_CONTENT_RATIO)
        make_icon(os.path.join(folder, "ic_launcher_round.png"), legacy_px, LEGACY_CONTENT_RATIO)

        adaptive_px = round(ADAPTIVE_BASE_DP * scale)
        make_icon(os.path.join(folder, "ic_launcher_fg.png"), adaptive_px, 1.0)

    print("全部完成")


if __name__ == "__main__":
    main()
