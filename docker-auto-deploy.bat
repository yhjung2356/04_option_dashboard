@echo off
REM ========================================
REM Docker 자동 빌드 및 배포 스크립트
REM ========================================

echo.
echo ========================================
echo  Docker 자동 배포
echo ========================================
echo.

REM 1. Git Pull
echo [1/5] Git Pull...
git pull origin main

REM 2. 기존 컨테이너 중지 및 삭제
echo.
echo [2/5] 기존 컨테이너 중지...
docker-compose down

REM 3. Maven 빌드
echo.
echo [3/5] Maven 빌드...
call mvnw clean package -DskipTests
if errorlevel 1 (
    echo 빌드 실패!
    pause
    exit /b 1
)

REM 4. Docker 이미지 빌드
echo.
echo [4/5] Docker 이미지 빌드...
docker-compose build

REM 5. 컨테이너 실행
echo.
echo [5/5] 컨테이너 실행...
docker-compose up -d

echo.
echo ========================================
echo  배포 완료! http://localhost:8080
echo ========================================
echo.
echo 로그 확인: docker-compose logs -f
echo 중지: docker-compose down
echo.

timeout /t 3 >nul
start http://localhost:8080
