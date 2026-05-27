"""AI 老照片修复服务 - FastAPI 入口

启动方式:
    uvicorn main:app --host 0.0.0.0 --port 9000 --reload

访问:
    http://localhost:9000/docs  - Swagger API 文档
    http://localhost:9000/api/health - 健康检查
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from config import settings
from api.routes import router

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    logger.info("=" * 50)
    logger.info(f"照片修复服务启动中...")
    logger.info(f"运行设备: {settings.device}")
    logger.info(f"上传目录: {settings.upload_dir}")
    logger.info(f"输出目录: {settings.output_dir}")
    logger.info(f"最大并发: {settings.max_concurrent_jobs}")
    logger.info("=" * 50)

    # 预加载模型（可选）
    # from models.codeformer_runner import get_runner
    # runner = get_runner(settings.codeformer_model_path, settings.device)
    # app.state.codeformer_runner = runner

    yield

    logger.info("照片修复服务已关闭")


app = FastAPI(
    title="AI Photo Repair Service",
    description="基于 CodeFormer 的 AI 老照片修复服务",
    version="1.0.0",
    lifespan=lifespan,
)

# 跨域配置
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router)


@app.get("/")
async def root():
    return {"service": "AI Photo Repair", "docs": "/docs", "health": "/api/health"}
