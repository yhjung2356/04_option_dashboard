# 📈 선물/옵션 실시간 거래 대시보드

한국투자증권 KIS API를 활용한 선물/옵션 실시간 거래 데이터 대시보드

---

## 🎉 최신 업데이트 (2025-12-21)

### 🔧 Linux 배포 환경 수정 (중요!)
- **mvnw.sh 복구**: 올바른 Linux용 Maven Wrapper로 교체
- **.gitattributes 추가**: Git 줄바꿈 설정으로 향후 문제 방지
- **모든 .sh 스크립트 수정**: `./mvnw.sh` 사용으로 통일
- 📖 **상세 가이드**: `docs/긴급수정_mvnw_복구완료.md` 참고

### ✅ 투자자 안내문 기능 추가
- **느낌표 버튼**: 헤더에 투자자 안내문 버튼 추가 (주황색 느낌표 아이콘)
- **팝업 모달**: 투자 위험 고지 및 주요 유의사항 제공
- **고위험 상품 경고**: 선물/옵션 거래의 위험성 명확히 안내

### ✅ 시장 상태 표시 개선
- **장 마감 통합**: "주말 휴장"을 "장 마감"으로 통합하여 중복 제거
- **심플한 인터페이스**: 더 깔끔한 시장 상태 표시

### ✅ 프로젝트 구조 정리
- **docs/**: 모든 문서 파일 정리
- **docs/archive/**: 과거 개발 이력 문서 보관
- **test-data/**: 테스트용 JSON/CSV 파일 분리

### ✅ 동적 종목코드 및 전거래일 계산 (2025-12-20)
- **전거래일 자동 계산**: 주말, 공휴일, 설날/추석 연휴 자동 고려
- **동적 종목코드 조회**: 한국투자증권 API를 통한 실시간 종목코드 조회
- **월물 자동 전환**: 현재 월 기준으로 거래 가능한 월물 자동 선택
- **하드코딩 제거**: 유지보수성 및 정확성 대폭 향상

---

## ✨ 주요 기능

### 📊 실시간 시장 데이터
- **선물 전체**: 거래량, 거래대금, 미결제약정
- **옵션 전체**: 거래량, 거래대금, 미결제약정
- **Put/Call Ratio**: 거래량, 미결제, 거래대금 기준 3가지 비율
- **시장 심리 지표**: IV 지수 및 투자 심리 게이지
- **Greeks 요약**: Delta, Gamma, Theta, Vega, IV (ATM 기준)

### 🔥 상위 종목 분석
- 거래량 상위 5개 종목
- 미결제약정 상위 5개 종목
- 실시간 가격 및 거래 정보

### 📋 옵션 체인 분석
- 기초자산 가격
- ATM(At The Money) 행사가
- Max Pain 가격
- Call/Put 옵션 전체 체인 with Greeks

### ⚠️ 투자자 안내문
- **느낌표 버튼 클릭**: 투자 위험 고지 및 유의사항 확인
- **고위험 상품 경고**: 선물/옵션의 위험성 명확히 안내
- **책임 명시**: 투자 책임 및 권장사항 제공

### ⚡ 실시간 반영 (Hot Reload) ⭐NEW⭐
- **핫 리로드**: 파일 저장하면 즉시 반영 (5초!)
- **자동 배포 스크립트**: Git Pull → 빌드 → 재시작 (한 번에!)
- **GitHub Actions**: Git Push 시 자동 빌드/배포
- **Docker 자동 배포**: 컨테이너 기반 일관된 배포

---

## 🚀 빠른 시작

### Windows
```bash
# Java 17 이상 필요
java -version

# Maven 설치 확인
mvn -version
```

### AWS Ubuntu / Linux
```bash
# EC2 접속
ssh -i your-key.pem ubuntu@your-ec2-ip

# 한 줄로 설치 및 실행
sudo mkdir -p /opt/futures-dashboard && \
sudo chown -R ubuntu:ubuntu /opt/futures-dashboard && \
cd /opt/futures-dashboard && \
git clone <your-repo-url> . && \
chmod +x *.sh && \
./quick-start.sh
```

**자세한 내용:** `docs/AWS_Ubuntu_빠른배포.md` 참고

---

## 🔧 환경별 설정

### 1. Windows 환경
`src/main/resources/application.properties` 파일 생성:
```properties
# 한국투자증권 KIS API 설정
kis.api.app-key=YOUR_APP_KEY
kis.api.app-secret=YOUR_APP_SECRET
kis.api.base-url=https://openapi.koreainvestment.com:9443

# 서버 설정
server.port=8080
```

### 2. Linux / AWS Ubuntu 환경
```bash
# application.properties 편집
nano src/main/resources/application.properties

# 또는 환경 변수로 설정
export KIS_API_APP_KEY=YOUR_APP_KEY
export KIS_API_APP_SECRET=YOUR_APP_SECRET
```

---

## 🎮 실행 방법

### Windows
```bash
# 방법 1: 핫 리로드 (개발용)
hot-reload.bat

# 방법 2: 자동 배포
auto-deploy.bat

# 방법 3: 직접 실행
mvnw clean package
java -jar target\futures-options-dashboard-1.0.0.jar
```

### Linux / AWS Ubuntu
```bash
# 방법 1: 빠른 시작 (대화형)
./quick-start.sh

# 방법 2: 자동 배포
./auto-deploy.sh

# 방법 3: systemd 서비스 (프로덕션 추천!)
./setup-systemd.sh
sudo systemctl start futures-dashboard

# 방법 4: Docker
./docker-auto-deploy.sh
```

---

## 🌐 브라우저 접속
```
http://localhost:8080
```

---

## 🌐 브라우저 접속

### Windows (로컬)
```
http://localhost:8080
```

### AWS Ubuntu (원격)
```
http://your-ec2-ip:8080
http://your-domain.com:8080  (도메인 설정 시)
```

**주의:** AWS 보안 그룹에서 포트 8080을 열어야 합니다!

---

## ⚡ 실시간 반영 (개발용)

### Windows - 핫 리로드 ⭐
```bash
# 파일 변경 시 자동 재시작
hot-reload.bat
```

### Linux - 핫 리로드 ⭐
```bash
# 파일 변경 시 자동 재시작
./hot-reload.sh
```

**사용법:**
1. `hot-reload.bat` 실행 (한 번만)
2. 파일 수정 (Java, HTML, CSS, JS)
3. **Ctrl+S** 저장
4. **F5** 새로고침
5. ✅ **즉시 반영!** (5초)

### 자동 배포
```bash
# Git Pull → 빌드 → 재시작 (한 번에!)
auto-deploy.bat

# Docker 사용 시
docker-auto-deploy.bat
```

### GitHub Actions (CI/CD)
```bash
# 코드 완성 후
git add .
git commit -m "기능 추가"
git push origin main

# GitHub Actions가 자동으로:
# 1. 빌드
# 2. 테스트
# 3. Docker 이미지 생성
# 4. (선택) AWS 배포
```

**자세한 내용:** `docs/실시간반영가이드.md` 참고

---

## 📸 페이지 공유 사용법

### 가장 빠른 방법 (추천!)
```
1. Ctrl + Shift + C
2. 원하는 곳에 Ctrl + V
3. 완료! 🎉
```

### 버튼 사용
화면 상단에 4개의 파란색 버튼:
- 📋 클립보드 복사
- 💾 텍스트 파일 저장
- 📄 JSON 파일 저장
- 💻 콘솔 출력

### 출력 형식 예시
```
╔════════════════════════════════════════════════════════════════╗
║         선물/옵션 실시간 거래 대시보드 스냅샷                 ║
╚════════════════════════════════════════════════════════════════╝

📅 캡처 시간: 2025-12-20 15:30:45
📊 시장 상태: 주간장 거래중
💾 데이터 소스: KIS
🔌 연결 상태: ✅ 연결됨

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🚀 선물 전체
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   거래량:     1,234,567
   거래대금:   ₩12,345,678,900
   미결제약정: 987,654
...
```

자세한 사용법: [`페이지공유_빠른사용법.md`](페이지공유_빠른사용법.md)

---

## 🛠️ 기술 스택

### Backend
- **Spring Boot 3.2.5**
- **WebSocket (STOMP)** - 실시간 데이터 전송
- **RestTemplate** - KIS API 연동
- **Lombok** - 보일러플레이트 코드 감소

### Frontend
- **Vanilla JavaScript** - 의존성 없는 순수 JS
- **SockJS + STOMP.js** - WebSocket 클라이언트
- **CSS3** - 그라디언트, 애니메이션, 반응형
- **Font Awesome** - 아이콘

### API
- **한국투자증권 KIS API**
- **WebSocket 실시간 시세**

---

## 📁 프로젝트 구조

```
futures-options-dashboard/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/trading/dashboard/
│   │   │       ├── DashboardApplication.java
│   │   │       ├── config/           # 설정
│   │   │       │   ├── KisApiConfig.java
│   │   │       │   └── WebSocketConfig.java
│   │   │       ├── controller/       # REST API
│   │   │       │   ├── DashboardController.java
│   │   │       │   └── MarketDataController.java
│   │   │       ├── dto/              # 데이터 전송 객체
│   │   │       ├── model/            # 데이터 모델
│   │   │       ├── repository/       # 데이터 액세스
│   │   │       ├── service/          # 비즈니스 로직
│   │   │       └── websocket/        # WebSocket 핸들러
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── static/
│   │       │   ├── css/
│   │       │   │   └── dashboard.css
│   │       │   └── js/
│   │       │       └── dashboard.js
│   │       └── templates/
│   │           └── dashboard.html
│   └── test/                          # 테스트
├── 문서/
│   ├── 페이지공유_빠른사용법.md       # 사용자용 빠른 가이드
│   ├── 페이지공유_사용예시.md         # 실제 사용 시나리오
│   ├── 페이지공유방법.md              # 상세 사용법
│   ├── 페이지공유기능_완료.md         # 완료 요약
│   ├── 페이지상태전달가이드.md        # 기술 문서
│   ├── 한국투자증권API연동가이드.md
│   └── ...
├── pom.xml
└── README.md
```

---

## 🎯 사용 시나리오

### 1. 동료에게 빠르게 공유
```
동료: "지금 시장 상황 어때?"
나: Ctrl+Shift+C → 카톡 전송
동료: "오~ 완벽해!"
```

### 2. 일일 보고
```
매일 장 마감 시:
1. Ctrl+Shift+S
2. 파일명: "2025-12-20-마감리포트.txt"
3. 이메일 첨부
```

### 3. 데이터 분석
```
1. Ctrl+Shift+J (JSON 다운로드)
2. Python/Excel로 분석
3. 트렌드 파악
```

### 4. 문제 보고
```
1. Windows+Shift+S (스크린샷)
2. Ctrl+Shift+C (데이터 복사)
3. 개발자에게 전송
```

---

## ⚙️ 설정

### application.properties
```properties
# KIS API 설정
kis.api.app-key=YOUR_APP_KEY
kis.api.app-secret=YOUR_APP_SECRET
kis.api.base-url=https://openapi.koreainvestment.com:9443

# 서버 설정
server.port=8080

# WebSocket 설정
spring.websocket.allowed-origins=*

# 로깅
logging.level.com.trading.dashboard=DEBUG
```

---

## 🐛 문제 해결

### Q: 버튼이 안 보여요
**A:** 브라우저에서 `F5` 키로 새로고침

### Q: 클립보드 복사가 안돼요
**A:** 
1. 버튼 클릭 시도 (자동 폴백)
2. HTTPS 접속 확인
3. 브라우저 클립보드 권한 확인

### Q: WebSocket 연결 실패
**A:**
1. 방화벽 설정 확인
2. 포트 8080 사용 가능 확인
3. 브라우저 콘솔에서 에러 메시지 확인

### Q: KIS API 인증 실패
**A:**
1. APP_KEY, APP_SECRET 확인
2. API 사용 권한 확인
3. API 호출 제한 확인

---

## 📚 문서

- [빠른 사용법](페이지공유_빠른사용법.md) - 페이지 공유 기능 사용법
- [사용 예시](페이지공유_사용예시.md) - 실제 시나리오별 예시
- [상세 가이드](페이지공유방법.md) - 모든 기능 상세 설명
- [기술 문서](페이지상태전달가이드.md) - 개발자용 기술 가이드
- [API 연동 가이드](한국투자증권API연동가이드.md) - KIS API 사용법

---

## 🎨 스크린샷

### 메인 대시보드
```
╔═══════════════════════════════════════════════════════════╗
║  📈 선물/옵션 실시간 거래 대시보드    [📋][💾][📄][💻]  ║
╠═══════════════════════════════════════════════════════════╣
║                                                            ║
║  🚀 선물 전체      📊 옵션 전체      ⚖️ Put/Call Ratio  ║
║  거래량: 1.2M      거래량: 5.6M      거래량 R: 1.23      ║
║  거래대금: 12.3B   거래대금: 56.7B   미결제 R: 0.98      ║
║  미결제: 987K      미결제: 3.4M      거래대금 R: 1.15    ║
║                                                            ║
╠═══════════════════════════════════════════════════════════╣
║  🔥 거래대금 상위 종목             📈 거래량 상위 종목  ║
║  ...                                ...                   ║
╠═══════════════════════════════════════════════════════════╣
║  📋 옵션 체인 분석                                        ║
║  ...                                                      ║
╚═══════════════════════════════════════════════════════════╝
```

---

## 🤝 기여

프로젝트 개선을 위한 제안이나 버그 리포트는 언제든 환영합니다!

---

## 📄 라이선스

이 프로젝트는 MIT 라이선스를 따릅니다.

---

## 👤 작성자

**트레이딩 대시보드 팀**

---

## 📞 지원

문제가 발생하면 다음을 확인하세요:
1. [문제해결완료.md](문제해결완료.md) - 자주 발생하는 문제들
2. [한투API문제해결완료.md](한투API문제해결완료.md) - API 관련 문제
3. [페이지공유_빠른사용법.md](페이지공유_빠른사용법.md) - 페이지 공유 기능

---

## 🎉 버전 히스토리

### v1.1.0 (2025-12-20)
- ✨ 페이지 공유 기능 추가
  - 클립보드 복사 (Ctrl+Shift+C)
  - 텍스트 파일 저장 (Ctrl+Shift+S)
  - JSON 파일 저장 (Ctrl+Shift+J)
  - 콘솔 출력
- 🎨 UI 개선
  - 헤더에 공유 버튼 추가
  - 알림 시스템 구현
  - 반응형 디자인 개선
- 📚 문서 추가
  - 사용자용 빠른 가이드
  - 실제 사용 예시
  - 기술 문서

### v1.0.0 (2025-12-19)
- 🎉 초기 릴리스
- 📊 실시간 선물/옵션 데이터 표시
- 🔥 상위 종목 분석
- 📋 옵션 체인 분석
- 🔌 WebSocket 실시간 업데이트

---

**즐거운 트레이딩 되세요! 📈**
