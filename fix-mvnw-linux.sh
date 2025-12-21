#!/bin/bash

# ========================================
# Linux mvnw 문제 긴급 수정 스크립트
# ========================================

echo "========================================="
echo "  mvnw 문제 수정 중..."
echo "========================================="

# 1. 잘못된 mvnw 파일 삭제 (Windows 버전)
if [ -f "./mvnw" ]; then
    echo "잘못된 mvnw 파일 삭제 중..."
    rm -f ./mvnw
fi

# 2. mvnw.sh 실행 권한 부여
if [ -f "./mvnw.sh" ]; then
    echo "mvnw.sh 실행 권한 부여..."
    chmod +x ./mvnw.sh
else
    echo "ERROR: mvnw.sh 파일이 없습니다!"
    exit 1
fi

# 3. 모든 .sh 파일 실행 권한 부여
echo "모든 .sh 파일 실행 권한 부여..."
chmod +x *.sh

# 4. 확인
echo ""
echo "========================================="
echo "  수정 완료!"
echo "========================================="
echo ""
echo "Maven Wrapper 버전:"
./mvnw.sh --version

echo ""
echo "이제 다음 명령을 실행할 수 있습니다:"
echo "  ./hot-reload.sh     - 개발 모드"
echo "  ./auto-deploy.sh    - 자동 배포"
echo ""
