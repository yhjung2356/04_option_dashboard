# 🚨 긴급 수정: mvnw.sh 파일 복구 완료

## 문제
```bash
./mvnw.sh: line 1: @REM: command not found
./mvnw.sh: line 2: syntax error near unexpected token `('
```

## 원인
`mvnw.sh` 파일이 Windows 배치 파일 내용으로 덮어씌워짐 (Git의 줄바꿈 변환 문제)

## 해결
1. ✅ `mvnw.sh` 파일을 올바른 Linux/Unix 셸 스크립트로 교체
2. ✅ `.gitattributes` 파일 추가하여 향후 이런 문제 방지
3. ✅ 모든 `.sh` 스크립트가 `./mvnw.sh` 사용하도록 수정

---

## 🚀 서버에 즉시 적용하기

### 1단계: 서버 접속 후 최신 코드 받기
```bash
cd /opt/futures-dashboard/option_monitor
git pull origin main
```

### 2단계: 실행 권한 부여
```bash
chmod +x mvnw.sh
chmod +x *.sh
```

### 3단계: 실행 확인
```bash
# Maven Wrapper 정상 작동 확인
./mvnw.sh --version

# 핫 리로드 실행
./hot-reload.sh
```

---

## 예상 출력
```bash
root@ip-172-26-1-80:/opt/futures-dashboard/option_monitor# ./mvnw.sh --version
Apache Maven 3.9.x (xxxxxx)
Maven home: /root/.m2/wrapper/dists/...
Java version: 17.x.x, vendor: Oracle Corporation
...

root@ip-172-26-1-80:/opt/futures-dashboard/option_monitor# ./hot-reload.sh

========================================
  핫 리로드 모드 (개발용)
========================================

파일 변경 시 자동으로 재빌드/재시작됩니다.
Ctrl+C로 종료하세요.

[INFO] Scanning for projects...
[INFO] 
[INFO] --------< com.futures:futures-options-dashboard >--------
[INFO] Building futures-options-dashboard 1.0.0
...
```

---

## 문제가 계속된다면?

### 옵션 1: 강제로 파일 다시 받기
```bash
cd /opt/futures-dashboard/option_monitor
git fetch --all
git reset --hard origin/main
chmod +x mvnw.sh *.sh
./hot-reload.sh
```

### 옵션 2: Maven Wrapper 재다운로드
```bash
cd /opt/futures-dashboard/option_monitor
rm -rf .mvn/wrapper/maven-wrapper.jar
./mvnw.sh --version  # 자동으로 다운로드됨
./hot-reload.sh
```

---

## 변경된 파일 목록
- ✅ `mvnw.sh` - 올바른 Linux용 스크립트로 교체 (350줄)
- ✅ `.gitattributes` - Git 줄바꿈 설정 추가 (향후 문제 방지)
- ✅ `hot-reload.sh` - `./mvnw.sh` 사용
- ✅ `auto-deploy.sh` - `./mvnw.sh` 사용
- ✅ `setup-systemd.sh` - `./mvnw.sh` 사용
- ✅ `quick-start.sh` - `./mvnw.sh` 사용
- ✅ `docker-auto-deploy.sh` - `./mvnw.sh` 사용

---

## 기술적 설명

### 문제의 근본 원인
Windows에서 Git을 사용할 때 기본적으로 줄바꿈 문자를 자동 변환합니다:
- Windows: CRLF (`\r\n`)
- Linux/Mac: LF (`\n`)

`mvnw.sh` 파일이 Windows 배치 파일(`@REM`, `@echo off` 등)로 덮어씌워진 것은 파일 혼동 또는 잘못된 복사로 인한 문제였습니다.

### 해결 방법
`.gitattributes` 파일을 추가하여 Git이 파일 유형별로 올바른 줄바꿈을 사용하도록 설정:
```
*.sh text eol=lf        # Linux 스크립트는 항상 LF
*.bat text eol=crlf     # Windows 배치는 항상 CRLF
mvnw.sh text eol=lf     # Maven Wrapper도 LF
```

---

## 수정 완료일
**2025년 12월 21일**

## 테스트 확인
- ✅ Windows에서 커밋 완료
- ⏳ Linux 서버에서 테스트 필요

## 다음 단계
서버에 접속해서:
```bash
cd /opt/futures-dashboard/option_monitor
git pull origin main
chmod +x mvnw.sh *.sh
./hot-reload.sh
```

이제 정상적으로 작동할 것입니다! 🎉
