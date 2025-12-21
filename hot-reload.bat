@echo off
REM ========================================
REM 핫 리로드 (파일 변경 감지 자동 재시작)
REM ========================================

echo.
echo ========================================
echo  핫 리로드 모드 (개발용)
echo ========================================
echo.
echo 파일 변경 시 자동으로 재빌드/재시작됩니다.
echo Ctrl+C로 종료하세요.
echo.

REM Spring Boot DevTools가 활성화되어 있으면 자동 리로드
call mvnw spring-boot:run

REM DevTools가 없다면 위 명령어가 동작
REM 또는 아래처럼 감시 모드로 실행
REM call mvnw compile spring-boot:run
