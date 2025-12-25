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

    /**
     * KOSPI200 선물 데이터 로드
     */
    public void loadKospi200Futures() {
        log.info("Loading KOSPI200 Futures data from KIS API...");

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

        for (String code : futureCodes) {
            try {
                FuturesData futures = fetchFuturesPrice(token, code, timestamp);
                if (futures != null) {
                    futuresList.add(futures);
                }
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DataFetchException("Interrupted while fetching futures", "KisAPI", code);
            } catch (DataFetchException e) {
                log.warn("Failed to fetch futures {}: {}", code, e.getMessage());
            }
        }

        if (!futuresList.isEmpty()) {
            futuresDataRepository.saveAll(futuresList);
            log.info("✓ Loaded {} KOSPI200 futures from KIS API", futuresList.size());
        }
    }

    /**
     * KOSPI200 옵션 데이터 로드
     */
    public void loadKospi200Options() {
        try {
            log.info("Loading KOSPI200 Options data from KIS API...");

            String token = getAccessToken();
            if (token == null) {
                log.warn("Failed to get access token");
                return;
            }

            List<OptionData> optionsList = new ArrayList<>();
            LocalDateTime timestamp = LocalDateTime.now();

            // 실제 KOSPI200 옵션 종목코드 (2026년 1월물)
            // 콜옵션 (Call): B01601{행사가}, 풋옵션 (Put): C01601{행사가}
            // 행사가 560~575 (2.5pt 간격), 현재 KOSPI200 지수 567 부근
            String[] optionCodes = {
                    // 콜옵션 (B = Call)
                    "B01601560", // 560.0
                    "B01601562", // 562.5
                    "B01601565", // 565.0
                    "B01601567", // 567.0 (ATM)
                    "B01601570", // 570.0
                    "B01601572", // 572.5
                    "B01601575", // 575.0

                    // 풋옵션 (C = Put)
                    "C01601560", // 560.0
                    "C01601562", // 562.5
                    "C01601565", // 565.0
                    "C01601567", // 567.0 (ATM)
                    "C01601570", // 570.0
                    "C01601572", // 572.5
                    "C01601575" // 575.0
            };

            for (String code : optionCodes) {
                try {
                    // 옵션 타입 판별 (B = 콜, C = 풋)
                    OptionType optionType = code.startsWith("B0160") ? OptionType.CALL : OptionType.PUT;

                    // 행사가는 API 응답(acpr)에서 추출 (정확한 값 사용)
                    OptionData option = fetchOptionPrice(token, code, optionType, timestamp);
                    if (option != null) {
                        // 호가 정보 조회
                        fetchOptionAskingPrice(token, option);
                        optionsList.add(option);
                    }

                    Thread.sleep(100);

                } catch (Exception e) {
                    log.warn("Failed to fetch option {}: {}", code, e.getMessage());
                }
            }

            if (!optionsList.isEmpty()) {
                optionDataRepository.saveAll(optionsList);
                log.info("✓ Loaded {} KOSPI200 options from KIS API", optionsList.size());
            }

        } catch (Exception e) {
            log.error("Error loading KOSPI200 options: {}", e.getMessage(), e);
        }
    }

    /**
     * 개별 선물 시세 조회
     */
    private FuturesData fetchFuturesPrice(String token, String code, LocalDateTime timestamp) {
        try {
            String url = config.getBaseUrl() +
                    "/uapi/domestic-futureoption/v1/quotations/inquire-price" +
                    "?FID_COND_MRKT_DIV_CODE=F" + // F: 선물
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
                    FuturesData futures = new FuturesData();
                    futures.setSymbol(code);
                    futures.setName(getContractMonthName(code));

                    // 필드 추출
                    String prprStr = output1.path("futs_prpr").asText("0");
                    BigDecimal currentPrice = new BigDecimal(prprStr.replace(",", ""));
                    long volume = output1.path("acml_vol").asLong(0);

                    // 거래대금: API에서 제공하는 acml_tr_pbmn 필드 사용
                    String tradingValueStr = output1.path("acml_tr_pbmn").asText("0");
                    BigDecimal tradingValue = new BigDecimal(tradingValueStr.replace(",", ""));

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
    private OptionData fetchOptionPrice(String token, String code, OptionType type, LocalDateTime timestamp) {
        try {
            String url = config.getBaseUrl() +
                    "/uapi/domestic-futureoption/v1/quotations/inquire-price" +
                    "?FID_COND_MRKT_DIV_CODE=O" + // O: 옵션
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
                    option.setImpliedVolatility(new BigDecimal(ivStr.replace(",", "")));

                    // 그릭스(Greeks) 추출
                    String deltaStr = output1.path("delta_val").asText("0");
                    option.setDelta(new BigDecimal(deltaStr.replace(",", "")));

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
                log.info("✓ Using cached token from file");
                return cachedToken;
            }

            // 3. 새 토큰 발급
            log.info("Requesting new access token from KIS API...");

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

                log.info("✓ Access token obtained successfully! Expires in {} seconds", expiresIn);
                return newToken;
            } else {
                String responseBody = response.body();
                log.error("Failed to get access token: {} - {}", response.statusCode(), responseBody);

                // Rate Limit 에러인 경우, 파일에서 토큰 강제 로드 시도
                if (responseBody.contains("EGW00133")) {
                    log.warn("Rate limit exceeded. Attempting to use any cached token...");
                    String forcedToken = loadTokenFromFile(true);
                    if (forcedToken != null) {
                        log.info("✓ Using potentially expired token from cache");
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
