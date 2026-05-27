"""修复任务管理服务"""
import logging
import threading
import uuid
from typing import Dict, Optional
from dataclasses import dataclass, field
from datetime import datetime

import requests

from config import settings
from models.codeformer_runner import CodeFormerRunner, get_runner
from utils.image_utils import load_image, resize_if_needed, pil_to_numpy, numpy_to_pil, save_image
from utils.image_utils import enhance_sharpness, enhance_contrast
from .comparison_service import generate_comparison_image

logger = logging.getLogger(__name__)


@dataclass
class JobStatus:
    job_id: str
    status: str = "QUEUED"  # QUEUED, PROCESSING, COMPLETED, FAILED
    progress_percent: int = 0
    result_path: Optional[str] = None
    error_message: Optional[str] = None
    created_at: datetime = field(default_factory=datetime.now)


class RepairService:
    """修复任务管理服务

    提供异步修复任务提交、状态查询和回调通知。
    使用后台线程处理 GPU 推理任务，避免阻塞 FastAPI 事件循环。
    """

    def __init__(self, runner: Optional[CodeFormerRunner] = None):
        self.runner = runner or get_runner()
        self.jobs: Dict[str, JobStatus] = {}
        self._semaphore = threading.Semaphore(settings.max_concurrent_jobs)

    def submit(self, image_path: str, fidelity_weight: float = 0.7,
               colorize: bool = False, background_enhance: bool = False,
               face_enhance: bool = True, callback_url: Optional[str] = None) -> str:
        """提交修复任务，返回 job_id"""
        job_id = str(uuid.uuid4())
        self.jobs[job_id] = JobStatus(job_id=job_id, status="QUEUED")

        thread = threading.Thread(
            target=self._process,
            args=(job_id, image_path, fidelity_weight, colorize,
                  background_enhance, face_enhance, callback_url),
            daemon=True
        )
        thread.start()

        logger.info(f"Job submitted: {job_id} for {image_path}")
        return job_id

    def get_status(self, job_id: str) -> Optional[JobStatus]:
        """查询任务状态"""
        return self.jobs.get(job_id)

    def _process(self, job_id: str, image_path: str, fidelity_weight: float,
                 colorize: bool, background_enhance: bool, face_enhance: bool,
                 callback_url: Optional[str]):
        """后台处理线程"""
        self.jobs[job_id].status = "PROCESSING"

        with self._semaphore:
            try:
                self.jobs[job_id].progress_percent = 10

                # 1. 加载和预处理
                img = load_image(image_path)
                img = resize_if_needed(img)
                self.jobs[job_id].progress_percent = 20

                # 2. CodeFormer 修复
                img_np = pil_to_numpy(img)
                result_np = self.runner.enhance(
                    img_np,
                    fidelity_weight=fidelity_weight,
                    background_enhance=background_enhance,
                    face_enhance=face_enhance,
                )
                self.jobs[job_id].progress_percent = 70

                # 3. 后处理
                result_img = numpy_to_pil(result_np)
                result_img = enhance_sharpness(result_img, factor=1.1)
                result_img = enhance_contrast(result_img, factor=1.05)
                self.jobs[job_id].progress_percent = 85

                # 4. 黑白上色（降级：调整色温）
                if colorize:
                    result_img = self._apply_pseudo_colorize(result_img)
                self.jobs[job_id].progress_percent = 90

                # 5. 保存结果
                output_filename = f"restored_{job_id[:8]}.jpg"
                output_path = str(settings.output_dir + "/" + output_filename)
                # 确保 output_dir 是绝对路径
                from pathlib import Path
                output_path = str(Path(settings.output_dir) / output_filename)
                Path(output_path).parent.mkdir(parents=True, exist_ok=True)
                save_image(result_img, output_path)
                self.jobs[job_id].progress_percent = 95

                # 6. 生成对比图
                comparison_path = generate_comparison_image(image_path, output_path)
                self.jobs[job_id].progress_percent = 98

                # 7. 完成
                self.jobs[job_id].status = "COMPLETED"
                self.jobs[job_id].result_path = output_path
                self.jobs[job_id].progress_percent = 100

                logger.info(f"Job completed: {job_id}, result: {output_path}")

                # 8. 回调通知
                if callback_url:
                    self._send_callback(callback_url, job_id, output_path)

            except Exception as e:
                logger.error(f"Job failed: {job_id}, error: {e}")
                self.jobs[job_id].status = "FAILED"
                self.jobs[job_id].error_message = str(e)

                if callback_url:
                    self._send_callback(callback_url, job_id, "", str(e))

    def _apply_pseudo_colorize(self, img) -> 'Image.Image':
        """伪上色：调整饱和度和色温（降级方案）

        真实上色需要 DeOldify 或类似模型。
        """
        from PIL import ImageEnhance
        # 轻微增加饱和度模拟上色效果
        enhancer = ImageEnhance.Color(img)
        return enhancer.enhance(1.3)

    def _send_callback(self, url: str, job_id: str, result_path: str,
                        error: Optional[str] = None):
        """发送回调通知到 Java 服务"""
        try:
            payload = {
                "job_id": job_id,
                "result_path": result_path,
                "status": "COMPLETED" if not error else "FAILED",
            }
            if error:
                payload["error_message"] = error

            response = requests.post(url, json=payload, timeout=10)
            logger.info(f"Callback sent to {url}: {response.status_code}")
        except Exception as e:
            logger.error(f"Callback failed to {url}: {e}")


# 全局单例
_service_instance: Optional[RepairService] = None


def get_repair_service() -> RepairService:
    """获取 RepairService 单例"""
    global _service_instance
    if _service_instance is None:
        _service_instance = RepairService()
    return _service_instance
