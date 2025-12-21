#!/bin/bash

# ========================================
# Docker 자동 빌드 및 배포 스크립트 - Ubuntu/Linux
# ========================================

echo ""
echo "========================================"
echo "  Docker 자동 배포"
echo "========================================"
echo ""

# 1. Git Pull
echo "[1/5] Git Pull..."
git pull origin main

# 2. 기존 컨테이너 중지 및 삭제
echo ""
echo "[2/5] 기존 컨테이너 중지..."
docker-compose down

# 3. Maven 빌드
echo ""
echo "[3/5] Maven 빌드..."
./mvnw.sh clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "빌드 실패!"
    exit 1
fi

# 4. Docker 이미지 빌드
echo ""
echo "[4/5] Docker 이미지 빌드..."
docker-compose build

# 5. 컨테이너 실행
echo ""
echo "[5/5] 컨테이너 실행..."
docker-compose up -d

echo ""
echo "========================================"
echo "  배포 완료! http://your-server:8080"
echo "========================================"
echo ""
echo "로그 확인: docker-compose logs -f"
echo "중지: docker-compose down"
echo ""
