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

## 🎯 최근 업데이트 (2025-12-28)

### ✅ 완료된 작업

#### Phase 1-5: 핵심 기능 구현 (2025-12-20 ~ 2025-12-28)

1. **실시간 업데이트 완전 수정** 🎉
   - Pipe-delimited 데이터 파서 구현 (`0|TR_ID|001|DATA^...`)
   - 야간장 TR_ID 지원 (H0MFCNT0, H0EUCNT0)
   - 선물/옵션 거래량 실시간 업데이트 검증 완료
   - 자동 재연결 로직 (지수 백오프)

2. **데이터베이스 무결성 개선** 🛡️
   - `FuturesData.symbol`, `OptionData.symbol`에 UNIQUE 제약조건 추가
   - NonUniqueResultException 오류 완전 해결
   - JPA Repository 계층 안정화

3. **로그 정리 및 최적화** 🧹
   - 백엔드: DEBUG → INFO 레벨 변경
   - 프론트엔드: console.log 대부분 주석 처리 (에러만 유지)
   - 불필요한 WebSocket 메시지 로깅 제거

4. **UI/UX 개선** 🎨
   - 사이드바 거래량 차트 제거 (불필요)
   - Greeks 카드 디자인 대폭 개선 (컬러풀한 gradient 배경)
   - 시장 심리 게이지 크기 확대 및 색상 변경
   - 다크 모드 지원 완료
   - 반응형 디자인 (모바일/태블릿/데스크톱)

5. **API 경로 통일** 🔧
   - 프론트엔드/백엔드 API 경로 일치 (`/api/market/*`)
   - 404 Not Found 오류 완전 해결
   - CORS 설정 최적화

6. **설정 페이지 정확도 개선** ⚙️
   - `MarketOverviewDTO`에 `dataSource` 필드 추가
   - "실시간 KIS API" 정확하게 표시
   - 시스템 정보 표시 (Spring Boot, Vue, WebSocket 버전)

7. **PWA 지원** 📱
   - Progressive Web App 설정 완료
   - 오프라인 지원 및 앱 설치 가능
   - Service Worker 등록

### 📊 프로젝트 진행률

```
███████████████████░░░░░░  70% (Phase 1~5 완료)
```

---

## 📝 향후 계획

> 자세한 내용은 [TODO.md](./TODO.md) 참고!

### 🔴 즉시 처리 (1~3일 내) - High Priority

**안정성 및 신뢰성 개선**
- [ ] **Greeks 값 0.000 문제 해결** ⭐⭐⭐⭐⭐
  - KIS API 필드명 재매핑 (`gama` vs `gamma`)
  - 데이터 검증 로직 추가
- [ ] **WebSocket 연결 상태 인디케이터** ⭐⭐⭐⭐⭐
  - 헤더에 실시간 연결 상태 표시 (🟢 연결됨 / 🔴 끊김)
  - 재연결 시도 중 표시 및 토스트 알림
- [ ] **데이터 새로고침 기능** ⭐⭐⭐⭐
  - 새로고침 버튼 + F5 단축키
  - 마지막 업데이트 시간 표시
  - 로딩 스피너 추가

**에러 처리**
- [ ] **전역 에러 바운더리** ⭐⭐⭐⭐
  - Vue errorHandler 설정
  - 사용자 친화적인 에러 메시지
- [ ] **API 타임아웃 처리** ⭐⭐⭐
  - 30초 타임아웃 + 자동 재시도 (최대 3회)

### 🟡 단기 개선 (1~2주 내) - Medium Priority

**실시간 차트 및 시각화**
- [ ] **실시간 가격 차트** ⭐⭐⭐⭐
  - TradingView Lightweight Charts 통합
  - 선물 1분/5분/15분 캔들 차트
  - 거래량 막대 그래프, Pan/Zoom 기능
- [ ] **호가창 (Order Book)** ⭐⭐⭐⭐
  - 10호가 실시간 표시 (매수/매도)
  - 호가 잔량 시각화 (바 차트)
- [ ] **IV Skew 차트** ⭐⭐⭐
  - 행사가별 내재 변동성 분포
  - Call/Put IV Skew 비교

**알림 시스템**
- [ ] **가격 알림** ⭐⭐⭐⭐
  - 사용자 정의 가격 알림 설정
  - 브라우저 Push 알림
  - 거래량 급증 알림, IV 급변 알림

**데이터 테이블**
- [ ] **고급 필터링** ⭐⭐⭐
  - ITM/ATM/OTM 필터
  - 거래량/Delta/Gamma/IV 범위 필터
- [ ] **키보드 단축키** ⭐⭐⭐
  - ↑↓ 행 이동, ←→ Call/Put 전환
  - F5 새로고침, Esc 닫기, Space 선택

### 🟢 장기 개선 (1개월+) - Low Priority

**고급 분석 도구**
- [ ] **Greeks 시간별 변화 추이** ⭐⭐⭐
  - Delta/Gamma/Theta/Vega 히트맵
  - 일중 Greeks 변화 차트
- [ ] **Max Pain 계산** ⭐⭐⭐
  - 행사가별 미결제약정 분석
  - Max Pain 지점 표시
- [ ] **변동성 표면** ⭐⭐
  - 3D 변동성 표면 차트
  - 만기별/행사가별 IV 분포

**포트폴리오 관리**
- [ ] **가상 포트폴리오** ⭐⭐⭐
  - 로컬 저장소에 포지션 저장
  - 실시간 손익 계산
  - 포지션별 Greeks 합산
- [ ] **손익 계산기** ⭐⭐⭐
  - 옵션 전략 시뮬레이터 (Bull/Bear Spread, Straddle 등)
  - 손익 다이어그램 (Payoff Diagram)
- [ ] **백테스팅 엔진** ⭐⭐
  - 과거 데이터 기반 전략 검증

**사용자 경험**
- [ ] **대시보드 커스터마이징** ⭐⭐
  - 드래그 앤 드롭 레이아웃 편집
  - 위젯 추가/제거
- [ ] **다국어 지원** ⭐
  - 한국어, 영어, 일본어

**백엔드 개선**
- [ ] **Redis 캐싱** ⭐⭐
  - 토큰 캐싱, 옵션 마스터 데이터 캐싱
- [ ] **PostgreSQL 마이그레이션** ⭐⭐
  - H2 → PostgreSQL 전환
  - 과거 데이터 장기 저장
- [ ] **API Rate Limiting** ⭐⭐⭐
  - KIS API 호출 제한 준수

**배포 및 운영**
- [ ] **Docker 컨테이너화** ⭐⭐⭐
  - Dockerfile + Docker Compose
- [ ] **CI/CD 파이프라인** ⭐⭐
  - GitHub Actions 워크플로우
- [ ] **클라우드 배포** ⭐⭐
  - AWS EC2/Lightsail

### 📅 다음 Sprint 목표 (2025-12-28 ~ 2026-01-03)

**Sprint 1: 안정성 및 신뢰성 개선**
1. Greeks 값 0.000 문제 해결
2. WebSocket 연결 상태 인디케이터 추가
3. 데이터 새로고침 기능 구현
4. 전역 에러 바운더리 설정
5. API 타임아웃 처리 개선

**예상 소요 시간**: 40시간  
**목표 완료일**: 2026-01-03

---

## 🐛 알려진 문제 (Known Issues)

### 긴급 (Critical)
1. **Greeks 값 일부 0.000 표시**
   - **원인**: KIS API 응답 필드명 불일치 가능성 (`gama` vs `gamma`)
   - **영향**: Delta, Gamma 등이 0으로 표시되어 정확한 리스크 분석 불가
   - **해결 예정**: 2026-01-03까지 필드명 재매핑 및 데이터 검증 로직 추가

### 중요 (High)
2. **WebSocket 연결 상태 표시 없음**
   - **현상**: 연결 끊김 시 사용자에게 알림 없음
   - **영향**: 데이터 업데이트 중단을 인지하지 못할 수 있음
   - **해결 예정**: 헤더에 연결 상태 인디케이터 추가

### 보통 (Medium)
3. **옵션 체인 테이블 스크롤 성능**
   - **현상**: 100개 이상 행 렌더링 시 스크롤 버벅임
   - **해결 예정**: 가상 스크롤 도입으로 성능 개선

4. **모바일 사이드바 오버레이 애니메이션**
   - **현상**: 사이드바 열림/닫힘 시 애니메이션 부자연스러움
   - **해결 예정**: CSS transition 개선

### 낮음 (Low)
5. **다크 모드 일부 컴포넌트 색상 불일치**
   - **현상**: 특정 버튼/아이콘이 다크 모드에서 가독성 낮음
   - **해결 예정**: Tailwind 색상 변수 통일

---

## 📊 성능 지표

### 현재 성능
- 옵션 체인 테이블 렌더링: ~150ms (목표: <100ms)
- WebSocket 메시지 처리: ~5ms ✅
- REST API 응답 시간: ~300ms ✅
- 페이지 로드 시간: ~1.5초 ✅

### 코드 품질
- TypeScript 커버리지: 95% ✅
- 테스트 커버리지: 45% (목표: >80%)
- ESLint 경고: 0 ✅
- Lighthouse 점수: 88 (목표: >90)

---

## 🤝 기여 (Contributing)

버그 리포트, 기능 제안, PR 모두 환영합니다!

### 기여 방법
1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'feat: Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### 개발 가이드라인
- **코드 스타일**: ESLint + Prettier 설정 준수
- **커밋 메시지**: Conventional Commits 규칙 (`feat:`, `fix:`, `docs:`, `style:`, `refactor:`, `test:`, `chore:`)
- **테스트**: 새 기능 추가 시 단위 테스트 작성 필수
- **문서화**: 주요 함수/클래스에 JSDoc/JavaDoc 주석 작성

### 이슈 리포트
버그를 발견하셨나요? [GitHub Issues](https://github.com/yhjung2356/04_option_dashboard/issues)에서 다음 정보와 함께 리포트해주세요:
- 재현 방법 (Steps to Reproduce)
- 예상 동작 (Expected Behavior)
- 실제 동작 (Actual Behavior)
- 스크린샷 (있다면)
- 환경 정보 (OS, 브라우저, Java/Node 버전)

### 기능 제안
새로운 아이디어가 있으신가요? [GitHub Discussions](https://github.com/yhjung2356/04_option_dashboard/discussions)에서 공유해주세요!

---

## 📄 라이센스

This project is licensed under the MIT License.

---

## 📧 문의

- **프로젝트 이슈**: [GitHub Issues](https://github.com/yhjung2356/04_option_dashboard/issues)
- **기능 제안**: [GitHub Discussions](https://github.com/yhjung2356/04_option_dashboard/discussions)
- **이메일**: yhjung2356@gmail.com (문의 시 "[Option Dashboard]" 태그 포함)

---

## 🙏 감사의 말

- **한국투자증권**: [KIS API](https://apiportal.koreainvestment.com/) 제공
- **Spring Boot Team**: 강력한 백엔드 프레임워크
- **Vue.js Team**: 직관적인 프론트엔드 프레임워크
- **Tailwind CSS**: 아름다운 UI 스타일링
- **Open Source Community**: 수많은 오픈소스 라이브러리 기여자분들께 감사드립니다

---

## 📚 참고 자료

### KIS API 문서
- [KIS Developers Portal](https://apiportal.koreainvestment.com/)
- [WebSocket API 가이드](https://apiportal.koreainvestment.com/apiservice/websocket)
- [REST API 레퍼런스](https://apiportal.koreainvestment.com/apiservice/apiservice-domestic-stock)

### 기술 문서
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Vue 3 Documentation](https://vuejs.org/)
- [TypeScript Handbook](https://www.typescriptlang.org/docs/)
- [Tailwind CSS Docs](https://tailwindcss.com/docs)
- [Pinia Documentation](https://pinia.vuejs.org/)

### 옵션 트레이딩 참고
- [Options Pricing Models](https://en.wikipedia.org/wiki/Black%E2%80%93Scholes_model)
- [Greeks (finance)](https://en.wikipedia.org/wiki/Greeks_(finance))
- [Implied Volatility](https://www.investopedia.com/terms/i/iv.asp)

---

## 🔐 보안 및 면책 조항

### 보안
- **API 키 보호**: `application.properties`를 절대 공개 저장소에 커밋하지 마세요!
- **환경 변수 사용**: 프로덕션 환경에서는 환경 변수로 민감 정보 관리
- **HTTPS 필수**: 실제 배포 시 반드시 HTTPS 사용

### 면책 조항
⚠️ **중요**: 이 프로젝트는 **교육 및 개발 목적**으로만 사용하세요.

- 실제 투자 결정에 활용 시 발생하는 **금전적 손실**에 대해 책임지지 않습니다.
- 제공되는 데이터는 실시간이지만 **지연**이 있을 수 있습니다.
- 모든 투자 결정은 **본인의 책임**하에 이루어져야 합니다.
- 이 소프트웨어는 **AS-IS** 기준으로 제공되며, 어떠한 보증도 하지 않습니다.

---

## 📜 변경 로그 (Changelog)

### [0.9.0] - 2025-12-28
**Added**
- TODO.md 파일 추가 (상세 개발 계획 문서화)
- 프로젝트 진행률 추적 시스템
- 성능 지표 및 코드 품질 목표 설정

**Changed**
- README.md 대폭 업데이트 (향후 계획 상세화)
- 알려진 이슈 우선순위 분류
- 기여 가이드라인 추가

**Fixed**
- 문서 정확도 개선

### [0.8.0] - 2025-12-26
**Added**
- Greeks 카드 컬러풀한 gradient 배경
- PWA 지원 (Service Worker)
- 데이터 소스 표시 (`MarketOverviewDTO.dataSource`)

**Changed**
- 사이드바 거래량 차트 제거
- 로그 레벨 최적화 (DEBUG → INFO)

**Fixed**
- NonUniqueResultException 완전 해결
- API 경로 통일 (`/api/market/*`)
- 실시간 업데이트 파서 수정

### [0.7.0] - 2025-12-25
**Added**
- 야간장 TR_ID 지원 (H0MFCNT0, H0EUCNT0)
- 자동 재연결 로직 (지수 백오프)

**Fixed**
- Pipe-delimited 데이터 파싱 오류 수정
- WebSocket 연결 안정성 개선

### [0.6.0] - 2025-12-24
**Added**
- 옵션 체인 테이블 UI
- Greeks 요약 카드
- 시장 심리 게이지

### [0.5.0] - 2025-12-23
**Added**
- KIS WebSocket 실시간 시세 구독
- 토큰 관리 시스템 (`TokenManager`)

### [0.4.0] - 2025-12-22
**Added**
- KIS REST API 통합 (`KisApiService`)
- 옵션 마스터 데이터 조회

### [0.3.0] - 2025-12-21
**Added**
- Spring Data JPA + H2 데이터베이스
- WebSocket (STOMP) 설정

### [0.2.0] - 2025-12-20
**Added**
- Vue 3 프론트엔드 초기화
- Pinia 상태 관리

### [0.1.0] - 2025-12-20
**Added**
- 프로젝트 초기 설정
- Spring Boot 3.4.1 기본 구조

---

**Made with ❤️ by [yhjung2356](https://github.com/yhjung2356)**

**프로젝트 버전**: 0.9.0-SNAPSHOT  
**마지막 업데이트**: 2025-12-28  
**라이센스**: MIT License
