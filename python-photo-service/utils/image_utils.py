"""图像预处理和后处理工具"""
from PIL import Image, ImageEnhance
import numpy as np
from pathlib import Path
from typing import Tuple, Optional

ALLOWED_FORMATS = {'.jpg', '.jpeg', '.png', '.webp', '.bmp'}
MAX_IMAGE_SIZE = 2048


def load_image(image_path: str) -> Image.Image:
    """加载图片并验证格式"""
    path = Path(image_path)
    if not path.exists():
        raise FileNotFoundError(f"图片文件不存在: {image_path}")
    if path.suffix.lower() not in ALLOWED_FORMATS:
        raise ValueError(f"不支持的图片格式: {path.suffix}")
    img = Image.open(image_path)
    if img.mode not in ('RGB', 'RGBA', 'L'):
        img = img.convert('RGB')
    return img


def resize_if_needed(img: Image.Image, max_size: int = MAX_IMAGE_SIZE) -> Image.Image:
    """如果图片尺寸超过限制，等比缩放"""
    if img.width <= max_size and img.height <= max_size:
        return img
    ratio = max_size / max(img.width, img.height)
    new_size = (int(img.width * ratio), int(img.height * ratio))
    return img.resize(new_size, Image.LANCZOS)


def pil_to_numpy(img: Image.Image) -> np.ndarray:
    """PIL Image 转 numpy array (RGB)"""
    if img.mode == 'RGBA':
        # 创建白色背景
        background = Image.new('RGB', img.size, (255, 255, 255))
        background.paste(img, mask=img.split()[3])
        img = background
    elif img.mode == 'L':
        img = img.convert('RGB')
    return np.array(img)


def numpy_to_pil(arr: np.ndarray) -> Image.Image:
    """numpy array 转 PIL Image"""
    arr = np.clip(arr, 0, 255).astype(np.uint8)
    return Image.fromarray(arr)


def enhance_sharpness(img: Image.Image, factor: float = 1.2) -> Image.Image:
    """增强图像锐度"""
    enhancer = ImageEnhance.Sharpness(img)
    return enhancer.enhance(factor)


def enhance_contrast(img: Image.Image, factor: float = 1.1) -> Image.Image:
    """增强对比度"""
    enhancer = ImageEnhance.Contrast(img)
    return enhancer.enhance(factor)


def save_image(img: Image.Image, output_path: str, quality: int = 95) -> str:
    """保存图片，自动创建目录"""
    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    # 确保 RGB 模式用于 JPG 保存
    if img.mode == 'RGBA' and path.suffix.lower() in ('.jpg', '.jpeg'):
        background = Image.new('RGB', img.size, (255, 255, 255))
        background.paste(img, mask=img.split()[3])
        img = background
    img.save(output_path, quality=quality)
    return output_path
