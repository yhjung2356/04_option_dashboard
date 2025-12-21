# Linux mvnw 문제 수정 완료

## 문제 상황
```bash
./mvnw: line 1: @REM: command not found
./mvnw: line 2: syntax error near unexpected token `('
```

## 원인
- Linux 서버에 Windows용 `mvnw` 파일이 업로드됨
- 모든 `.sh` 스크립트가 `./mvnw.sh`를 사용하도록 수정 필요

## 수정된 파일들
1. ✅ `hot-reload.sh` - `./mvnw` → `./mvnw.sh`
2. ✅ `auto-deploy.sh` - `./mvnw` → `./mvnw.sh`
3. ✅ `setup-systemd.sh` - `./mvnw` → `./mvnw.sh`
4. ✅ `quick-start.sh` - `./mvnw` → `./mvnw.sh`
5. ✅ `docker-auto-deploy.sh` - `./mvnw` → `./mvnw.sh`

## 서버에 적용하는 방법

### 방법 1: Git으로 업데이트 (권장)
```bash
cd /opt/futures-dashboard/option_monitor
git pull origin main
chmod +x *.sh
chmod +x mvnw.sh
./hot-reload.sh
```

### 방법 2: 잘못된 mvnw 파일 삭제
서버에 `mvnw` 파일(확장자 없음)이 있다면 삭제:
```bash
cd /opt/futures-dashboard/option_monitor
rm -f mvnw  # Windows 배치 파일 삭제
ls -la mvnw*  # mvnw.sh만 있는지 확인
./hot-reload.sh
```

### 방법 3: 수동으로 파일 수정
서버의 각 `.sh` 파일에서 `./mvnw`를 모두 `./mvnw.sh`로 변경:
```bash
cd /opt/futures-dashboard/option_monitor
sed -i 's|./mvnw |./mvnw.sh |g' *.sh
./hot-reload.sh
```

## 확인 방법
```bash
# 올바른 파일 구조
ls -la mvnw*
# 결과:
# -rw-r--r-- 1 root root  6609 Dec 21 xx:xx mvnw.cmd   (Windows용)
# -rwxr-xr-x 1 root root 10070 Dec 21 xx:xx mvnw.sh    (Linux용)

# mvnw.sh 실행 권한 확인
./mvnw.sh --version
```

## 작업 완료일
- 2025년 12월 21일

## 참고
- Linux/Mac에서는 항상 `mvnw.sh` 사용
- Windows에서는 `mvnw.cmd` 사용
- Git을 통해 배포하면 올바른 파일이 자동으로 업로드됨
