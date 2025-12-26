# 📊 KOSPI200 옵션 실시간 대시보드

한국투자증권 KIS API를 활용한 **KOSPI200 선물/옵션 실시간 모니터링 대시보드**입니다.  
WebSocket을 통해 선물 가격, 옵션 체인, Greeks 지표를 실시간으로 추적하고 시각화합니다.

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen)
![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D)
![TypeScript](https://img.shields.io/badge/TypeScript-5.6-3178C6)

---

## ✨ 주요 기능

### 🔴 실시간 데이터 스트리밍
- **WebSocket 연결**: KIS API와 실시간 양방향 통신
- **40개 심볼 동시 구독**: 선물 1개 + 옵션 39개 (ATM 중심)
- **자동 재연결**: 연결 끊김 시 지수 백오프로 자동 복구
- **야간장 지원**: 정규장/야간장 TR_ID 자동 전환 (H0STCNT0/H0MFCNT0)

### 📈 옵션 체인 테이블
- **Call/Put 양방향 표시**: 행사가 중심으로 대칭 레이아웃
- **실시간 업데이트**: 가격, 거래량, 미결제약정 즉시 반영
- **색상 코딩**: ITM(In-The-Money) 행사가 강조 표시
- **Greeks 지표**: Delta, Gamma, Theta, Vega, Rho 실시간 계산

### 📊 시장 개요 (Market Overview)
- **선물 현재가**: A01603 (KOSPI200 근월물) 실시간 추적
- **거래량/미결제**: 당일 거래량 및 미결제약정 모니터링
- **가격 변동률**: 전일 대비 등락률 및 변동폭
- **시장 심리 게이지**: Fear & Greed Index 스타일 게이지

### 🎨 Greeks 요약 카드
- **Delta**: 기초자산 가격 민감도 (컬러풀 gradient 배경)
- **Gamma**: Delta 변화율
- **Theta**: 시간 가치 소멸
- **Vega**: 변동성 민감도
- **IV (Implied Volatility)**: 내재 변동성 지수

### ⚙️ 설정 페이지
- **API 연결 상태**: 실시간 KIS API 연결 확인
- **데이터 소스**: 현재 데이터 제공자 표시 (실시간 KIS API / 데모 데이터)
- **시스템 정보**: Spring Boot, Vue, WebSocket 버전 정보

---

## 🛠️ 기술 스택

### Backend
- **Spring Boot 3.4.1**: REST API 및 WebSocket 서버
- **Java 17**: 최신 LTS 버전
- **Spring Data JPA**: 데이터 영속성 관리
- **H2 Database**: 파일 기반 임베디드 데이터베이스
- **Java-WebSocket 1.5.3**: KIS API WebSocket 클라이언트
- **Lombok**: 보일러플레이트 코드 감소

### Frontend
- **Vue 3.5**: Composition API + `<script setup>`
- **TypeScript 5.6**: 정적 타입 검사
- **Vite 5**: 빠른 개발 서버 및 빌드 도구
- **Pinia**: Vue 3 공식 상태 관리
- **Tailwind CSS 3.4**: 유틸리티 기반 CSS 프레임워크
- **SockJS + STOMP**: 백엔드 WebSocket 통신
- **PWA (Progressive Web App)**: 오프라인 지원 및 앱 설치 가능

### API & External Services
- **한국투자증권 KIS API**: 실시간 시세 데이터 제공
  - REST API: 옵션 마스터 조회, 시장 개요
  - WebSocket API: 실시간 체결가, 호가 스트리밍

---

## 🚀 시작하기

### 📋 사전 요구사항

1. **Java 17 이상** 설치
   ```powershell
   java -version
   ```

2. **Node.js 18 이상** 설치
   ```powershell
   node -v
   npm -v
   ```

3. **한국투자증권 KIS API 계정**
   - [KIS Developers](https://apiportal.koreainvestment.com/) 회원가입
   - 앱 생성 후 **APP KEY**, **APP SECRET** 발급
   - 모의투자 신청 (실전투자 불필요)

---

### ⚙️ 환경 설정

#### 1. 프로젝트 클론
```bash
git clone https://github.com/yhjung2356/04_option_dashboard.git
cd 04_option_dashboard
```

#### 2. Backend 설정 (Spring Boot)

`src/main/resources/application.properties` 파일 수정:

```properties
# KIS API 인증 정보 (필수!)
kis.app-key=YOUR_APP_KEY_HERE
kis.app-secret=YOUR_APP_SECRET_HERE
kis.account-number=YOUR_ACCOUNT_NUMBER

# WebSocket 설정
kis.websocket.url=wss://ops.koreainvestment.com:21000
kis.websocket.approval-key=YOUR_APPROVAL_KEY

# 데이터 소스 (KIS = 실시간, MOCK = 데모)
trading.data-source=KIS
trading.demo-mode=false

# 데이터베이스 (H2 file-based)
spring.datasource.url=jdbc:h2:file:./data/optiondb
spring.jpa.hibernate.ddl-auto=update

# 로깅
logging.level.com.trading.dashboard.service.KisWebSocketService=INFO
```

#### 3. Frontend 설정

`frontend/` 디렉토리에서 의존성 설치:

```bash
cd frontend
npm install
```

---

### ▶️ 실행 방법

#### 개발 모드 (Development)

**Terminal 1: Backend 실행**
```powershell
# 프로젝트 루트에서
.\mvnw.cmd spring-boot:run
```
→ Spring Boot 서버: `http://localhost:8080`

**Terminal 2: Frontend 실행**
```powershell
cd frontend
npm run dev
```
→ Vite 개발 서버: `http://localhost:5173`

브라우저에서 `http://localhost:5173` 접속!

---

#### 프로덕션 빌드 (Production)

**1. Frontend 빌드**
```powershell
cd frontend
npm run build
```
→ `frontend/dist/` 디렉토리에 빌드 파일 생성

**2. Backend에 통합**
```powershell
# dist 폴더를 Spring Boot static 디렉토리로 복사
Copy-Item -Recurse -Force frontend/dist/* src/main/resources/static/
```

**3. JAR 파일 생성**
```powershell
.\mvnw.cmd clean package -DskipTests
```

**4. 실행**
```powershell
java -jar target/dashboard-0.0.1-SNAPSHOT.jar
```
→ 단일 JAR로 실행: `http://localhost:8080`

---

## 📁 프로젝트 구조

```
04_option_monitor/
├── src/main/java/com/trading/dashboard/     # Spring Boot 백엔드
│   ├── config/                               # WebSocket, CORS 설정
│   ├── controller/                           # REST API 엔드포인트
│   ├── dto/                                  # 데이터 전송 객체
│   ├── model/                                # JPA 엔티티 (FuturesData, OptionData)
│   ├── repository/                           # Spring Data JPA 리포지토리
│   ├── service/                              # 비즈니스 로직
│   │   ├── KisApiService.java               # KIS REST API 클라이언트
│   │   ├── KisWebSocketService.java         # KIS WebSocket 클라이언트
│   │   └── MarketDataService.java           # 데이터 집계 및 가공
│   └── websocket/                            # STOMP WebSocket 핸들러
├── src/main/resources/
│   ├── application.properties                # Spring Boot 설정 파일
│   └── static/                               # 빌드된 프론트엔드 (프로덕션)
├── frontend/                                 # Vue 3 프론트엔드
│   ├── src/
│   │   ├── components/                       # Vue 컴포넌트
│   │   │   ├── charts/                       # 차트 컴포넌트 (게이지)
│   │   │   ├── dashboard/                    # 대시보드 컴포넌트
│   │   │   └── layout/                       # 헤더, 사이드바
│   │   ├── stores/                           # Pinia 상태 관리
│   │   │   ├── market.ts                     # 시장 데이터 스토어
│   │   │   ├── option.ts                     # 옵션 체인 스토어
│   │   │   └── websocket.ts                  # WebSocket 연결 스토어
│   │   ├── types/                            # TypeScript 타입 정의
│   │   ├── router/                           # Vue Router
│   │   └── views/                            # 페이지 뷰
│   ├── public/                               # 정적 파일 (PWA 아이콘)
│   ├── vite.config.ts                        # Vite 빌드 설정
│   └── package.json                          # NPM 의존성
├── data/                                     # H2 데이터베이스 파일
├── pom.xml                                   # Maven 의존성
├── TODO.md                                   # 개발 계획 및 완료 작업
└── README.md                                 # 이 파일!
```

---

## 🔌 API 엔드포인트

### REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/market/overview` | 시장 개요 데이터 (선물 가격, 거래량, Greeks 요약) |
| GET | `/api/market/option-chain` | 옵션 체인 전체 데이터 (Call/Put) |

### WebSocket (STOMP)

| Destination | Description |
|-------------|-------------|
| `/topic/futures` | 선물 실시간 체결가 스트림 |
| `/topic/options` | 옵션 실시간 체결가 스트림 (39개 심볼) |

**연결 URL**: `http://localhost:8080/ws` (SockJS)

---

## 🎯 최근 업데이트 (2025-12-26)

### ✅ 완료된 작업

1. **실시간 업데이트 완전 수정** 🎉
   - Pipe-delimited 데이터 파서 구현 (`0|TR_ID|001|DATA^...`)
   - 야간장 TR_ID 지원 (H0MFCNT0, H0EUCNT0)
   - 선물/옵션 거래량 실시간 업데이트 검증 완료

2. **데이터베이스 무결성 개선** 🛡️
   - `FuturesData.symbol`, `OptionData.symbol`에 UNIQUE 제약조건 추가
   - NonUniqueResultException 오류 완전 해결

3. **로그 정리 및 최적화** 🧹
   - 백엔드: DEBUG → INFO 레벨 변경
   - 프론트엔드: console.log 대부분 주석 처리 (에러만 유지)

4. **UI/UX 개선** 🎨
   - 사이드바 거래량 차트 제거 (불필요)
   - Greeks 카드 디자인 대폭 개선 (컬러풀한 gradient 배경)
   - 시장 심리 게이지 크기 확대 및 색상 변경

5. **API 경로 통일** 🔧
   - 프론트엔드/백엔드 API 경로 일치 (`/api/market/*`)
   - 404 Not Found 오류 완전 해결

6. **설정 페이지 정확도 개선** ⚙️
   - `MarketOverviewDTO`에 `dataSource` 필드 추가
   - "실시간 KIS API" 정확하게 표시

---

## 📝 향후 계획

### 🔴 즉시 처리 (High Priority)
- [ ] Greeks 값 검증 (일부 0.000으로 표시되는 문제)
- [ ] WebSocket 연결 상태 인디케이터 추가
- [ ] 데이터 새로고침 버튼 및 마지막 업데이트 시간 표시

### 🟡 단기 개선 (1~2주)
- [ ] 실시간 가격 차트 (TradingView Lightweight Charts)
- [ ] 호가창 (Order Book) 10호가 실시간 표시
- [ ] 알림 기능 (가격/거래량/IV 알림)
- [ ] 데이터 테이블 필터링 (ITM/ATM/OTM, 거래량 임계값)
- [ ] 키보드 단축키 (↑↓ 이동, F5 새로고침, Esc 닫기)

### 🟢 장기 개선 (1개월+)
- [ ] IV Skew 차트 (행사가별 IV 분포)
- [ ] Greeks 시간별 변화 추이 그래프
- [ ] 가상 포트폴리오 (로컬 저장)
- [ ] 손익 계산기
- [ ] Max Pain 계산 및 시각화

자세한 내용은 [TODO.md](./TODO.md) 참고!

---

## 🐛 알려진 문제 (Known Issues)

1. **Greeks 값 일부 0.000 표시**
   - 원인: KIS API 응답 필드명 불일치 가능성 (`gama` vs `gamma`)
   - 해결 예정: 필드명 재매핑 및 데이터 검증

2. **WebSocket 연결 상태 표시 없음**
   - 현재 연결 끊김 시 사용자에게 알림 없음
   - 해결 예정: 헤더에 연결 상태 인디케이터 추가

---

## 🤝 기여 (Contributing)

버그 리포트, 기능 제안, PR 모두 환영합니다!

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'feat: Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 라이센스

This project is licensed under the MIT License.

---

## 📧 문의

프로젝트 관련 문의: [GitHub Issues](https://github.com/yhjung2356/04_option_dashboard/issues)

---

## 🙏 감사의 말

- **한국투자증권**: KIS API 제공
- **Spring Boot Team**: 강력한 백엔드 프레임워크
- **Vue.js Team**: 직관적인 프론트엔드 프레임워크
- **Tailwind CSS**: 아름다운 UI 스타일링

---

**⚠️ 면책 조항**: 이 프로젝트는 교육 및 개발 목적으로만 사용하세요. 실제 투자 결정에 활용 시 발생하는 손실에 대해 책임지지 않습니다.

---

**Made with ❤️ by [yhjung2356](https://github.com/yhjung2356)**
