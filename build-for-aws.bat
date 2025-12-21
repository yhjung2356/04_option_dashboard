@echo off
REM ======================================
REM AWS 배포용 빌드 스크립트
REM ======================================

echo.
echo ========================================
echo  Futures Options Dashboard
echo  AWS 배포용 빌드 시작
echo ========================================
echo.

REM 1. 기존 빌드 정리
echo [1/4] 기존 빌드 정리 중...
call mvnw.cmd clean
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Clean 실패!
    pause
    exit /b 1
)
echo [OK] Clean 완료
echo.

REM 2. Maven 패키징 (테스트 스킵)
echo [2/4] Maven 패키징 중... (테스트 스킵)
call mvnw.cmd package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] 빌드 실패!
    pause
    exit /b 1
)
echo [OK] 빌드 완료
echo.

REM 3. 빌드 결과 확인
echo [3/4] 빌드 결과 확인 중...
if not exist "target\futures-options-dashboard-1.0.0.jar" (
    echo [ERROR] JAR 파일을 찾을 수 없습니다!
    pause
    exit /b 1
)

for %%A in ("target\futures-options-dashboard-1.0.0.jar") do set SIZE=%%~zA
echo [OK] JAR 파일 생성 완료
echo     파일: target\futures-options-dashboard-1.0.0.jar
echo     크기: %SIZE% bytes
echo.

REM 4. 배포 준비 완료
echo [4/4] 배포 준비 완료!
echo.
echo ========================================
echo  빌드 완료!
echo ========================================
echo.
echo 다음 단계:
echo 1. EC2 인스턴스에 JAR 파일 업로드
echo    scp -i "your-key.pem" target\futures-options-dashboard-1.0.0.jar ec2-user@YOUR-IP:/home/ec2-user/
echo.
echo 2. SSH 접속
echo    ssh -i "your-key.pem" ec2-user@YOUR-IP
echo.
echo 3. 애플리케이션 실행
echo    java -jar -Dspring.profiles.active=prod futures-options-dashboard-1.0.0.jar
echo.
echo 자세한 내용은 AWS배포가이드.md 참조
echo ========================================
echo.

pause
