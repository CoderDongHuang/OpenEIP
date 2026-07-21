FROM python:3.12.10-slim-bookworm

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY mcp-server mcp-server
COPY mcp-client mcp-client
RUN useradd --create-home --uid 10001 spike
RUN mkdir /results && chown 10001:10001 /results

USER spike
CMD ["python", "mcp-client/client.py"]
