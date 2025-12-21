#!/bin/bash

# ========================================
# 자동 빌드 및 배포 스크립트 (Ubuntu/Linux)
# ========================================

echo ""
echo "========================================"
echo "  선물/옵션 대시보드 자동 배포"
echo "========================================"
echo ""

# 1. Git Pull (최신 코드 받기)
echo "[1/4] Git Pull..."
git pull origin main
if [ $? -ne 0 ]; then
    echo "Git Pull 실패!"
    exit 1
fi

# 2. 기존 프로세스 종료
echo ""
echo "[2/4] 기존 프로세스 종료..."
PID=$(lsof -t -i:8080)
if [ ! -z "$PID" ]; then
    echo "포트 8080을 사용중인 프로세스 종료: $PID"
    kill -9 $PID
    sleep 2
fi

# 3. Maven 빌드
echo ""
echo "[3/4] Maven 빌드..."
./mvnw clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "빌드 실패!"
    exit 1
fi

# 4. 애플리케이션 실행
echo ""
echo "[4/4] 애플리케이션 실행..."
echo ""
echo "========================================"
echo "  배포 완료! http://your-server:8080"
echo "========================================"
echo ""

# 백그라운드 실행 및 로그 파일로 출력
nohup java -jar target/futures-options-dashboard-1.0.0.jar > app.log 2>&1 &

echo "애플리케이션이 백그라운드에서 실행중입니다."
echo "로그 확인: tail -f app.log"
echo "프로세스 확인: ps aux | grep java"
echo "종료: kill \$(lsof -t -i:8080)"
echo ""
