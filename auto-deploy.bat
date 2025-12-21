@echo off
REM ========================================
REM 자동 빌드 및 배포 스크립트
REM ========================================

echo.
echo ========================================
echo  선물/옵션 대시보드 자동 배포
echo ========================================
echo.

REM 1. Git Pull (최신 코드 받기)
echo [1/4] Git Pull...
git pull origin main
if errorlevel 1 (
    echo Git Pull 실패!
    pause
    exit /b 1
)

REM 2. 기존 프로세스 종료
echo.
echo [2/4] 기존 프로세스 종료...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080 ^| findstr LISTENING') do (
    echo 포트 8080을 사용중인 프로세스 종료: %%a
    taskkill /F /PID %%a >nul 2>&1
)

REM 3. Maven 빌드
echo.
echo [3/4] Maven 빌드...
call mvnw clean package -DskipTests
if errorlevel 1 (
    echo 빌드 실패!
    pause
    exit /b 1
)

REM 4. 애플리케이션 실행
echo.
echo [4/4] 애플리케이션 실행...
echo.
echo ========================================
echo  배포 완료! http://localhost:8080
echo ========================================
echo.

start "Futures Dashboard" java -jar target\futures-options-dashboard-1.0.0.jar

timeout /t 3 >nul
start http://localhost:8080

echo.
echo 애플리케이션이 백그라운드에서 실행중입니다.
echo 종료하려면 작업 관리자에서 java.exe를 종료하세요.
echo.
