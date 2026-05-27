"""CodeFormer 模型加载与推理

注意：此模块需要安装 CodeFormer 及其依赖（torch, torchvision 等）。
在实际部署前，请参考 CodeFormer 官方仓库安装模型权重文件。
https://github.com/sczhou/CodeFormer

如果没有 GPU 或未安装 CodeFormer，此模块提供降级方案：
使用 PIL 进行基础图像增强（去噪、锐化、对比度增强）。
"""

import logging
import numpy as np
from pathlib import Path
from typing import Optional

logger = logging.getLogger(__name__)

# 尝试导入 CodeFormer
try:
    import torch
    HAS_TORCH = True
except ImportError:
    HAS_TORCH = False
    logger.warning("PyTorch 未安装，将使用降级图像增强方案")

# 尝试导入 CodeFormer 相关模块
HAS_CODEFORMER = False
try:
    # 实际部署时取消以下注释:
    # from basicsr.archs.codeformer_arch import CodeFormer
    # from basicsr.utils import img2tensor, tensor2img
    # HAS_CODEFORMER = True
    pass
except ImportError:
    logger.warning("CodeFormer 未安装，将使用降级图像增强方案")


class CodeFormerRunner:
    """CodeFormer 模型运行器

    提供两种运行模式：
    1. 完整模式：加载真实 CodeFormer 模型进行 AI 修复（需要 torch + codeformer 权重）
    2. 降级模式：使用 PIL 进行基础图像增强（无需 GPU）
    """

    def __init__(self, model_path: Optional[str] = None, device: str = "cpu"):
        self.model_path = model_path
        self.device = device if HAS_TORCH else "cpu"
        self.model = None
        self.use_real_model = False

        if HAS_CODEFORMER and model_path and Path(model_path).exists():
            self._load_codeformer(model_path)
        else:
            logger.info("使用 PIL 降级增强方案（无需 GPU）")

    def _load_codeformer(self, model_path: str):
        """加载 CodeFormer 模型权重（需要实际部署时实现）"""
        # TODO: 实际部署时实现
        # checkpoint = torch.load(model_path)
        # self.model = CodeFormer(...)
        # self.model.load_state_dict(checkpoint)
        # self.model.to(self.device)
        # self.model.eval()
        # self.use_real_model = True
        pass

    def enhance(
        self,
        img: np.ndarray,
        fidelity_weight: float = 0.7,
        background_enhance: bool = False,
        face_enhance: bool = True,
    ) -> np.ndarray:
        """对输入图像进行增强修复

        Args:
            img: RGB 格式的 numpy array (H, W, 3)
            fidelity_weight: 保真度权重 0.0~1.0。越高越保留原图特征，越低修复越激进
            background_enhance: 是否增强背景
            face_enhance: 是否增强人脸

        Returns:
            修复后的 RGB numpy array
        """
        if self.use_real_model and self.model is not None:
            return self._enhance_with_codeformer(img, fidelity_weight)
        else:
            return self._enhance_with_pil(img, fidelity_weight, background_enhance)

    def _enhance_with_codeformer(self, img: np.ndarray, fidelity_weight: float) -> np.ndarray:
        """使用 CodeFormer 模型进行修复（需要实际部署时实现）"""
        # TODO: 实际部署时实现 CodeFormer 推理逻辑
        # tensor = img2tensor(img).unsqueeze(0).to(self.device)
        # with torch.no_grad():
        #     output = self.model(tensor, w=fidelity_weight)
        # return tensor2img(output)
        return img

    def _enhance_with_pil(
        self, img: np.ndarray, fidelity_weight: float, background_enhance: bool
    ) -> np.ndarray:
        """使用 PIL 进行降级图像增强

        fidelity_weight 映射到增强强度：
        - 0.7 (SLIGHT): 轻度锐化 + 轻度对比度
        - 0.5 (MODERATE): 中度锐化 + 降噪 + 对比度
        - 0.3 (SEVERE): 强锐化 + 强降噪 + 高对比度
        """
        from PIL import Image, ImageEnhance, ImageFilter

        pil_img = Image.fromarray(img.astype(np.uint8))

        # 根据保真度确定增强强度
        # fidelity 越低 = 问题越严重 = 增强越强
        if fidelity_weight <= 0.4:  # SEVERE
            sharpen_factor = 2.0
            contrast_factor = 1.3
            denoise_radius = 2
        elif fidelity_weight <= 0.6:  # MODERATE
            sharpen_factor = 1.5
            contrast_factor = 1.15
            denoise_radius = 1
        else:  # SLIGHT
            sharpen_factor = 1.2
            contrast_factor = 1.05
            denoise_radius = 0

        # 降噪（使用中值滤波）
        if denoise_radius > 0:
            pil_img = pil_img.filter(ImageFilter.MedianFilter(denoise_radius))

        # 锐化
        enhancer = ImageEnhance.Sharpness(pil_img)
        pil_img = enhancer.enhance(sharpen_factor)

        # 对比度增强
        enhancer = ImageEnhance.Contrast(pil_img)
        pil_img = enhancer.enhance(contrast_factor)

        if background_enhance:
            # 背景增强：轻微的亮度提升
            enhancer = ImageEnhance.Brightness(pil_img)
            pil_img = enhancer.enhance(1.05)

        return np.array(pil_img)


# 全局单例
_runner_instance: Optional[CodeFormerRunner] = None


def get_runner(model_path: Optional[str] = None, device: str = "cpu") -> CodeFormerRunner:
    """获取 CodeFormerRunner 单例"""
    global _runner_instance
    if _runner_instance is None:
        _runner_instance = CodeFormerRunner(model_path, device)
    return _runner_instance
