"""FastAPI 路由定义"""
import logging
from pathlib import Path

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Optional

from config import settings
from services.repair_service import get_repair_service
from services.comparison_service import generate_comparison_image

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api")

# 获取服务单例
repair_service = get_repair_service()


# ---------- Pydantic 请求/响应模型 ----------

class RepairRequest(BaseModel):
    image_path: str
    fidelity_weight: float = 0.7
    colorize: bool = False
    background_enhance: bool = False
    face_enhance: bool = True
    callback_url: Optional[str] = None


class RepairResponse(BaseModel):
    job_id: str
    status: str = "QUEUED"
    estimated_seconds: int = 15


class JobStatusResponse(BaseModel):
    job_id: str
    status: str  # QUEUED, PROCESSING, COMPLETED, FAILED
    progress_percent: int = 0
    result_path: Optional[str] = None
    error_message: Optional[str] = None


class ComparisonRequest(BaseModel):
    original_path: str
    restored_path: str
    layout: str = "side-by-side"  # side-by-side, overlay


class ComparisonResponse(BaseModel):
    comparison_path: str


class HealthResponse(BaseModel):
    status: str = "ok"
    service: str = "photo-repair-service"
    version: str = "1.0.0"


# ---------- 路由 ----------

@router.get("/health", response_model=HealthResponse)
async def health():
    """健康检查"""
    return HealthResponse()


@router.post("/repair", response_model=RepairResponse)
async def submit_repair(request: RepairRequest):
    """提交照片修复任务（异步）"""
    if not Path(request.image_path).exists():
        raise HTTPException(status_code=400, detail=f"图片文件不存在: {request.image_path}")

    # 校验保真度范围
    if not (0.0 <= request.fidelity_weight <= 1.0):
        raise HTTPException(status_code=400, detail="保真度参数必须在 0.0~1.0 之间")

    job_id = repair_service.submit(
        image_path=request.image_path,
        fidelity_weight=request.fidelity_weight,
        colorize=request.colorize,
        background_enhance=request.background_enhance,
        face_enhance=request.face_enhance,
        callback_url=request.callback_url,
    )

    return RepairResponse(job_id=job_id)


@router.get("/repair/{job_id}/status", response_model=JobStatusResponse)
async def get_repair_status(job_id: str):
    """查询修复任务状态"""
    status = repair_service.get_status(job_id)
    if status is None:
        raise HTTPException(status_code=404, detail=f"任务不存在: {job_id}")

    return JobStatusResponse(
        job_id=status.job_id,
        status=status.status,
        progress_percent=status.progress_percent,
        result_path=status.result_path,
        error_message=status.error_message,
    )


@router.post("/repair/{job_id}/comparison", response_model=ComparisonResponse)
async def create_comparison(job_id: str, request: ComparisonRequest):
    """生成对比图"""
    if not Path(request.original_path).exists():
        raise HTTPException(status_code=400, detail=f"原图文件不存在: {request.original_path}")
    if not Path(request.restored_path).exists():
        raise HTTPException(status_code=400, detail=f"修复图文件不存在: {request.restored_path}")

    try:
        comparison_path = generate_comparison_image(
            original_path=request.original_path,
            restored_path=request.restored_path,
            layout=request.layout,
        )
        return ComparisonResponse(comparison_path=comparison_path)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"对比图生成失败: {str(e)}")
