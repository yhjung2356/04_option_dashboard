# 🚀 빠른 해결 가이드 - mvnw 오류

## 문제
```bash
./mvnw: line 1: @REM: command not found
```

## 즉시 해결 방법

### 서버에서 다음 명령 실행:

```bash
cd /opt/futures-dashboard/option_monitor

# 방법 1: Git Pull (가장 권장)
git pull origin main
chmod +x *.sh mvnw.sh
./hot-reload.sh

# 방법 2: 자동 수정 스크립트 사용
chmod +x fix-mvnw-linux.sh
./fix-mvnw-linux.sh
./hot-reload.sh

# 방법 3: 수동 수정 (Git 사용 불가시)
rm -f mvnw
chmod +x mvnw.sh *.sh
./hot-reload.sh
```

## 원인
Windows용 `mvnw` 파일이 Linux 서버에 업로드되어 발생한 문제

## 해결됨
모든 `.sh` 스크립트가 `./mvnw.sh`를 사용하도록 수정 완료
- ✅ hot-reload.sh
- ✅ auto-deploy.sh  
- ✅ setup-systemd.sh
- ✅ quick-start.sh
- ✅ docker-auto-deploy.sh

---
**수정일**: 2025-12-21
