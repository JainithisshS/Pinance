FROM python:3.11-slim

WORKDIR /app

# Install system deps for spacy, torch, etc.
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential gcc curl && \
    rm -rf /var/lib/apt/lists/*

# Copy requirements first (Docker cache layer)
COPY backend/requirements.txt ./backend/
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu && \
    pip install --no-cache-dir -r backend/requirements.txt && \
    pip install --no-cache-dir tf-keras

# Copy backend code
COPY backend/ ./backend/

# HF Spaces uses port 7860 by default
EXPOSE 7860

# Run with uvicorn — HF Spaces expects port 7860
CMD ["python", "-m", "uvicorn", "backend.main:app", "--host", "0.0.0.0", "--port", "7860"]
