#!/bin/bash
# ========================================
# 서버에서 이 명령들을 복사해서 실행하세요
# ========================================

cd /opt/futures-dashboard/option_monitor

# 1. 최신 코드 받기
git pull origin master

# 2. 실행 권한 부여
chmod +x mvnw.sh
chmod +x *.sh

# 3. Maven Wrapper 버전 확인
./mvnw.sh --version

# 4. 핫 리로드 실행
./hot-reload.sh
