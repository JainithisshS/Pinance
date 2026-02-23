FROM python:3.11-slim

WORKDIR /app

# Install system deps for spacy, torch, etc.
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential gcc curl && \
    rm -rf /var/lib/apt/lists/*

# Copy requirements first (Docker cache layer)
COPY backend/requirements.txt ./backend/
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r backend/requirements.txt && \
    pip install --no-cache-dir tf-keras

# Copy backend code
COPY backend/ ./backend/

# Expose port (Render sets PORT env var)
EXPOSE 8001

# Run with uvicorn — Render sets $PORT
CMD ["sh", "-c", "cd /app && python -m uvicorn backend.main:app --host 0.0.0.0 --port ${PORT:-8001}"]
