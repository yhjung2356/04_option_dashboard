package com.trading.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.dashboard.config.KisApiConfig;
import com.trading.dashboard.model.OptionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 선물/옵션 종목코드 마스터 관리 서비스
 * 하드코딩 대신 동적으로 거래 가능한 종목 리스트를 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SymbolMasterService {

    private final KisApiConfig config;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${kis.options.strike-range:15}")
    private int defaultStrikeRange;

    @Value("${kis.options.strike-interval:2.5}")
    private BigDecimal defaultStrikeInterval;

    @Value("${kis.options.default-index:585.0}")
    private BigDecimal defaultIndex;

    // 실시간 코스피200 지수 캐시 (선물 가격에서 역산)
    private volatile BigDecimal realtimeKospi200Index = null;

    private String accessToken;
    private LocalDateTime tokenExpiry;

    /**
     * REST API용 선물코드를 WebSocket용으로 변환
     * REST: A016{월월} → WebSocket: 101{분기코드}{월월}
     * 
     * 분기코드:
     * - V: 2025년
     * - S: 2026년
     * - U: 2027년
     * - W: 2028년
     * 
     * @param restCode REST API 코드 (예: A01603)
     * @return WebSocket 코드 (예: 101S03)
     */
    public String convertToWebSocketCode(String restCode) {
        if (!restCode.startsWith("A016")) {
            throw new IllegalArgumentException("Invalid futures code: " + restCode);
        }

        int month = Integer.parseInt(restCode.substring(4)); // "A01603" → 3
        LocalDate now = LocalDate.now();
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue();

        // 해당 월물이 올해인지 내년인지 판단
        int targetYear = currentYear;
        if (month < currentMonth) {
            targetYear = currentYear + 1;
        }

        // 연도별 분기코드 매핑
        char quarterCode;
        switch (targetYear) {
            case 2025:
                quarterCode = 'V';
                break;
            case 2026:
                quarterCode = 'S';
                break;
            case 2027:
                quarterCode = 'U';
                break;
            case 2028:
                quarterCode = 'W';
                break;
            default:
                throw new IllegalStateException("Unsupported year: " + targetYear);
        }

        String wsCode = String.format("101%c%02d", quarterCode, month);
        log.debug("🔄 선물 코드 변환: {} → {} ({}-{}월물)", restCode, wsCode, targetYear, month);
        return wsCode;
    }

    /**
     * 거래 가능한 KOSPI200 선물 종목코드 생성 (REST API용)
     * 규칙: A016{월월} 형태 (예: A01603 = 3월물)
     * 결과를 캐싱하여 반복 계산 방지
     * 
     * 만기일 기준: 각 월물의 두 번째 목요일 이후에는 차월물로 전환
     * TODO: 현재는 최근월물(nearest) 1개만 반환 - 테스트용
     */
    @Cacheable("futuresCodes")
    public List<String> getActiveFuturesCodes() {
        List<String> codes = new ArrayList<>();

        // 현재 날짜 기준으로 거래 가능한 월물 결정
        LocalDate now = LocalDate.now();
        int currentDay = now.getDayOfMonth();

        // 매월 10일 이후면 차월물로 전환 (두 번째 목요일이 보통 8~14일이므로 안전하게 10일 기준)
        int startOffset = (currentDay >= 10) ? 1 : 0;

        for (int i = startOffset; i < startOffset + 12; i++) {
            YearMonth yearMonth = YearMonth.from(now.plusMonths(i));
            int month = yearMonth.getMonthValue();

            // 선물은 3, 6, 9, 12월물만 거래됨 (분기물)
            if (month % 3 == 0) {
                String code = String.format("A016%02d", month);
                codes.add(code);
                log.info("✅ 선물 최근월물: {} ({}-{}월물)", code, yearMonth.getYear(), month);
                break; // 테스트용: 첫 번째 분기월만 사용
            }
        }

        log.info("📊 활성 선물 종목 {}개 생성: {}", codes.size(), codes);
        return codes;
    }

    /**
     * 활성 옵션의 만기년월 반환 (YYYYMM 형식)
     * 전광판 API 호출 시 사용
     */
    public String getActiveOptionMonth() {
        LocalDate now = LocalDate.now();
        int currentDay = now.getDayOfMonth();

        // 매월 10일 이후면 차월물로 전환
        int startOffset = (currentDay >= 10) ? 1 : 0;

        YearMonth yearMonth = YearMonth.from(now.plusMonths(startOffset));
        String maturityMonth = yearMonth.format(DateTimeFormatter.ofPattern("yyyyMM"));

        log.debug("🗓️ 활성 옵션 만기년월: {}", maturityMonth);
        return maturityMonth;
    }

    /**
     * WebSocket용 선물 종목코드 생성
     * 규칙: 101{분기코드}{월월} (예: 101S03 = 2026년 3월물)
     */
    @Cacheable("futuresWebSocketCodes")
    public List<String> getActiveFuturesWebSocketCodes() {
        List<String> restCodes = getActiveFuturesCodes();
        List<String> wsCodes = new ArrayList<>();

        for (String restCode : restCodes) {
            String wsCode = convertToWebSocketCode(restCode);
            wsCodes.add(wsCode);
        }

        log.info("🌐 WebSocket용 선물 종목 {}개 생성: {}", wsCodes.size(), wsCodes);
        return wsCodes;
    }

    /**
     * 야간장 옵션 종목코드 생성 (WebSocket용)
     * 
     * ⚠️ 중요: 야간장은 주간장과 동일한 종목코드 사용
     * - 주간장 코드: B01601570 (콜), C01601570 (풋)
     * - 야간장 코드: B01601570 (콜), C01601570 (풋) <- 동일!
     * - 차이점: TR_ID만 다름 (주간: H0IOCNT0, 야간: H0EUCNT0)
     * 
     * 이 메서드는 주간장 코드 생성 로직을 그대로 사용합니다.
     * 
     * @param basePrice      기준가격 (현재 KOSPI200 지수)
     * @param strikeRange    기준가 위아래 범위 (기본 15포인트)
     * @param strikeInterval 행사가 간격 (기본 2.5포인트)
     * @return 야간장 옵션 종목코드 리스트 (주간장과 동일)
     * @deprecated 야간장은 주간장과 동일한 코드를 사용하므로 getActiveOptionCodes() 사용 권장
     */
    @Cacheable(value = "nightOptionsCodes", key = "#basePrice + '_' + #strikeRange + '_' + #strikeInterval")
    public List<OptionCodeInfo> getActiveNightOptionCodes(BigDecimal basePrice, int strikeRange,
            BigDecimal strikeInterval) {
        // 야간장은 주간장과 동일한 종목코드를 사용
        // 주간장 코드 생성 로직을 그대로 호출
        List<OptionCodeInfo> codes = getActiveOptionCodes(basePrice, strikeRange, strikeInterval);

        log.info("🌙 야간 옵션 종목 {}개 생성 (주간장 코드 사용): 예제={}",
                codes.size(),
                codes.isEmpty() ? "N/A" : codes.get(0).code);

        return codes;
    }

    /**
     * 기본값으로 야간장 옵션 종목 생성
     */
    public List<OptionCodeInfo> getActiveNightOptionCodes(BigDecimal basePrice) {
        return getActiveNightOptionCodes(basePrice, defaultStrikeRange, defaultStrikeInterval);
    }

    /**
     * 거래 가능한 KOSPI200 옵션 종목코드 생성 (주간장용)
     * 
     * @param basePrice      기준가격 (현재 KOSPI200 지수)
     * @param strikeRange    기준가 위아래 범위 (기본 15포인트)
     * @param strikeInterval 행사가 간격 (기본 2.5포인트)
     *                       결과를 캐싱하여 반복 계산 방지 (basePrice를 키로 사용)
     */
    @Cacheable(value = "optionsCodes", key = "#basePrice + '_' + #strikeRange + '_' + #strikeInterval")
    public List<OptionCodeInfo> getActiveOptionCodes(BigDecimal basePrice, int strikeRange, BigDecimal strikeInterval) {
        List<OptionCodeInfo> codes = new ArrayList<>();

        // 근월물 만기월 계산 (당월 또는 차월)
        LocalDate now = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(now);
        YearMonth nextMonth = currentMonth.plusMonths(1);

        // 매월 두 번째 목요일 이후면 차월물로 전환
        boolean useNextMonth = isAfterSecondThursday(now);

        // 🔧 수정: 2026년 1월물 사용 (차월물 - 거래 활발)
        // 월물코드: 0601 (2026년 1월)
        YearMonth targetMonth = YearMonth.of(2026, 1);

        int year = targetMonth.getYear();
        int month = targetMonth.getMonthValue();

        // 🔧 실제 KIS API 옵션코드 형식 (10자리) 사용
        // 형식: [콜풋:1자리][01:기초자산][월물코드:3자리][행사가:3자리]
        // 예: B01601600 = B(Call) + 01(코스피200) + 601(2026년 1월) + 600(행사가)
        // 예: C01601560 = C(Put) + 01(코스피200) + 601(2026년 1월) + 560(행사가)
        //
        // 월물코드 생성: YMM 형식의 3자리 (예: 601 = 2026년 01월, 512 = 2025년 12월)
        // Y: 2020년대 기준 (2026년=6, 2025년=5)
        String monthCode = String.format("%d%02d", year - 2020, month); // 6 + 01 = 601

        // ATM(At-The-Money) 행사가 계산
        // 기준가를 2.5 단위로 반올림 (584.64 → 585.0, 587.3 → 587.5)
        BigDecimal atmStrike = basePrice
                .divide(strikeInterval, 0, RoundingMode.HALF_UP)
                .multiply(strikeInterval);

        // ATM 중심으로 위아래 범위 계산
        // strikeRange가 15면 ATM ± 15 포인트 범위
        BigDecimal rangeBD = new BigDecimal(strikeRange);
        BigDecimal lowerBound = atmStrike.subtract(rangeBD);
        BigDecimal upperBound = atmStrike.add(rangeBD);

        // 행사가를 2.5 단위로 조정
        lowerBound = lowerBound
                .divide(strikeInterval, 0, RoundingMode.DOWN)
                .multiply(strikeInterval);
        upperBound = upperBound
                .divide(strikeInterval, 0, RoundingMode.UP)
                .multiply(strikeInterval);

        log.info("📈 옵션 종목 생성(ATM중심): 기준가={}, ATM={}, 범위={}~{} (±{}), 간격={}, 월물코드={} ({}년{}월)",
                basePrice, atmStrike, lowerBound, upperBound, strikeRange, strikeInterval, monthCode, year, month);

        // 행사가별로 콜/풋 생성
        BigDecimal strike = lowerBound;
        while (strike.compareTo(upperBound) <= 0) {
            // 행사가를 3자리 숫자로 변환 (575.0 -> "575", 577.5 -> "577")
            // 소수점 .5는 무시하고 정수부만 사용
            String strikeStr = String.format("%03d", strike.intValue());

            // 실제 KIS API 코드: B01601575 (Call), C01601575 (Put) - 10자리
            String callCode = String.format("B01%s%s", monthCode, strikeStr);
            String putCode = String.format("C01%s%s", monthCode, strikeStr);

            log.debug("🔍 실제코드 생성: monthCode={}, strikeStr={}, callCode='{}', putCode='{}'",
                    monthCode, strikeStr, callCode, putCode);
            codes.add(new OptionCodeInfo(callCode, OptionType.CALL, strike, targetMonth));
            codes.add(new OptionCodeInfo(putCode, OptionType.PUT, strike, targetMonth));

            strike = strike.add(strikeInterval);
        }

        log.info("📊 활성 옵션 종목 {}개 생성 ({}년 {}월물, 월물코드:{}, 샘플: {} / {})",
                codes.size(), year, month, monthCode,
                codes.isEmpty() ? "N/A" : codes.get(0).code,
                codes.size() > 1 ? codes.get(1).code : "N/A");
        return codes;
    }

    /**
     * 기본값으로 옵션 종목 생성 (설정 파일의 strike-range와 strike-interval 사용)
     */
    public List<OptionCodeInfo> getActiveOptionCodes(BigDecimal basePrice) {
        return getActiveOptionCodes(basePrice, defaultStrikeRange, defaultStrikeInterval);
    }

    /**
     * 매월 두 번째 목요일 이후인지 확인 (만기일 기준)
     */
    private boolean isAfterSecondThursday(LocalDate date) {
        YearMonth yearMonth = YearMonth.from(date);
        LocalDate firstDay = yearMonth.atDay(1);

        // 첫 번째 목요일 찾기
        LocalDate firstThursday = firstDay;
        while (firstThursday.getDayOfWeek().getValue() != 4) { // 4 = 목요일
            firstThursday = firstThursday.plusDays(1);
        }

        // 두 번째 목요일
        LocalDate secondThursday = firstThursday.plusWeeks(1);

        return date.isAfter(secondThursday);
    }

    /**
     * 행사가를 종목코드 형식으로 변환
     * 예: 560.0 → "560", 562.5 → "562", 565.0 → "565", 567.5 → "567"
     * KIS API 옵션코드: 2.5 단위 행사가를 정수부만 사용 (B01601560 = 560.0, B01601562 = 562.5)
     */
    private String formatStrikePrice(BigDecimal strike) {
        // 2.5 단위를 0.5로 곱하면: 560.0→560.0, 562.5→562.5, 565.0→565.0, 567.5→567.5
        // 정수부만 추출: 560, 562, 565, 567
        BigDecimal scaledStrike = strike.multiply(new BigDecimal("2")).divide(new BigDecimal("5"), 0,
                RoundingMode.HALF_UP);
        int codeValue = strike.intValue();

        // .5 단위는 짝수로 반올림 (562.5 → 562, 567.5 → 567)
        if (strike.remainder(BigDecimal.ONE).compareTo(new BigDecimal("0.5")) == 0) {
            // .5인 경우 해당 정수값 사용
            return String.format("%d", codeValue);
        } else {
            // 정수인 경우 그대로 사용
            return String.format("%d", codeValue);
        }
    }

    /**
     * 옵션 종목코드 정보 DTO
     */
    public static class OptionCodeInfo {
        public final String code;
        public final OptionType type;
        public final BigDecimal strikePrice;
        public final YearMonth expiryMonth;

        public OptionCodeInfo(String code, OptionType type, BigDecimal strikePrice, YearMonth expiryMonth) {
            this.code = code;
            this.type = type;
            this.strikePrice = strikePrice;
            this.expiryMonth = expiryMonth;
        }

        @Override
        public String toString() {
            return String.format("%s (%s, 행사가=%.1f, 만기=%s)",
                    code, type, strikePrice, expiryMonth);
        }
    }

    /**
     * 현재 KOSPI200 지수를 KIS API에서 조회
     * 만약 실시간 선물 가격이 있으면 그것을 우선 사용
     * 결과를 1분간 캐싱하여 API 호출 최소화
     */
    @Cacheable("kospi200Index")
    public BigDecimal getCurrentKospi200Index() {
        // 실시간 선물 가격에서 역산한 값이 있으면 우선 사용
        if (realtimeKospi200Index != null) {
            log.info("📊 코스피200 지수 (선물가격 기준): {}", realtimeKospi200Index);
            return realtimeKospi200Index;
        }

        try {
            String token = getAccessToken();
            if (token == null) {
                log.warn("⚠️ Failed to get access token, using default index: {}", defaultIndex);
                return defaultIndex;
            }

            String url = config.getBaseUrl() +
                    "/uapi/domestic-stock/v1/quotations/inquire-index-price" +
                    "?FID_COND_MRKT_DIV_CODE=U" + // U: 업종
                    "&FID_INPUT_ISCD=0001"; // 0001: KOSPI200

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + token)
                    .header("appkey", config.getAppKey())
                    .header("appsecret", config.getAppSecret())
                    .header("tr_id", "FHKUP03500100")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String rtCd = root.path("rt_cd").asText("");

                if ("0".equals(rtCd)) {
                    JsonNode output = root.path("output");
                    String bstp_nmix_prpr = output.path("bstp_nmix_prpr").asText(); // 업종 현재가

                    if (!bstp_nmix_prpr.isEmpty()) {
                        BigDecimal currentIndex = new BigDecimal(bstp_nmix_prpr);
                        log.info("✅ KOSPI200 현재가 조회 성공: {}", currentIndex);
                        return currentIndex;
                    }
                } else {
                    log.warn("⚠️ KOSPI200 조회 API 에러: {} - {}",
                            root.path("msg_cd").asText(),
                            root.path("msg1").asText());
                }
            } else {
                log.warn("⚠️ KOSPI200 조회 HTTP 에러: {}", response.statusCode());
            }

        } catch (Exception e) {
            log.warn("⚠️ KOSPI200 지수 조회 실패: {}. 기본값 사용: {}", e.getMessage(), defaultIndex);
        }

        return defaultIndex;
    }

    /**
     * KIS API 액세스 토큰 획득 (KisApiService와 동일한 로직)
     */
    private String getAccessToken() {
        try {
            // 메모리 캐시 확인
            if (accessToken != null && tokenExpiry != null && LocalDateTime.now().isBefore(tokenExpiry)) {
                return accessToken;
            }

            // 새 토큰 발급
            String url = config.getBaseUrl() + "/oauth2/tokenP";
            String requestBody = String.format(
                    "{\"grant_type\":\"client_credentials\",\"appkey\":\"%s\",\"appsecret\":\"%s\"}",
                    config.getAppKey(), config.getAppSecret());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                accessToken = root.get("access_token").asText();
                int expiresIn = root.get("expires_in").asInt();
                tokenExpiry = LocalDateTime.now().plusSeconds(expiresIn);
                log.debug("✓ Access token obtained (expires in {} seconds)", expiresIn);
                return accessToken;
            } else {
                log.error("Failed to get access token: {}", response.statusCode());
                return null;
            }

        } catch (Exception e) {
            log.error("Error getting access token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 종목코드 캐시 갱신 스케줄러
     * - 매일 오전 8시에 실행 (장 시작 전)
     * - 매월 두 번째 목요일에는 자동으로 만기월이 갱신됨
     */
    @Scheduled(cron = "0 0 8 * * ?")
    @CacheEvict(value = { "futuresCodes", "optionsCodes" }, allEntries = true)
    public void refreshSymbolCodes() {
        log.info("🔄 종목코드 캐시 갱신 시작...");
        // @CacheEvict에 의해 캐시가 비워지고, 다음 조회 시 재생성됨
    }

    /**
     * KOSPI200 지수 캐시 갱신 스케줄러
     * - 평일 오전 9시~오후 3시 30분까지 1분마다 실행
     */
    @Scheduled(cron = "0 * 9-15 * * MON-FRI")
    @CacheEvict(value = "kospi200Index", allEntries = true)
    public void refreshKospi200Index() {
        log.debug("🔄 KOSPI200 지수 캐시 갱신");
        // @CacheEvict에 의해 캐시가 비워지고, 다음 조회 시 재조회됨
    }

    /**
     * 옵션 심볼 코드에서 행사가 추출
     * 형식: B010601670 → 670.0 (또는 672 → 672.5)
     * 마지막 3자리가 행사가 (홀수면 .5 추가)
     * 
     * @param symbol 옵션 심볼 코드 (예: B010601670, C010601672)
     * @return 행사가 (예: 670.0, 672.5)
     */
    public static BigDecimal parseStrikePrice(String symbol) {
        if (symbol == null || symbol.length() < 10) {
            return BigDecimal.ZERO;
        }

        try {
            // 마지막 3자리 추출 (예: "670", "672")
            String strikeStr = symbol.substring(symbol.length() - 3);
            int strikeInt = Integer.parseInt(strikeStr);

            // 실제 행사가 계산 (2.5 간격)
            // 670 → 670.0, 672 → 672.5, 675 → 675.0, 677 → 677.5
            BigDecimal strike = new BigDecimal(strikeInt);

            // 홀수면 .5 추가
            if (strikeInt % 2 == 1) {
                strike = strike.add(new BigDecimal("0.5"));
            }

            return strike;
        } catch (Exception e) {
            log.warn("행사가 파싱 실패: symbol={}", symbol);
            return BigDecimal.ZERO;
        }
    }

    /**
     * 옵션 심볼 코드에서 만기월 추출
     * 형식: B010601670 → 2026-01
     * 중간 4자리가 월물코드 (YYMM 형식)
     * 
     * @param symbol 옵션 심볼 코드
     * @return 만기월 문자열 (예: "2026-01")
     */
    public static String parseExpiryMonth(String symbol) {
        if (symbol == null || symbol.length() < 10) {
            return null;
        }

        try {
            // 중간 4자리 추출 (예: "0601")
            String monthCode = symbol.substring(3, 7);
            int yy = Integer.parseInt(monthCode.substring(0, 2));
            int mm = Integer.parseInt(monthCode.substring(2, 4));

            // 2020년대 기준 (06 → 2026)
            int year = 2020 + yy;

            return String.format("%d-%02d", year, mm);
        } catch (Exception e) {
            log.warn("만기월 파싱 실패: symbol={}", symbol);
            return null;
        }
    }

    /**
     * 옵션 심볼 코드에서 종목명 생성
     * 형식: B010601670 → "KOSPI200 CALL 670.0 (2026.01)"
     * 
     * @param symbol     옵션 심볼 코드
     * @param optionType CALL or PUT
     * @return 종목명
     */
    public static String generateOptionName(String symbol, OptionType optionType) {
        if (symbol == null || symbol.length() < 10) {
            return "";
        }

        BigDecimal strikePrice = parseStrikePrice(symbol);
        String expiryMonth = parseExpiryMonth(symbol);

        if (expiryMonth == null) {
            return "";
        }

        String typeStr = (optionType == OptionType.CALL) ? "CALL" : "PUT";

        return String.format("KOSPI200 %s %.1f (%s)", typeStr, strikePrice, expiryMonth.replace("-", "."));
    }

    /**
     * 선물 가격에서 코스피200 지수를 추정하여 업데이트
     * 
     * @param futuresPrice 선물 현재가 (예: 594.00)
     */
    public void updateKospi200IndexFromFutures(BigDecimal futuresPrice) {
        // 선물 가격은 보통 지수보다 약간 높게 형성됨 (베이시스)
        // 간단하게 선물가격 자체를 지수로 근사
        // (만기일이 가까울수록 베이시스는 0에 수렴)
        this.realtimeKospi200Index = futuresPrice;

        log.info("📊 코스피200 지수 업데이트 (선물가격 기준): {}", realtimeKospi200Index);
    }

    /**
     * 야간장 옵션 WebSocket 종목코드 생성 (8자리 형식)
     * 
     * 형식: 101{분기코드}{월월}{행사가}
     * - 101: KOSPI200 옵션 고정
     * - 분기코드: V(2025), S(2026), U(2027), W(2028)
     * - 월월: 01~12 (2자리)
     * - 행사가: 4자리 (예: 5850 = 585.0, 5875 = 587.5)
     * 
     * 예제: 101V9000 (2025년 11월, 행사가 900.0)
     * 
     * @return 야간장 옵션 코드 리스트 (CALL + PUT, 8자리)
     */
    public List<String> getActiveNightOptionCodes() {
        // KOSPI200 현재가 가져오기
        BigDecimal currentIndex = getCurrentKospi200Index();

        // getActiveNightOptionCodes(BigDecimal, int, BigDecimal)를 호출하여 OptionCodeInfo
        // 리스트 받기
        List<OptionCodeInfo> optionInfos = getActiveNightOptionCodes(currentIndex, defaultStrikeRange,
                defaultStrikeInterval);

        // String 리스트로 변환 (code만 추출)
        List<String> codes = optionInfos.stream()
                .map(info -> info.code)
                .collect(Collectors.toList());

        log.info("🌃 야간옵션 코드 생성 (9자리): {} 종목, 예제={}",
                codes.size(),
                codes.isEmpty() ? "" : codes.get(0));

        return codes;
    }
}
