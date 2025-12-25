# 🔐 실제 API 연동 가이드

## 1. 현재 상태

### ✅ 이미 설정된 것
- Spring Boot `application.properties`에 한국투자증권 API 키 설정 완료
- 백엔드 서비스 로직 구현 완료
- 데모 데이터 생성기 작동 중 (3초마다 갱신)

### 🔄 전환 방법

**데모 모드 → 실시간 모드 전환**

현재는 `DemoDataGenerator`가 3초마다 랜덤 데이터를 생성하고 있습니다.
실제 API로 전환하려면:

## 2. 백엔드 수정

### Option 1: 환경 변수로 모드 전환

`application.properties`에 추가:
```properties
# 데이터 소스 설정
trading.data.source=DEMO  # DEMO 또는 LIVE
```

### Option 2: Profile 분리

**application-demo.properties** (데모 모드)
```properties
spring.profiles.active=demo
trading.data.source=DEMO
```

**application-live.properties** (실시간 모드)
```properties
spring.profiles.active=live
trading.data.source=LIVE
```

실행 시:
```bash
# 데모 모드
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=demo

# 실시간 모드
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=live
```

## 3. API 연동 체크리스트

### ✅ 확인 사항

1. **API 키 유효성**
   - 한국투자증권 API 키 만료 여부 확인
   - AppKey: `PSEum68j7AE49Xfm3xa4DpScv79KYzi8cO7l`
   - AppSecret: 설정 완료

2. **네트워크 연결**
   - `https://openapi.koreainvestment.com:9443` 접근 가능 여부
   - 방화벽/프록시 설정 확인

3. **API 사용량**
   - 한국투자증권 API 호출 제한 확인
   - Rate limiting 고려

4. **시장 운영 시간**
   - 정규장: 09:00 - 15:30
   - 장후시간외: 15:40 - 16:00
   - 시간외단일가: 16:00 - 18:00

## 4. 프론트엔드 확인 방법

Vue 앱에서 데이터 소스 확인:

### 헤더에 표시되는 정보
- **데이터 소스**: "실시간" 또는 "데모"
- **WebSocket 상태**: 연결됨/연결 끊김
- **마지막 업데이트**: 실시간 표시

### 설정 페이지 (`/settings`)
1. 브라우저에서 `http://localhost:3000/settings` 접속
2. "데이터 소스" 섹션에서 현재 상태 확인
3. 새로고침 버튼으로 최신 데이터 받기

## 5. API 응답 데이터 매핑

### 한국투자증권 API → 애플리케이션 모델

| API 필드 | 애플리케이션 필드 | 설명 |
|---------|-----------------|------|
| `bstp_nmix_prpr` | `currentPrice` | 현재가 |
| `bstp_nmix_oprc` | `openPrice` | 시가 |
| `bstp_nmix_hgpr` | `highPrice` | 고가 |
| `bstp_nmix_lwpr` | `lowPrice` | 저가 |
| `acml_vol` | `volume` | 누적 거래량 |
| `optn_theo_pr` | `theoreticalPrice` | 이론가 |
| `impl_vol` | `impliedVolatility` | 내재 변동성 |
| `dlta` | `delta` | 델타 |
| `gmma` | `gamma` | 감마 |
| `thta` | `theta` | 세타 |
| `vega` | `vega` | 베가 |

## 6. 트러블슈팅

### 문제 1: API 연결 실패
```
ERROR: Failed to connect to KIS API
```

**해결 방법:**
1. API 키 유효성 확인
2. 네트워크 연결 확인
3. `application.properties`의 `trading.kis.base-url` 확인

### 문제 2: 토큰 만료
```
ERROR: Access token expired
```

**해결 방법:**
- `TokenManager`가 자동으로 토큰 갱신
- 수동 갱신: `/api/market/refresh-token` 호출

### 문제 3: Rate Limiting
```
ERROR: Too many requests
```

**해결 방법:**
- API 호출 빈도 조정 (현재 3초 → 5초 이상 권장)
- `DemoDataGenerator`의 `@Scheduled` 설정 수정

## 7. 모니터링

### 로그 확인

**Spring Boot 로그:**
```bash
# 디버그 레벨로 실행
.\mvnw.cmd spring-boot:run -Dlogging.level.com.dashboard=DEBUG
```

**주요 로그 메시지:**
- `[KIS API] Token 발급 성공`
- `[Market Data] 데이터 갱신 완료`
- `[WebSocket] 클라이언트 연결: {sessionId}`

### 브라우저 콘솔

Vue 앱에서 F12 → Console:
```javascript
[WebSocket] 연결 성공
[Market Store] 개요 로딩 완료
[Option Store] 체인 데이터 업데이트
```

## 8. 성능 최적화

### 백엔드
- **Database Connection Pool**: HikariCP (기본 설정 최적화)
- **Caching**: Spring Cache 적용 고려
- **Async Processing**: `@Async`로 비동기 처리

### 프론트엔드
- **Code Splitting**: Vite가 자동으로 처리 (vue-vendor, chart chunks)
- **Lazy Loading**: Vue Router로 페이지별 lazy load
- **PWA Caching**: Service Worker로 오프라인 지원

## 9. 프로덕션 배포

### 빌드 및 배포

```bash
# 1. 프론트엔드 빌드
cd frontend
npm run build

# 2. Spring Boot JAR 빌드
cd ..
.\mvnw.cmd clean package -DskipTests

# 3. JAR 실행
java -jar target/futures-options-dashboard-1.0.0.jar --spring.profiles.active=live

# 4. 접속
# http://localhost:8080
```

### 환경 변수로 API 키 관리 (보안 강화)

```bash
# Windows
set TRADING_KIS_APP_KEY=your_app_key
set TRADING_KIS_APP_SECRET=your_app_secret

# Linux/Mac
export TRADING_KIS_APP_KEY=your_app_key
export TRADING_KIS_APP_SECRET=your_app_secret
```

`application.properties`:
```properties
trading.kis.app-key=${TRADING_KIS_APP_KEY}
trading.kis.app-secret=${TRADING_KIS_APP_SECRET}
```

## 10. 추가 기능 제안

### 구현 가능한 기능들

1. **알림 기능**
   - 특정 가격/거래량 도달 시 알림
   - 웹 푸시 알림 (PWA)

2. **포트폴리오 관리**
   - 보유 종목 추적
   - 손익 계산

3. **차트 추가**
   - 캔들스틱 차트
   - 변동성 콘 (Volatility Cone)
   - IV Skew 그래프

4. **백테스팅**
   - 과거 데이터로 전략 테스트
   - 수익률 시뮬레이션

5. **다중 사용자 지원**
   - 사용자 인증 (Spring Security)
   - 개인별 설정 저장

---

## 📞 문의 및 지원

- GitHub Issues: [프로젝트 저장소]
- 한국투자증권 API 문의: https://apiportal.koreainvestment.com
- Vue.js 문서: https://vuejs.org
- Spring Boot 문서: https://spring.io/projects/spring-boot
