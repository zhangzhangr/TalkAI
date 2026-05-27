"""应用配置管理"""
from pydantic_settings import BaseSettings
from pathlib import Path


class Settings(BaseSettings):
    # 服务配置
    host: str = "0.0.0.0"
    port: int = 9000

    # 存储路径
    upload_dir: str = "./uploads/photo"
    output_dir: str = "./outputs/photo"

    # CodeFormer 模型配置
    codeformer_model_path: str = "./models/codeformer.pth"
    device: str = "cuda"  # cuda / cpu

    # 最大并发修复任务数
    max_concurrent_jobs: int = 2

    # 单张图片最大尺寸（超过会等比缩放）
    max_image_size: int = 2048

    model_config = {"env_prefix": "PHOTO_"}


settings = Settings()

# 确保输出目录存在
Path(settings.upload_dir).mkdir(parents=True, exist_ok=True)
Path(settings.output_dir).mkdir(parents=True, exist_ok=True)
