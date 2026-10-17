FROM python:3.11-slim
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1 \
    SAFE_NAVI_DB=/app/data/safe_navi_runtime.sqlite \
    SAFE_NAVI_PRODUCT_DB=/data/safe_navi_product.sqlite \
    SAFE_NAVI_EVIDENCE_DIR=/data/evidence
WORKDIR /app
COPY backend/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt \
    && groupadd --gid 10001 safenavi && useradd --uid 10001 --gid 10001 safenavi \
    && mkdir -p /data /app/data && chown safenavi:safenavi /data
COPY backend/app ./app
COPY backend/models/safety_score_linear_v1.json ./models/safety_score_linear_v1.json
COPY backend/data/safe_navi_runtime.sqlite ./data/safe_navi_runtime.sqlite
USER safenavi
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=4)"
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1", "--no-proxy-headers"]
