package com.trading.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.dashboard.config.KisApiConfig;
import com.trading.dashboard.exception.DataFetchException;
import com.trading.dashboard.exception.DataParseException;
import com.trading.dashboard.exception.TokenExpiredException;
import com.trading.dashboard.model.*;
import com.trading.dashboard.repository.FuturesDataRepository;
import com.trading.dashboard.repository.OptionDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 한국투자증권 API 서비스
 * 실제 종목코드를 사용하여 KOSPI200 선물/옵션 데이터 조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KisApiService {

    private final KisApiConfig config;
    private final FuturesDataRepository futuresDataRepository;
    private final OptionDataRepository optionDataRepository;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TokenManager tokenManager;
    private final MarketStatusService marketStatusService;

    // 이전 조회의 평균 IV (변동성 기반 범위 조정용)
    private volatile Double previousAvgIV = null;

    /**
     * 현재 야간장 시간인지 판단
     */
    private boolean isNightMarket() {
        MarketStatusService.MarketStatus status = marketStatusService.getMarketStatus();
        return status == MarketStatusService.MarketStatus.OPEN_NIGHT_SESSION;
    }

    /**
     * 모든 데이터 삭제 (세션 전환 시 사용)
     */
    @Transactional
    public void clearAllData() {
        long futuresCount = futuresDataRepository.count();
        long optionsCount = optionDataRepository.count();

        if (futuresCount > 0 || optionsCount > 0) {
            futuresDataRepository.deleteAll();
            optionDataRepository.deleteAll();
            log.info("[KIS API] Cleared {} futures, {} options", futuresCount, optionsCount);
        }
    }

    /**
     * KOSPI200 선물 데이터 로드
     */
    @Transactional
    public void loadKospi200Futures() {
        log.info("[KIS API] Loading KOSPI200 Futures data...");

        String token = getAccessToken();
        List<FuturesData> futuresList = new ArrayList<>();
        LocalDateTime timestamp = LocalDateTime.now();

        // 실제 KOSPI200 선물 종목코드
        String[] futureCodes = {
                "A01603", // 3월물
                "A01606", // 6월물
                "A01609", // 9월물
                "A01612", // 12월물
        };

        // 시장 상태에 따라 시장구분코드 결정 (F:주간선물, CM:야간선물)
        boolean isNight = isNightMarket();
        String marketDivCode = isNight ? "CM" : "F";
        log.info("[FUTURES] Market session: {} (marketDivCode: {})", isNight ? "Night" : "Day", marketDivCode);

        for (String code : futureCodes) {
            try {
                FuturesData futures = fetchFuturesPrice(token, code, timestamp, marketDivCode);
                if (futures != null) {
                    futuresList.add(futures);
                }
                Thread.sleep(100); // API 요청 간격: 100ms (rate limit 고려)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DataFetchException("Interrupted while fetching futures", "KisAPI", code);
            } catch (DataFetchException e) {
                log.warn("Failed to fetch futures {}: {}", code, e.getMessage());
            }
        }

        if (!futuresList.isEmpty()) {
            futuresDataRepository.saveAll(futuresList);
            log.info("[KIS API] Loaded {} KOSPI200 futures", futuresList.size());
        }
    }

    /**
     * KOSPI200 옵션 데이터 로드
     * 
     * @Transactional
     */
    public void loadKospi200Options() {
        try {
            log.info("[KIS API] Loading KOSPI200 Options data...");

            String token = getAccessToken();
            if (token == null) {
                log.warn("Failed to get access token");
                return;
            }

            List<OptionData> optionsList = new ArrayList<>();
            LocalDateTime timestamp = LocalDateTime.now();

            // 1. 선물 가격 조회로 기초자산 가격 추정
            FuturesData nearestFutures = futuresDataRepository.findAll().stream()
                    .filter(f -> f.getVolume() > 0)
                    .findFirst()
                    .orElse(null);

            double underlyingPrice = 590.0; // 기본값
            if (nearestFutures != null) {
                underlyingPrice = nearestFutures.getCurrentPrice().doubleValue();
                log.info("[OPTIONS] Estimated underlying price from futures: {}", underlyingPrice);
            }

            // 2. ATM 계산 (5pt 단위로 반올림)
            int atmStrike = (int) (Math.round(underlyingPrice / 5.0) * 5);

            // 3. 변동성 기반 동적 범위 계산
            int baseRange = 15; // 기본 범위 ±15pt
            int range = baseRange;

            if (previousAvgIV != null && previousAvgIV > 0) {
                // IV 기반 범위 조정: IV 20% 기준, ±15pt
                // IV 40%면 2배 → ±30pt, IV 10%면 0.5배 → ±7.5pt
                double ivFactor = previousAvgIV / 20.0;
                range = (int) Math.round(baseRange * ivFactor);

                // 최소 15pt, 최대 30pt로 제한 (IV 낮을 때: 26개, 높을 때: 50개)
                range = Math.max(15, Math.min(30, range));
                log.info(String.format("Dynamic range adjustment: IV=%.2f%%, factor=%.2f, range=±%dpt",
                        previousAvgIV, ivFactor, range));
            }

            int strikeStart = atmStrike - range;
            int strikeEnd = atmStrike + range;

            log.info("[OPTIONS] ATM Strike: {}, Range: {}~{} (±{}pt, underlying: {})",
                    atmStrike, strikeStart, strikeEnd, range, underlyingPrice);

            // 실제 KOSPI200 옵션 종목코드 (2026년 1월물)
            // 콜옵션 (Call): B01601{행사가}, 풋옵션 (Put): C01601{행사가}
            // KOSPI200 옵션은 2.5pt 간격으로만 존재 (575.0, 577.5, 580.0, 582.5, ...)
            List<String> optionCodes = new ArrayList<>();

            // 2.5pt 간격으로 옵션 코드 생성
            for (double strike = strikeStart; strike <= strikeEnd; strike += 2.5) {
                int strikeInt = (int) strike; // 575.0 -> 575, 577.5 -> 577
                String strikeCode = String.format("%03d", strikeInt);
                optionCodes.add("B01601" + strikeCode); // 콜옵션
                optionCodes.add("C01601" + strikeCode); // 풋옵션
            }

            int expectedStrikes = (int) ((strikeEnd - strikeStart) / 2.5) + 1;
            log.info(
                    "[OPTIONS] Querying {} contracts ({}~{}, {} strikes, ATM: {}, Range: ±{}pt)",
                    optionCodes.size(), strikeStart, strikeEnd, expectedStrikes, atmStrike, range);

            // 시장 상태에 따라 시장구분코드 결정 (O:주간옵션, EU:야간옵션)
            boolean isNight = isNightMarket();
            String marketDivCode = isNight ? "EU" : "O";
            log.info("[OPTIONS] Market session: {} (marketDivCode: {})", isNight ? "Night" : "Day", marketDivCode);

            for (String code : optionCodes) {
                try {
                    // 옵션 타입 판별 (B = 콜, C = 푻)
                    OptionType optionType = code.startsWith("B0160") ? OptionType.CALL : OptionType.PUT;

                    // 행사가는 API 응답(acpr)에서 추출 (정확한 값 사용)
                    OptionData option = fetchOptionPrice(token, code, optionType, timestamp, marketDivCode);
                    if (option != null) {
                        // 호가 정보 조회
                        fetchOptionAskingPrice(token, option);
                        optionsList.add(option);
                    }

                    Thread.sleep(100); // API 요청 간격: 100ms (rate limit 고려)

                } catch (Exception e) {
                    log.warn("Failed to fetch option {}: {}", code, e.getMessage());
                }
            }

            if (!optionsList.isEmpty()) {
                optionDataRepository.saveAll(optionsList);
                log.info("[KIS API] Loaded {} KOSPI200 options", optionsList.size());

                // 평균 IV 계산 및 캐싱 (다음 조회 시 범위 조정용)
                double avgIV = optionsList.stream()
                        .filter(opt -> opt.getImpliedVolatility() != null
                                && opt.getImpliedVolatility().compareTo(BigDecimal.ZERO) > 0)
                        .mapToDouble(opt -> opt.getImpliedVolatility().doubleValue())
                        .average()
                        .orElse(0.20); // 기본값 20%

                previousAvgIV = avgIV;
                log.info(
                        String.format("Average IV calculated: %.2f%% (will adjust range for next query)", avgIV));
            }

        } catch (Exception e) {
            log.error("Error loading KOSPI200 options: {}", e.getMessage(), e);
        }
    }

    /**
     * 개별 선물 시세 조회
     */
    private FuturesData fetchFuturesPrice(String token, String code, LocalDateTime timestamp, String marketDivCode) {
        try {
            String url = config.getBaseUrl() +
                    "/uapi/domestic-futureoption/v1/quotations/inquire-price" +
                    "?FID_COND_MRKT_DIV_CODE=" + marketDivCode + // F:주간선물, CM:야간선물
                    "&FID_INPUT_ISCD=" + code;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + token)
                    .header("appkey", config.getAppKey())
                    .header("appsecret", config.getAppSecret())
                    .header("tr_id", "FHMIF10000000")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());

                // API 응답 로깅 (디버깅용)
                log.debug("Futures {} API response: {}", code, response.body());

                // rt_cd 체크 (성공: "0", 실패: "1")
                String rtCd = root.path("rt_cd").asText("");
                if (!"0".equals(rtCd)) {
                    log.warn("API error for futures {}: {} - {}",
                            code, root.path("msg_cd").asText(""), root.path("msg1").asText(""));
                    return null;
                }

                JsonNode output1 = root.get("output1");
                if (output1 != null && !output1.isEmpty()) {
                    // 필드 추출
                    String prprStr = output1.path("futs_prpr").asText("0");
                    BigDecimal currentPrice = new BigDecimal(prprStr.replace(",", ""));
                    long volume = output1.path("acml_vol").asLong(0);
                    long openInterest = output1.path("hts_otst_stpl_qty").asLong(0);

                    // 거래대금: API에서 제공하는 acml_tr_pbmn 필드 사용
                    String tradingValueStr = output1.path("acml_tr_pbmn").asText("0");
                    BigDecimal tradingValue = new BigDecimal(tradingValueStr.replace(",", ""));

                    // 디버깅: 실제 API 데이터 확인
                    log.info("[API DATA] {} - Price: {}, Volume: {}, OI: {}, TradingValue: {}",
                            code, currentPrice, volume, openInterest, tradingValue);

                    FuturesData futures = new FuturesData();
                    futures.setSymbol(code);
                    futures.setName(getContractMonthName(code));

                    futures.setCurrentPrice(currentPrice);
                    futures.setChangeAmount(
                            new BigDecimal(output1.path("futs_prdy_vrss").asText("0").replace(",", "")));
                    futures.setChangePercent(
                            new BigDecimal(output1.path("futs_prdy_ctrt").asText("0").replace(",", "")));
                    futures.setVolume(volume);
                    futures.setTradingValue(tradingValue);

                    // 미결제약정: hts_otst_stpl_qty 필드 사용
                    futures.setOpenInterest(output1.path("hts_otst_stpl_qty").asLong(0));

                    futures.setTimestamp(timestamp);
                    return futures;
                } else {
                    log.warn("No output1 data for futures {}", code);
                }
            } else {
                log.warn("HTTP error {} for futures {}: {}", response.statusCode(), code, response.body());
            }
        } catch (Exception e) {
            log.error("Error fetching futures price for {}: {}", code, e.getMessage());
        }
        return null;
    }

    /**
     * 개별 옵션 시세 조회
     */
    private OptionData fetchOptionPrice(String token, String code, OptionType type, LocalDateTime timestamp,
            String marketDivCode) {
        try {
            String url = config.getBaseUrl() +
                    "/uapi/domestic-futureoption/v1/quotations/inquire-price" +
                    "?FID_COND_MRKT_DIV_CODE=" + marketDivCode + // O:주간옵션, EU:야간옵션
                    "&FID_INPUT_ISCD=" + code;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + token)
                    .header("appkey", config.getAppKey())
                    .header("appsecret", config.getAppSecret())
                    .header("tr_id", "FHMIF10000000")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());

                // API 응답 로깅 (디버깅용)
                log.debug("Option {} API response: {}", code, response.body());

                // rt_cd 체크 (성공: "0", 실패: "1")
                String rtCd = root.path("rt_cd").asText("");
                if (!"0".equals(rtCd)) {
                    log.warn("API error for option {}: {} - {}",
                            code, root.path("msg_cd").asText(""), root.path("msg1").asText(""));
                    return null;
                }

                JsonNode output1 = root.get("output1");
                if (output1 != null && !output1.isEmpty()) {
                    OptionData option = new OptionData();
                    option.setSymbol(code);
                    option.setOptionType(type);

                    // 종목명: API 응답의 hts_kor_isnm 필드 사용 (예: "C 202601 567.5")
                    String korName = output1.path("hts_kor_isnm").asText("");
                    option.setName(korName);

                    // 행사가: API 응답의 acpr 필드 사용 (정확한 값)
                    String strikeStr = output1.path("acpr").asText("0");
                    BigDecimal strikePrice = new BigDecimal(strikeStr.replace(",", ""));
                    option.setStrikePrice(strikePrice);

                    // 필드 추출
                    String prprStr = output1.path("futs_prpr").asText("0");
                    BigDecimal currentPrice = new BigDecimal(prprStr.replace(",", ""));
                    long volume = output1.path("acml_vol").asLong(0);

                    // 거래대금: API에서 제공하는 acml_tr_pbmn 필드 사용
                    String tradingValueStr = output1.path("acml_tr_pbmn").asText("0");
                    BigDecimal tradingValue = new BigDecimal(tradingValueStr.replace(",", ""));

                    option.setCurrentPrice(currentPrice);
                    option.setVolume(volume);
                    option.setTradingValue(tradingValue);

                    // 미결제약정: hts_otst_stpl_qty 필드 사용
                    option.setOpenInterest(output1.path("hts_otst_stpl_qty").asLong(0));

                    // 내재변동성: hts_ints_vltl 필드 사용
                    String ivStr = output1.path("hts_ints_vltl").asText("0");
                    BigDecimal rawIV = new BigDecimal(ivStr.replace(",", ""));

                    // 그리스(Greeks) 추출
                    String deltaStr = output1.path("delta_val").asText("0");
                    BigDecimal rawDelta = new BigDecimal(deltaStr.replace(",", ""));

                    // KIS API의 IV 스케일 처리:
                    // OTM: 0.2150 (소수점) → 21.50% (100 곱함)
                    // ITM: 23.8142 (%) → 23.81% (그대로)
                    BigDecimal normalizedIV = rawIV.compareTo(BigDecimal.ONE) < 0
                            ? rawIV.multiply(BigDecimal.valueOf(100))
                            : rawIV;

                    log.info("[IV DEBUG] {} - Raw IV: {}, Normalized IV: {}, Delta: {}", code, rawIV, normalizedIV,
                            rawDelta);
                    option.setImpliedVolatility(normalizedIV);
                    option.setDelta(rawDelta);

                    String gammaStr = output1.path("gama").asText("0"); // API 오타: gama
                    option.setGamma(new BigDecimal(gammaStr.replace(",", "")));

                    String thetaStr = output1.path("theta").asText("0");
                    option.setTheta(new BigDecimal(thetaStr.replace(",", "")));

                    String vegaStr = output1.path("vega").asText("0");
                    option.setVega(new BigDecimal(vegaStr.replace(",", "")));

                    // 기초자산 가격 (KOSPI200 지수): output3에서 추출
                    JsonNode output3 = root.get("output3");
                    if (output3 != null && !output3.isEmpty()) {
                        String underlyingStr = output3.path("bstp_nmix_prpr").asText("0");
                        option.setUnderlyingPrice(new BigDecimal(underlyingStr.replace(",", "")));
                    } else {
                        option.setUnderlyingPrice(null);
                    }

                    // 호가 정보는 별도 API 필요 (inquire-asking-price)
                    // 현재는 null로 설정
                    option.setBidPrice(null);
                    option.setAskPrice(null);
                    option.setBidVolume(null);
                    option.setAskVolume(null);

                    option.setTimestamp(timestamp);
                    return option;
                } else {
                    log.warn("No output1 data for option {}", code);
                }
            } else {
                log.warn("HTTP error {} for option {}: {}", response.statusCode(), code, response.body());
            }
        } catch (Exception e) {
            log.error("Error fetching option price for {}: {}", code, e.getMessage());
        }
        return null;
    }

    /**
     * 옵션 호가 조회 (매수/매도 호가)
     */
    private void fetchOptionAskingPrice(String token, OptionData option) {
        try {
            String url = config.getBaseUrl() +
                    "/uapi/domestic-futureoption/v1/quotations/inquire-asking-price" +
                    "?FID_COND_MRKT_DIV_CODE=O" + // O: 옵션
                    "&FID_INPUT_ISCD=" + option.getSymbol();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + token)
                    .header("appkey", config.getAppKey())
                    .header("appsecret", config.getAppSecret())
                    .header("tr_id", "FHMIF10100000") // 호가 조회 TR_ID
                    .timeout(java.time.Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());

                String rtCd = root.path("rt_cd").asText("");
                if ("0".equals(rtCd)) {
                    JsonNode output1 = root.get("output1");
                    if (output1 != null && !output1.isEmpty()) {
                        // 최우선 매도호가 (1호가)
                        String askPriceStr = output1.path("optn_lstn_askp1").asText("0");
                        if (!askPriceStr.isEmpty() && !"0".equals(askPriceStr)) {
                            option.setAskPrice(new BigDecimal(askPriceStr.replace(",", "")));
                            option.setAskVolume(output1.path("optn_lstn_askp_rsqn1").asInt(0));
                        }

                        // 최우선 매수호가 (1호가)
                        String bidPriceStr = output1.path("optn_lstn_bidp1").asText("0");
                        if (!bidPriceStr.isEmpty() && !"0".equals(bidPriceStr)) {
                            option.setBidPrice(new BigDecimal(bidPriceStr.replace(",", "")));
                            option.setBidVolume(output1.path("optn_lstn_bidp_rsqn1").asInt(0));
                        }

                        log.debug("Option {} asking price: bid={}, ask={}",
                                option.getSymbol(), option.getBidPrice(), option.getAskPrice());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to fetch asking price for {}: {}", option.getSymbol(), e.getMessage());
            // 호가 조회 실패는 치명적이지 않으므로 계속 진행
        }
    }

    /**
     * 액세스 토큰 발급/갱신
     */
    public String getAccessToken() {
        try {
            // 1. 메모리 캐시 확인 (TokenManager)
            if (tokenManager.isTokenValid()) {
                log.debug("Using cached token from memory (expires at {})", tokenManager.getExpiry());
                return tokenManager.getToken();
            }

            // 2. 파일 캐시 확인
            String cachedToken = loadTokenFromFile();
            if (cachedToken != null) {
                log.info("[AUTH] Using cached token from file");
                return cachedToken;
            }

            // 3. 새 토큰 발급
            log.info("[AUTH] Requesting new access token...");

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
                String newToken = root.get("access_token").asText();
                int expiresIn = root.get("expires_in").asInt();
                LocalDateTime expiry = LocalDateTime.now().plusSeconds(expiresIn);

                // TokenManager에 저장 (Thread-safe)
                tokenManager.setToken(newToken, expiry);

                // 파일에도 저장 (재시작 시 사용)
                saveTokenToFile(newToken, expiry);

                log.info("[AUTH] Access token obtained successfully (expires in {} seconds)", expiresIn);
                return newToken;
            } else {
                String responseBody = response.body();
                log.error("Failed to get access token: {} - {}", response.statusCode(), responseBody);

                // Rate Limit 에러인 경우, 파일에서 토큰 강제 로드 시도
                if (responseBody.contains("EGW00133")) {
                    log.warn("Rate limit exceeded. Attempting to use any cached token...");
                    String forcedToken = loadTokenFromFile(true);
                    if (forcedToken != null) {
                        log.info("[AUTH] Using potentially expired token from cache");
                        return forcedToken;
                    }
                }

                throw new TokenExpiredException("Failed to get access token: " + response.statusCode());
            }

        } catch (TokenExpiredException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error getting access token: {}", e.getMessage(), e);
            throw new TokenExpiredException("Failed to get access token: " + e.getMessage());
        }
    }

    /**
     * 토큰을 파일에 저장
     */
    private void saveTokenToFile(String token, LocalDateTime expiry) {
        try {
            String tokenData = token + "|" + expiry.toString();
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of("kis_token.cache"),
                    tokenData,
                    java.nio.charset.StandardCharsets.UTF_8);
            log.debug("Token saved to file");
        } catch (Exception e) {
            log.warn("Failed to save token to file: {}", e.getMessage());
        }
    }

    /**
     * 파일에서 토큰 로드
     */
    private String loadTokenFromFile() {
        return loadTokenFromFile(false);
    }

    /**
     * 파일에서 토큰 로드 (만료 체크 옵션)
     */
    private String loadTokenFromFile(boolean ignoreExpiry) {
        try {
            java.nio.file.Path path = java.nio.file.Path.of("kis_token.cache");
            if (!java.nio.file.Files.exists(path)) {
                return null;
            }

            String tokenData = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
            String[] parts = tokenData.split("\\|");
            if (parts.length != 2) {
                return null;
            }

            String token = parts[0];
            LocalDateTime expiry = LocalDateTime.parse(parts[1]);

            // 만료 체크
            if (!ignoreExpiry && LocalDateTime.now().isAfter(expiry)) {
                log.debug("Cached token expired at {}", expiry);
                return null;
            }

            // TokenManager에 로드
            tokenManager.setToken(token, expiry);
            return token;

        } catch (Exception e) {
            log.debug("Failed to load token from file: {}", e.getMessage());
            return null;
        }
    }

    /**
     * WebSocket approval_key 발급
     * REST API의 access_token과 다른 WebSocket 전용 키
     */
    public String getWebSocketApprovalKey() {
        try {
            log.debug("Requesting WebSocket approval key...");

            String url = config.getBaseUrl() + "/oauth2/Approval";
            String requestBody = String.format(
                    "{\"grant_type\":\"client_credentials\",\"appkey\":\"%s\",\"secretkey\":\"%s\"}",
                    config.getAppKey(), config.getAppSecret());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String approvalKey = root.get("approval_key").asText();
                log.info("[WS] WebSocket approval key obtained successfully");
                return approvalKey;
            } else {
                log.error("Failed to get WebSocket approval key: {} - {}",
                        response.statusCode(), response.body());
                throw new TokenExpiredException("Failed to get WebSocket approval key: " + response.statusCode());
            }

        } catch (TokenExpiredException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error getting WebSocket approval key: {}", e.getMessage(), e);
            throw new TokenExpiredException("Failed to get WebSocket approval key: " + e.getMessage());
        }
    }

    /**
     * 월물 코드로 계약 월 이름 반환
     */
    private String getContractMonthName(String code) {
        if (code.equals("A01603"))
            return "KOSPI200 선물 3월물";
        if (code.equals("A01606"))
            return "KOSPI200 선물 6월물";
        if (code.equals("A01609"))
            return "KOSPI200 선물 9월물";
        if (code.equals("A0160C"))
            return "KOSPI200 선물 12월물";
        return code;
    }
}
