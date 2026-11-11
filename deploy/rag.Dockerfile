FROM python:3.12-slim-bookworm
LABEL org.opencontainers.image.source="https://github.com/Line4-Idea2IP/Idea2IP-BE"
ENV PYTHONUNBUFFERED=1
WORKDIR /app
COPY pyproject.toml constraints-tested.txt ./
COPY idea2ip ./idea2ip
COPY samples/legal_catalog.json ./samples/legal_catalog.json
RUN pip install --no-cache-dir -c constraints-tested.txt .
USER 10001:10001
EXPOSE 8000
CMD ["python", "-m", "uvicorn", "idea2ip.main:app", "--host", "0.0.0.0", "--port", "8000"]
