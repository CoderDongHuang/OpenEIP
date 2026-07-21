FROM python:3.12.10-slim-bookworm

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY scripts scripts
RUN useradd --create-home --uid 10001 spike
RUN mkdir /results && chown 10001:10001 /results

USER spike
CMD ["python", "scripts/milvus_spike.py"]
