# 📦 AWS 배포 준비 완료!

## 📅 2025년 12월 21일

## ✅ 생성된 파일

### 1. 배포 가이드
- ✅ `AWS배포가이드.md` - 상세한 배포 가이드 (전체)
- ✅ `AWS배포_빠른시작.md` - 빠른 배포 가이드 (요약)

### 2. 설정 파일
- ✅ `application-prod.properties` - 운영 환경 설정
- ✅ `Dockerfile` - Docker 이미지 빌드
- ✅ `docker-compose.yml` - Docker Compose 설정
- ✅ `.dockerignore` - Docker 빌드 제외 파일

### 3. 빌드 스크립트
- ✅ `build-for-aws.cmd` - Windows용 자동 빌드 스크립트

---

## 🚀 빠른 배포 방법

### 방법 1: 직접 배포 (추천)

#### Step 1: 빌드
```cmd
cd D:\Workspace\Spring\futures-options-dashboard
build-for-aws.cmd
```

**또는 직접 실행:**
```cmd
mvnw.cmd clean package -DskipTests
```

**결과:**
```
target\futures-options-dashboard-1.0.0.jar
크기: 약 60-80MB
```

#### Step 2: EC2 설정
1. AWS Console → EC2 → 인스턴스 시작
2. AMI: Amazon Linux 2023
3. 타입: t2.micro (프리티어) 또는 t3.small
4. 보안 그룹:
   - SSH (22) - 내 IP
   - Custom TCP (8080) - 0.0.0.0/0

#### Step 3: 업로드
```powershell
scp -i "키.pem" target\futures-options-dashboard-1.0.0.jar ec2-user@EC2-IP:/home/ec2-user/
```

#### Step 4: 실행
```bash
ssh -i "키.pem" ec2-user@EC2-IP

# Java 설치
sudo dnf install -y java-17-amazon-corretto-devel

# 실행
java -jar futures-options-dashboard-1.0.0.jar

# 또는 백그라운드
nohup java -jar futures-options-dashboard-1.0.0.jar > app.log 2>&1 &
```

#### Step 5: 접속
```
http://EC2-PUBLIC-IP:8080
```

---

### 방법 2: Docker 배포

#### 로컬에서 빌드
```bash
docker build -t trading-dashboard .
```

#### 실행
```bash
docker run -d -p 8080:8080 --restart unless-stopped trading-dashboard
```

---

## 📋 배포 체크리스트

### 사전 준비
- [ ] AWS 계정 준비
- [ ] 신용카드 등록 (프리티어도 필요)
- [ ] PEM 키 다운로드 위치 확인

### 빌드
- [ ] Java 17 설치 확인: `java -version`
- [ ] Maven 빌드: `build-for-aws.cmd`
- [ ] JAR 파일 확인: `target\futures-options-dashboard-1.0.0.jar`

### EC2 설정
- [ ] 인스턴스 생성 (t2.micro/t3.small)
- [ ] 보안 그룹 설정 (22, 8080)
- [ ] 탄력적 IP 할당 (선택)
- [ ] 키 페어 생성/선택

### 배포
- [ ] Java 17 설치
- [ ] JAR 파일 업로드
- [ ] 애플리케이션 실행
- [ ] 로그 확인

### 테스트
- [ ] 브라우저 접속: `http://IP:8080`
- [ ] 데이터 로딩 확인
- [ ] WebSocket 연결 확인
- [ ] Greeks 데이터 표시 확인

---

## 🎯 추천 배포 방식

### 소규모 (개인/테스트)
**EC2 t2.micro + 직접 배포**
- 비용: $0 (프리티어)
- 설정: 간단
- 관리: 쉬움

### 중규모 (팀/상용)
**EC2 t3.small + Systemd 서비스**
- 비용: ~$15/월
- 설정: 중간
- 관리: 자동 재시작

### 대규모 (기업)
**ECS + Docker + ALB**
- 비용: ~$50+/월
- 설정: 복잡
- 관리: 완전 자동화

---

## ⚙️ 운영 환경 설정

### application-prod.properties
```properties
# 로깅 레벨 낮춤
logging.level.root=WARN
logging.level.com.trading.dashboard=INFO

# H2 콘솔 비활성화 (보안)
spring.h2.console.enabled=false

# Thymeleaf 캐시 활성화 (성능)
spring.thymeleaf.cache=true

# 연결 풀 최적화
spring.datasource.hikari.maximum-pool-size=10
```

### JVM 옵션 (권장)
```bash
java -Xms512m -Xmx1024m -XX:+UseG1GC -jar app.jar
```

---

## 🔐 보안 권장사항

### 1. H2 콘솔 비활성화
```properties
spring.h2.console.enabled=false
```

### 2. SSH 키 관리
```bash
chmod 400 your-key.pem
```

### 3. 보안 그룹 제한
- SSH: 특정 IP만 허용
- 8080: 필요한 IP 범위만

### 4. 정기 업데이트
```bash
sudo dnf update -y
```

---

## 📊 모니터링

### 로그 확인
```bash
# 실시간 로그
sudo journalctl -u trading -f

# 최근 100줄
sudo journalctl -u trading -n 100
```

### 리소스 확인
```bash
# CPU/메모리
top

# 디스크
df -h

# 네트워크
sudo netstat -tulpn | grep 8080
```

---

## 🛠️ 자주 사용하는 명령어

### Systemd 서비스 관리
```bash
sudo systemctl status trading   # 상태
sudo systemctl start trading    # 시작
sudo systemctl stop trading     # 중지
sudo systemctl restart trading  # 재시작
sudo systemctl enable trading   # 부팅 시 자동 시작
```

### 프로세스 관리
```bash
ps aux | grep java              # Java 프로세스 확인
kill -9 [PID]                   # 강제 종료
```

### 포트 확인
```bash
sudo lsof -i :8080              # 8080 포트 사용 확인
sudo netstat -tulpn | grep 8080 # 네트워크 상태
```

---

## 💰 비용 절감 팁

### 1. 프리티어 최대 활용
- t2.micro: 월 750시간 무료
- 데이터 전송: 15GB 무료

### 2. 예약 인스턴스
- 1년 약정: 최대 40% 할인
- 3년 약정: 최대 60% 할인

### 3. 스팟 인스턴스
- 최대 90% 할인
- 단, 중단 가능성 있음

### 4. 자동 중지/시작
```bash
# Cron으로 야간 자동 중지
0 22 * * * sudo systemctl stop trading
0 9 * * * sudo systemctl start trading
```

---

## 🚨 트러블슈팅

### 1. 접속 안됨
```bash
# 방화벽 확인
sudo firewall-cmd --list-all

# 8080 포트 열기
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
```

### 2. OutOfMemoryError
```bash
# JVM 힙 크기 증가
java -Xms512m -Xmx1024m -jar app.jar
```

### 3. 포트 충돌
```bash
# 포트 사용 프로세스 확인
sudo lsof -i :8080
sudo kill -9 [PID]
```

### 4. 느린 응답
```bash
# 로그 확인
sudo journalctl -u trading -n 100

# 리소스 확인
top
free -h
```

---

## 📚 참고 문서

### 프로젝트 내
- `AWS배포가이드.md` - 전체 가이드
- `AWS배포_빠른시작.md` - 빠른 시작
- `application-prod.properties` - 운영 설정

### 외부 링크
- [AWS EC2 문서](https://docs.aws.amazon.com/ec2/)
- [Spring Boot 배포](https://spring.io/guides/gs/spring-boot/)
- [Systemd 서비스](https://www.freedesktop.org/software/systemd/man/systemd.service.html)

---

## 🎉 준비 완료!

이제 다음 명령어만 실행하면 됩니다:

```cmd
cd D:\Workspace\Spring\futures-options-dashboard
build-for-aws.cmd
```

빌드 완료 후 `AWS배포_빠른시작.md`를 참고하여 EC2에 배포하세요! 🚀

---

## 📞 다음 단계

1. **지금 바로 배포**: `build-for-aws.cmd` 실행
2. **상세 가이드 확인**: `AWS배포가이드.md` 읽기
3. **빠른 배포**: `AWS배포_빠른시작.md` 따라하기

**예상 소요 시간:**
- 빌드: 2-3분
- EC2 설정: 5-10분
- 배포: 5분
- **총: 15-20분** ⏱️
