# 코드 리뷰 보고서

**프로젝트**: 선물/옵션 실시간 거래 대시보드  
**리뷰 날짜**: 2025-12-25  
**리뷰어**: GitHub Copilot

---

## 📊 리뷰 요약

### ✅ 전반적인 평가
- **코드 품질**: 양호 (일부 개선 필요)
- **아키텍처**: 잘 구성됨 (MVC 패턴, Service Layer 분리)
- **보안**: **위험** (민감 정보 노출 발견)
- **성능**: 양호 (일부 최적화 가능)
- **테스트**: **부족** (단위 테스트 없음)

---

## 🚨 심각한 문제 (Critical Issues)

### 1. ⚠️ **보안 위험: API 키 하드코딩**

**파일**: `src/main/resources/application.properties`

```properties
# ⚠️ 보안 위험: 실제 APP KEY와 SECRET이 코드에 노출됨!
kis.api.app-key=PSEum68j7AE49Xfm3xa4DpScv79KYzi8cO7l
kis.api.app-secret=KHv89zQcJQU6dI5PlvoWJWd+c2Mrpt5NeT/ccw63JOhUYAmfh9K9HIZHQoIaXzhOtgp/5Ng4UkOmEf10uYt1T8B8/X6NkJWCUDfKE7sgt4xZ6mqEWGNHHKXm+VpMPFpK2ZKpspGrfoB5pywolT5eakH2KZxhEQqkleH8Ant8TuQ/CM3s3NM=
kis.api.account-no=43602495
```

**문제점**:
- 실제 API 키와 시크릿이 코드에 평문으로 저장
- GitHub에 업로드되어 공개됨
- 누구나 이 키로 API 호출 가능
- 계정 번호도 노출됨

**해결 방안**:
1. **즉시 조치 필요**:
   ```bash
   # 1. 한국투자증권 사이트에서 API 키 재발급
   # 2. application.properties에서 민감정보 제거
   # 3. Git 히스토리에서 완전 제거
   git filter-branch --force --index-filter \
     "git rm --cached --ignore-unmatch src/main/resources/application.properties" \
     --prune-empty --tag-name-filter cat -- --all
   ```

2. **환경 변수 사용**:
   ```properties
   # application.properties
   kis.api.app-key=${KIS_API_KEY}
   kis.api.app-secret=${KIS_API_SECRET}
   kis.api.account-no=${KIS_ACCOUNT_NO}
   ```

3. **application-local.properties 생성** (.gitignore에 추가):
   ```properties
   # application-local.properties (Git에 커밋하지 않음)
   kis.api.app-key=실제키
   kis.api.app-secret=실제시크릿
   kis.api.account-no=계좌번호
   ```

4. **.gitignore에 추가**:
   ```
   application-local.properties
   application-prod.properties
   kis_token.cache
   ```

---

## 🐛 발견된 버그 및 문제점

### 2. Deprecated API 사용

**파일**: `DataSimulationService.java`

**문제**:
- `BigDecimal.ROUND_HALF_UP` 상수 사용 (Java 9부터 deprecated)
- `setScale(int, int)` 메소드 사용 (Java 9부터 deprecated)

**상태**: ✅ 수정 완료
```java
// 수정 전
.setScale(2, BigDecimal.ROUND_HALF_UP)

// 수정 후
.setScale(2, RoundingMode.HALF_UP)
```

### 3. 사용하지 않는 Import 문

**수정 완료된 파일**:
- ✅ `InitialDataLoader.java` - `LocalTime` 제거
- ✅ `StrikePriceDataDTO.java` - `OptionType` 제거
- ✅ `KisWebSocketService.java` - `StandardCharsets` 제거
- ✅ `MarketDataService.java` - `FuturesData` 제거

### 4. Null Safety 경고

**파일**: 여러 Repository 메소드 호출

**문제점**:
- Repository에서 반환된 값이 null일 수 있음
- NonNull 어노테이션 누락

**해결**: ✅ `WebSocketConfig.java`에 `@NonNull` 어노테이션 추가 완료

### 5. 사용되지 않는 필드

**파일**: `KisWebSocketService.java:27`

```java
private final KisApiConfig config;  // ⚠️ 사용되지 않음
```

**해결 방안**: config 필드를 실제로 사용하거나 제거

---

## 🔧 코드 품질 개선 사항

### 6. 하드코딩된 종목코드

**파일**: `KisApiService.java`

```java
String[] futureCodes = {
    "A01603",  // 3월물
    "A01606",  // 6월물
    "A01609",  // 9월물
    "A01612",  // 12월물
};
```

**문제점**:
- 월물이 바뀌면 코드 수정 필요
- 유지보수성 저하

**개선 방안**:
```java
// Config 파일로 분리
@Value("${kis.futures.codes}")
private List<String> futureCodes;
```

### 7. 에러 핸들링 부족

**파일**: 여러 Service 클래스

**문제점**:
- API 호출 실패 시 단순 로그만 출력
- 사용자에게 명확한 에러 메시지 전달 부족
- Retry 로직 없음

**개선 방안**:
```java
@Retryable(
    value = {HttpException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000)
)
public String getAccessToken() {
    // ...
}
```

### 8. Magic Number 사용

**파일**: `DataSimulationService.java`

```java
Thread.sleep(100);  // Magic number
BigDecimal.valueOf(optionPrice - 0.5)  // Magic number
```

**개선 방안**:
```java
private static final long API_CALL_DELAY_MS = 100L;
private static final double BID_ASK_SPREAD = 0.5;
```

### 9. 로그 레벨 혼용

**파일**: 여러 Service 클래스

```java
log.warn("Failed to fetch...");  // 경고
log.debug("Received message...");  // 디버그
log.info("✓ Loaded...");  // 정보
```

**개선 방안**: 일관된 로그 레벨 정책 수립

---

## 📝 아키텍처 분석

### 장점 ✅

1. **계층 분리 잘됨**:
   - Controller → Service → Repository 패턴
   - DTO를 사용한 데이터 전송
   - Model과 DTO 명확히 분리

2. **설정 외부화**:
   - `@ConfigurationProperties` 사용
   - application.properties로 설정 관리

3. **비동기 처리**:
   - `@EnableScheduling` 활용
   - WebSocket 실시간 통신

4. **Lombok 활용**:
   - Boilerplate 코드 감소
   - @Slf4j로 로깅 간소화

### 개선점 📌

1. **Exception Handling**:
   - `@ControllerAdvice`로 전역 예외 처리 필요
   - Custom Exception 클래스 정의

2. **Validation**:
   - `@Valid` 어노테이션 사용
   - DTO에 Bean Validation 추가

3. **Transaction 관리**:
   - 일부 Service 메소드에만 `@Transactional` 적용
   - 일관성 있는 트랜잭션 정책 필요

4. **API 응답 표준화**:
   ```java
   public class ApiResponse<T> {
       private boolean success;
       private String message;
       private T data;
   }
   ```

---

## 🧪 테스트 코드 부재

### 문제점
- **단위 테스트**: 0개
- **통합 테스트**: 0개
- **테스트 커버리지**: 0%

### 필요한 테스트

```java
// 예시: KisApiService 테스트
@SpringBootTest
class KisApiServiceTest {
    
    @Test
    void 액세스_토큰_발급_성공() {
        // given
        // when
        String token = kisApiService.getAccessToken();
        // then
        assertThat(token).isNotNull();
    }
    
    @Test
    void API_호출_실패시_예외_발생() {
        // given
        // when & then
        assertThrows(RuntimeException.class, 
            () -> kisApiService.fetchFuturesPrice("INVALID", null, null));
    }
}
```

---

## ⚡ 성능 최적화 제안

### 1. HTTP Client 재사용
**현재**: 매번 새로운 HttpClient 생성
```java
private final HttpClient httpClient = HttpClient.newHttpClient();
```

**개선**: Connection Pool 설정
```java
private final HttpClient httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .executor(Executors.newFixedThreadPool(5))
    .build();
```

### 2. 캐싱 추가
```java
@Cacheable(value = "optionData", key = "#symbol")
public OptionData getOptionData(String symbol) {
    // ...
}
```

### 3. 비동기 API 호출
```java
@Async
public CompletableFuture<List<OptionData>> loadKospi200Options() {
    // ...
}
```

---

## 🔒 보안 개선 사항

### 필수 조치

1. **API 키 재발급** (최우선)
2. **환경 변수로 분리**
3. **Git 히스토리 정리**
4. **HTTPS 강제 적용**
   ```java
   @Configuration
   public class SecurityConfig {
       @Bean
       public SecurityFilterChain filterChain(HttpSecurity http) {
           http.requiresChannel()
               .anyRequest()
               .requiresSecure();
           return http.build();
       }
   }
   ```

5. **Rate Limiting 구현**
   ```java
   @RateLimiter(name = "kisApi")
   public String getAccessToken() {
       // ...
   }
   ```

---

## 📦 Spring Boot 버전 업그레이드 권장

**현재**: Spring Boot 3.2.0
**문제**: OSS 지원 종료 (2024-12-31)

**해결**:
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.0</version>  <!-- 최신 LTS 버전으로 업그레이드 -->
</parent>
```

---

## 📋 TODO 우선순위

### 🚨 긴급 (Immediate)
1. ❗ API 키 재발급 및 환경 변수 분리
2. ❗ Git 히스토리에서 민감정보 제거
3. ❗ .gitignore 업데이트

### 🔴 높음 (High Priority)
4. Spring Boot 3.3.0 업그레이드
5. 전역 Exception Handler 추가
6. 테스트 코드 작성 시작 (최소 30% 커버리지)
7. 사용되지 않는 필드 제거

### 🟡 중간 (Medium Priority)
8. Magic Number 상수화
9. 하드코딩된 종목코드 Config 분리
10. API Response 표준화
11. Retry 로직 추가
12. Caching 구현

### 🟢 낮음 (Low Priority)
13. 성능 최적화 (Connection Pool)
14. 비동기 처리 확대
15. 문서화 개선 (JavaDoc)
16. 코드 리팩토링

---

## 💡 추천 사항

### 도구 추가
- **SonarQube**: 코드 품질 분석
- **JaCoCo**: 테스트 커버리지 측정
- **Checkstyle**: 코드 스타일 검사
- **SpotBugs**: 버그 패턴 탐지

### 라이브러리 추가
```xml
<!-- Spring Retry -->
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>

<!-- Resilience4j (Circuit Breaker) -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>

<!-- Redis Cache -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

---

## ✅ 수정 완료 항목

- [x] Deprecated BigDecimal 메소드 → RoundingMode 사용
- [x] 사용하지 않는 import 문 제거
- [x] NonNull 어노테이션 추가
- [x] 코드 포맷팅 개선

---

## 📞 다음 단계

1. **보안 이슈 해결** (즉시)
2. **테스트 코드 작성** (1주일 내)
3. **Spring Boot 업그레이드** (1주일 내)
4. **Exception Handling 개선** (2주일 내)
5. **성능 최적화** (1개월 내)

---

**마지막 업데이트**: 2025-12-25  
**다음 리뷰 예정**: 2026-01-25
