"""对比图合成服务

生成「左原图、右修复图」左右分屏效果图，适配短视频展示。
"""

import logging
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from typing import Optional

from config import settings

logger = logging.getLogger(__name__)

# 尝试加载中文字体
FONT_PATH_CANDIDATES = [
    "C:/Windows/Fonts/msyh.ttc",       # 微软雅黑 (Windows)
    "C:/Windows/Fonts/simhei.ttf",     # 黑体 (Windows)
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",  # DejaVu (Linux)
    "/System/Library/Fonts/PingFang.ttc",  # 苹方 (macOS)
]


def _get_font(size: int = 36) -> ImageFont.FreeTypeFont:
    """获取可用的中文字体"""
    for font_path in FONT_PATH_CANDIDATES:
        if Path(font_path).exists():
            try:
                return ImageFont.truetype(font_path, size)
            except Exception:
                continue
    # 降级：使用默认字体
    return ImageFont.load_default()


def generate_comparison_image(
    original_path: str,
    restored_path: str,
    layout: str = "side-by-side",
    output_path: Optional[str] = None,
) -> str:
    """生成原图与修复图对比图

    Args:
        original_path: 原始照片路径
        restored_path: 修复后照片路径
        layout: 布局模式 - "side-by-side" / "overlay" / "slider"
        output_path: 输出路径，默认自动生成

    Returns:
        对比图输出路径
    """
    original = Image.open(original_path).convert('RGB')
    restored = Image.open(restored_path).convert('RGB')

    if layout == "side-by-side":
        result = _side_by_side(original, restored)
    elif layout == "overlay":
        result = _overlay(original, restored)
    else:
        result = _side_by_side(original, restored)

    # 确定输出路径
    if output_path is None:
        orig_name = Path(original_path).stem
        output_path = str(Path(settings.output_dir) / f"comparison_{orig_name}.jpg")

    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    result.save(output_path, quality=95)
    logger.info(f"Comparison image saved: {output_path}")

    return output_path


def _side_by_side(original: Image.Image, restored: Image.Image) -> Image.Image:
    """左右分屏对比图"""
    # 统一高度
    max_height = max(original.height, restored.height)
    spacing = 4  # 中间分隔线宽度
    label_height = 60  # 顶部标签高度

    total_width = original.width + restored.width + spacing
    total_height = max_height + label_height

    # 创建画布
    result = Image.new('RGB', (total_width, total_height), (255, 255, 255))
    draw = ImageDraw.Draw(result)

    # 粘贴原图（左侧）
    y_offset = label_height + (max_height - original.height) // 2
    result.paste(original, (0, y_offset))

    # 粘贴修复图（右侧）
    y_offset = label_height + (max_height - restored.height) // 2
    result.paste(restored, (original.width + spacing, y_offset))

    # 画中间分隔线
    line_x = original.width + spacing // 2
    draw.line([(line_x, label_height), (line_x, total_height)], fill=(200, 200, 200), width=1)

    # 添加标签文字
    try:
        font = _get_font(28)
        # 左标签 - 原图
        left_text = "原图"
        left_bbox = draw.textbbox((0, 0), left_text, font=font)
        left_x = (original.width - (left_bbox[2] - left_bbox[0])) // 2
        draw.text((left_x, 12), left_text, fill=(120, 120, 120), font=font)

        # 右标签 - 修复完成
        right_text = "修复完成"
        right_bbox = draw.textbbox((0, 0), right_text, font=font)
        right_x = original.width + spacing + (restored.width - (right_bbox[2] - right_bbox[0])) // 2
        draw.text((right_x, 12), right_text, fill=(46, 139, 87), font=font)
    except Exception:
        pass  # 字体加载失败时跳过文字

    return result


def _overlay(original: Image.Image, restored: Image.Image) -> Image.Image:
    """叠加切换对比图（透明度混合）"""
    # 统一尺寸
    target_size = (max(original.width, restored.width), max(original.height, restored.height))
    original = original.resize(target_size, Image.LANCZOS)
    restored = restored.resize(target_size, Image.LANCZOS)

    # 50% 透明度叠加
    result = Image.blend(original, restored, alpha=0.5)

    # 添加文字标注
    draw = ImageDraw.Draw(result)
    try:
        font = _get_font(24)
        draw.text((10, 10), "原图 50% / 修复 50%", fill=(255, 255, 255), font=font)
    except Exception:
        pass

    return result
