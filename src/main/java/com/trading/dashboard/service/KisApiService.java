package com.trading.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.dashboard.config.KisApiConfig;
import com.trading.dashboard.model.*;
import com.trading.dashboard.repository.FuturesDataRepository;
import com.trading.dashboard.repository.OptionDataRepository;
import com.trading.dashboard.service.SymbolMasterService.OptionCodeInfo;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
    private final SymbolMasterService symbolMasterService;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String accessToken;
    private LocalDateTime tokenExpiry;

    /**
     * 야간장 여부 체크
     * 야간장: 18:00 ~ 익일 08:30 (월~금)
     * 주간장: 09:00 ~ 15:45 (월~금)
     */
    private boolean isNightSession() {
        java.time.LocalTime now = java.time.LocalTime.now();
        // 18:00 이후 또는 08:30 이전이면 야간장
        return now.isAfter(java.time.LocalTime.of(18, 0)) ||
                now.isBefore(java.time.LocalTime.of(8, 30));
    }

    /**
     * KOSPI200 선물 데이터 로드
     */
    public void loadKospi200Futures() {
        try {
            log.info("Loading KOSPI200 Futures data from KIS API...");

            String token = getAccessToken();
            if (token == null) {
                log.warn("Failed to get access token");
                return;
            }

            List<FuturesData> futuresList = new ArrayList<>();
            LocalDateTime timestamp = LocalDateTime.now();

            // SymbolMasterService를 통해 동적으로 거래 가능한 선물 종목코드 생성
            List<String> futureCodes = symbolMasterService.getActiveFuturesCodes();

            if (futureCodes.isEmpty()) {
                log.warn("⚠️ 활성 선물 종목이 없습니다");
                return;
            }

            for (String code : futureCodes) {
                try {
                    FuturesData futures = fetchFuturesPrice(token, code, timestamp);
                    if (futures != null) {
                        futuresList.add(futures);
                    }
                    Thread.sleep(100);
                } catch (Exception e) {
                    log.warn("Failed to fetch futures {}: {}", code, e.getMessage());
                }
            }

            if (!futuresList.isEmpty()) {
                futuresDataRepository.saveAll(futuresList);
                log.info("✓ Loaded {} KOSPI200 futures from KIS API", futuresList.size());
            }

        } catch (Exception e) {
            log.error("Error loading KOSPI200 futures: {}", e.getMessage(), e);
        }
    }

    /**
     * KOSPI200 옵션 데이터 로드 (국내옵션전광판_콜풋 API 사용)
     * FHPIF05030100: 만기월별 전체 옵션 일괄 조회 (내재가치/시간가치/호가 포함)
     */
    public void loadKospi200Options() {
        try {
            log.info("Loading KOSPI200 Options data from KIS API (전광판 API)...");

            String token = getAccessToken();
            if (token == null) {
                log.warn("Failed to get access token");
                return;
            }

            LocalDateTime timestamp = LocalDateTime.now();

            // 현재 활성 만기월 조회
            String activeMonth = symbolMasterService.getActiveOptionMonth();
            log.info("📊 옵션 전광판 조회 시작: 만기월={}", activeMonth);

            // 전광판 API로 일괄 조회 (콜+풋 전체)
            List<OptionData> optionsList = fetchOptionDisplayBoard(token, activeMonth, timestamp);

            if (!optionsList.isEmpty()) {
                optionDataRepository.saveAll(optionsList);
                log.info("✓ Loaded {} KOSPI200 options from 전광판 API", optionsList.size());
            } else {
                log.warn("⚠️ 전광판 API에서 옵션 데이터를 가져오지 못했습니다");
            }

        } catch (Exception e) {
            log.error("Error loading KOSPI200 options: {}", e.getMessage(), e);
        }
    }

    /**
     * 국내옵션전광판_콜풋 API 호출 (FHPIF05030100)
     * 만기월별 콜/풋 옵션 전체를 한 번에 조회
     */
    private List<OptionData> fetchOptionDisplayBoard(String token, String maturityMonth,
            LocalDateTime timestamp) {
        List<OptionData> optionsList = new ArrayList<>();

        try {
            String url = config.getBaseUrl() +
                    "/uapi/domestic-futureoption/v1/quotations/display-board-callput" +
                    "?FID_COND_MRKT_DIV_CODE=O" + // O: 옵션
                    "&FID_COND_SCR_DIV_CODE=20503" + // Unique key
                    "&FID_MRKT_CLS_CODE=CO" + // CO: 콜옵션
                    "&FID_MTRT_CNT=" + maturityMonth + // 만기년월 (YYYYMM)
                    "&FID_COND_MRKT_CLS_CODE=" + // 공백: KOSPI200
                    "&FID_MRKT_CLS_CODE1=PO"; // PO: 풋옵션

            log.debug("📡 전광판 API 호출: {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + token)
                    .header("appkey", config.getAppKey())
                    .header("appsecret", config.getAppSecret())
                    .header("tr_id", "FHPIF05030100")
                    .header("custtype", "P")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());

                String rtCd = root.path("rt_cd").asText("");
                if (!"0".equals(rtCd)) {
                    log.warn("전광판 API 오류: {} - {}",
                            root.path("msg_cd").asText(""),
                            root.path("msg1").asText(""));
                    return optionsList;
                }

                // output1: 콜옵션 배열
                JsonNode callOptions = root.path("output1");
                if (callOptions.isArray()) {
                    for (JsonNode callNode : callOptions) {
                        OptionData option = parseOptionFromDisplayBoard(
                                callNode, OptionType.CALL, timestamp);
                        if (option != null) {
                            optionsList.add(option);
                        }
                    }
                }

                // output2: 풋옵션 배열
                JsonNode putOptions = root.path("output2");
                if (putOptions.isArray()) {
                    for (JsonNode putNode : putOptions) {
                        OptionData option = parseOptionFromDisplayBoard(
                                putNode, OptionType.PUT, timestamp);
                        if (option != null) {
                            optionsList.add(option);
                        }
                    }
                }

                log.info("✓ 전광판 API 파싱 완료: 콜 {}건, 풋 {}건",
                        callOptions.size(), putOptions.size());

            } else {
                log.warn("전광판 API HTTP 오류: {}", response.statusCode());
            }

        } catch (Exception e) {
            log.error("전광판 API 호출 실패: {}", e.getMessage(), e);
        }

        return optionsList;
    }

    /**
     * 전광판 API 응답에서 OptionData 객체 생성
     */
    private OptionData parseOptionFromDisplayBoard(JsonNode node, OptionType optionType,
            LocalDateTime timestamp) {
        try {
            // 옵션 단축 종목코드 (예: B01601480, C01601480)
            // 전광판 API는 이미 B01/C01 형식으로 반환
            String symbol = node.path("optn_shrn_iscd").asText("");
            if (symbol.isEmpty()) {
                return null;
            }

            // 행사가 추출
            String strikeStr = node.path("acpr").asText("0");
            BigDecimal strikePrice = new BigDecimal(strikeStr.replace(",", ""));

            OptionData option = new OptionData();
            option.setSymbol(symbol);
            option.setOptionType(optionType);
            option.setStrikePrice(strikePrice);
            option.setTimestamp(timestamp);

            // 현재가
            String priceStr = node.path("optn_prpr").asText("0");
            option.setCurrentPrice(new BigDecimal(priceStr.replace(",", "")));

            // 거래량/거래대금
            String volumeStr = node.path("acml_vol").asText("0");
            option.setVolume(Long.parseLong(volumeStr));

            String tradingValueStr = node.path("acml_tr_pbmn").asText("0");
            option.setTradingValue(new BigDecimal(tradingValueStr.replace(",", "")));

            // 미결제약정
            String openInterestStr = node.path("hts_otst_stpl_qty").asText("0");
            option.setOpenInterest(Long.parseLong(openInterestStr));

            String oiChangeStr = node.path("otst_stpl_qty_icdc").asText("0");
            option.setOpenInterestChange(Long.parseLong(oiChangeStr));

            // ✅ 호가 정보 (전광판 API에 포함됨!)
            String bidPriceStr = node.path("optn_bidp").asText("0");
            String askPriceStr = node.path("optn_askp").asText("0");
            if (!"0".equals(bidPriceStr) && !bidPriceStr.isEmpty()) {
                option.setBidPrice(new BigDecimal(bidPriceStr.replace(",", "")));
            }
            if (!"0".equals(askPriceStr) && !askPriceStr.isEmpty()) {
                option.setAskPrice(new BigDecimal(askPriceStr.replace(",", "")));
            }

            // 호가 잔량
            String bidVolumeStr = node.path("total_bidp_rsqn").asText("0");
            String askVolumeStr = node.path("total_askp_rsqn").asText("0");
            if (!"0".equals(bidVolumeStr)) {
                option.setBidVolume(Integer.parseInt(bidVolumeStr));
            }
            if (!"0".equals(askVolumeStr)) {
                option.setAskVolume(Integer.parseInt(askVolumeStr));
            }

            // ✅ 이론가/내재가치/시간가치 (전광판 API에 포함됨!)
            String theoreticalPriceStr = node.path("hts_thpr").asText("");
            if (!theoreticalPriceStr.isEmpty() && !"0".equals(theoreticalPriceStr)) {
                option.setTheoreticalPrice(new BigDecimal(theoreticalPriceStr.replace(",", "")));
            }

            String intrinsicValueStr = node.path("invl_val").asText("");
            if (!intrinsicValueStr.isEmpty()) {
                // 내재가치는 0도 유효한 값 (OTM 옵션의 경우 0)
                option.setIntrinsicValue(new BigDecimal(intrinsicValueStr.replace(",", "")));
            }

            String timeValueStr = node.path("tmvl_val").asText("");
            if (!timeValueStr.isEmpty()) {
                // 시간가치도 0 이상의 모든 값이 유효
                option.setTimeValue(new BigDecimal(timeValueStr.replace(",", "")));
            }

            // Greeks - API에서 값이 있으면 파싱하되, 0.0000은 null로 처리 (의미 없는 값)
            // 휴장일에는 모든 Greeks가 0.0000으로 오므로 null로 저장
            String deltaStr = node.path("delta_val").asText("");
            log.debug("Symbol {}: delta_val from API = '{}'", symbol, deltaStr);
            if (!deltaStr.isEmpty()) {
                try {
                    BigDecimal deltaValue = new BigDecimal(deltaStr.replace(",", ""));
                    // 완전히 0이 아닌 값만 저장
                    if (deltaValue.compareTo(BigDecimal.ZERO) != 0) {
                        option.setDelta(deltaValue);
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid delta value for {}: {}", symbol, deltaStr);
                }
            }

            String gammaStr = node.path("gama").asText(""); // API 필드명: gama (오타)
            log.debug("Symbol {}: gama from API = '{}'", symbol, gammaStr);
            if (!gammaStr.isEmpty()) {
                try {
                    BigDecimal gammaValue = new BigDecimal(gammaStr.replace(",", ""));
                    if (gammaValue.compareTo(BigDecimal.ZERO) != 0) {
                        option.setGamma(gammaValue);
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid gamma value for {}: {}", symbol, gammaStr);
                }
            }

            String vegaStr = node.path("vega").asText("");
            log.debug("Symbol {}: vega from API = '{}'", symbol, vegaStr);
            if (!vegaStr.isEmpty()) {
                try {
                    BigDecimal vegaValue = new BigDecimal(vegaStr.replace(",", ""));
                    if (vegaValue.compareTo(BigDecimal.ZERO) != 0) {
                        option.setVega(vegaValue);
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid vega value for {}: {}", symbol, vegaStr);
                }
            }

            String thetaStr = node.path("theta").asText("");
            log.debug("Symbol {}: theta from API = '{}'", symbol, thetaStr);
            if (!thetaStr.isEmpty()) {
                try {
                    BigDecimal thetaValue = new BigDecimal(thetaStr.replace(",", ""));
                    if (thetaValue.compareTo(BigDecimal.ZERO) != 0) {
                        option.setTheta(thetaValue);
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid theta value for {}: {}", symbol, thetaStr);
                }
            }

            String rhoStr = node.path("rho").asText("");
            if (!rhoStr.isEmpty()) {
                try {
                    BigDecimal rhoValue = new BigDecimal(rhoStr.replace(",", ""));
                    if (rhoValue.compareTo(BigDecimal.ZERO) != 0) {
                        option.setRho(rhoValue);
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid rho value for {}: {}", symbol, rhoStr);
                }
            }

            String ivStr = node.path("hts_ints_vltl").asText("0");
            if (!"0".equals(ivStr) && !ivStr.isEmpty()) {
                option.setImpliedVolatility(new BigDecimal(ivStr.replace(",", "")));
            }

            // 기초자산 가격 (KOSPI200 지수)
            BigDecimal underlyingPrice = symbolMasterService.getCurrentKospi200Index();
            option.setUnderlyingPrice(underlyingPrice);

            return option;

        } catch (Exception e) {
            log.warn("전광판 데이터 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 개별 선물 시세 조회
     */
    private FuturesData fetchFuturesPrice(String token, String code, LocalDateTime timestamp) {
        try {
            // 야간장/주간장 구분하여 올바른 시장코드 사용
            String marketCode = isNightSession() ? "CM" : "F"; // CM: 야간선물, F: 주간선물

            String url = config.getBaseUrl() +
                    "/uapi/domestic-futureoption/v1/quotations/inquire-price" +
                    "?FID_COND_MRKT_DIV_CODE=" + marketCode +
                    "&FID_INPUT_ISCD=" + code;

            log.debug("Fetching futures {} with market code: {}", code, marketCode);

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
            // 야간장/주간장 구분하여 올바른 시장코드 사용
            String marketCode = isNightSession() ? "EU" : "O"; // EU: 야간옵션, O: 주간옵션

            String url = config.getBaseUrl() +
                    "/uapi/domestic-futureoption/v1/quotations/inquire-price" +
                    "?FID_COND_MRKT_DIV_CODE=" + marketCode +
                    "&FID_INPUT_ISCD=" + code;

            log.debug("Fetching option {} with market code: {}", code, marketCode);

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

                    // 종목명: API 응답이 비어있으면 심볼에서 생성
                    String korName = output1.path("hts_kor_isnm").asText("");
                    if (korName == null || korName.isEmpty()) {
                        korName = SymbolMasterService.generateOptionName(code, type);
                    }
                    option.setName(korName);

                    // 행사가: API 응답이 0이면 심볼 코드에서 추출
                    String strikeStr = output1.path("acpr").asText("0");
                    BigDecimal strikePrice = new BigDecimal(strikeStr.replace(",", ""));

                    // API가 0을 반환하면 심볼 코드에서 파싱 (야간장 대응)
                    if (strikePrice.compareTo(BigDecimal.ZERO) == 0) {
                        strikePrice = SymbolMasterService.parseStrikePrice(code);
                        log.debug("⚠ API 행사가=0, 심볼에서 추출: {} → {}", code, strikePrice);
                    }
                    option.setStrikePrice(strikePrice);

                    // 만기일: 심볼 코드에서 추출
                    String expiryMonth = SymbolMasterService.parseExpiryMonth(code);
                    if (expiryMonth != null) {
                        // 만기일은 해당 월의 두 번째 목요일로 설정 (추후 정확한 계산 가능)
                        option.setExpiryDate(expiryMonth + "-15"); // 간단히 15일로 설정
                    }

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

                    // 이론가 (hts_thpr) - 괴리율 9999.99이면 계산 불가 (야간장 OTM 옵션 등)
                    String theoreticalPriceStr = output1.path("hts_thpr").asText("0");
                    BigDecimal theoreticalPrice = new BigDecimal(theoreticalPriceStr.replace(",", ""));
                    option.setTheoreticalPrice(
                            theoreticalPrice.compareTo(BigDecimal.ZERO) > 0 ? theoreticalPrice : null);

                    // 내재가치/시간가치는 REST API(FHMIF10000000)에 없음
                    // WebSocket 실시간 시세(H0IOCNT0)에서만 제공됨:
                    // - fields[26] = INVL_VAL (내재가치 값)
                    // - fields[27] = TMVL_VAL (시간가치 값)
                    option.setIntrinsicValue(null);
                    option.setTimeValue(null);

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
            // 1. 메모리 캐시 확인
            if (accessToken != null && tokenExpiry != null && LocalDateTime.now().isBefore(tokenExpiry)) {
                log.debug("Using cached access token (expires at {})", tokenExpiry);
                return accessToken;
            }

            // 2. 파일 캐시 확인
            String cachedToken = loadTokenFromFile();
            if (cachedToken != null) {
                log.info("✓ Using cached token from file (expires at {})", tokenExpiry);
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
                accessToken = root.get("access_token").asText();
                int expiresIn = root.get("expires_in").asInt();
                tokenExpiry = LocalDateTime.now().plusSeconds(expiresIn);

                // 파일에 저장
                saveTokenToFile(accessToken, tokenExpiry);

                log.info("✓ Access token obtained successfully! Expires in {} seconds", expiresIn);
                return accessToken;
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

                throw new RuntimeException(
                        "Failed to get access token: " + response.statusCode() + " - " + responseBody);
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error getting access token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get access token", e);
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

            // 메모리에 로드
            accessToken = token;
            tokenExpiry = expiry;
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
        if (code.equals("A01612"))
            return "KOSPI200 선물 12월물";
        return code;
    }

    /**
     * WebSocket 접속을 위한 Approval Key 발급
     * 실시간 시세 조회를 위한 인증키
     * 캐싱: kis_approval_key.cache 파일에 저장하여 재사용 (24시간 유효)
     */
    public String getApprovalKey() {
        try {
            // 1. 파일 캐시에서 유효한 approval key 확인
            String cachedApprovalKey = loadApprovalKeyFromFile();
            if (cachedApprovalKey != null) {
                log.info("✓ 캐시된 Approval Key 사용 (재사용)");
                return cachedApprovalKey;
            }

            // 2. 새로운 approval key 발급
            log.info("🔑 WebSocket Approval Key 발급 요청...");

            String url = config.getBaseUrl() + "/oauth2/Approval";

            Map<String, String> body = new HashMap<>();
            body.put("grant_type", "client_credentials");
            body.put("appkey", config.getAppKey());
            body.put("secretkey", config.getAppSecret());

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String approvalKey = root.path("approval_key").asText("");

                if (!approvalKey.isEmpty()) {
                    // 파일에 저장 (24시간 유효)
                    saveApprovalKeyToFile(approvalKey);
                    log.info("✓ Approval Key 발급 성공 (캐시 저장)");
                    return approvalKey;
                } else {
                    log.error("Approval Key가 응답에 없습니다: {}", response.body());
                }
            } else {
                log.error("Approval Key 발급 실패: {} - {}", response.statusCode(), response.body());
            }

        } catch (Exception e) {
            log.error("Approval Key 발급 중 오류: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * Approval Key를 파일에 저장 (24시간 유효)
     */
    private void saveApprovalKeyToFile(String approvalKey) {
        try {
            LocalDateTime expiry = LocalDateTime.now().plusHours(24);
            String keyData = approvalKey + "|" + expiry.toString();
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of("kis_approval_key.cache"),
                    keyData,
                    java.nio.charset.StandardCharsets.UTF_8);
            log.debug("Approval Key 캐시 저장 완료 (만료: {})", expiry);
        } catch (Exception e) {
            log.warn("Approval Key 캐시 저장 실패: {}", e.getMessage());
        }
    }

    /**
     * 파일에서 Approval Key 로드 (24시간 이내)
     */
    private String loadApprovalKeyFromFile() {
        try {
            java.nio.file.Path path = java.nio.file.Path.of("kis_approval_key.cache");
            if (!java.nio.file.Files.exists(path)) {
                return null;
            }

            String keyData = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
            String[] parts = keyData.split("\\|");
            if (parts.length != 2) {
                return null;
            }

            String approvalKey = parts[0];
            LocalDateTime expiry = LocalDateTime.parse(parts[1]);

            // 만료 체크
            if (LocalDateTime.now().isAfter(expiry)) {
                log.debug("캐시된 Approval Key 만료: {}", expiry);
                return null;
            }

            log.debug("캐시된 Approval Key 로드 성공 (만료: {})", expiry);
            return approvalKey;

        } catch (Exception e) {
            log.debug("Approval Key 캐시 로드 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 과거 특정일 데이터 조회 (선물옵션기간별시세 API 사용)
     * 
     * @param tradingDate 조회할 날짜 (yyyyMMdd 형식)
     */
    public void loadHistoricalData(String tradingDate) {
        try {
            log.info("Loading historical data for date: {}", tradingDate);

            String token = getAccessToken();
            if (token == null) {
                log.warn("Failed to get access token");
                return;
            }

            // 선물 데이터 먼저 조회하여 KOSPI200 지수 추정
            BigDecimal kospi200Index = loadHistoricalFutures(token, tradingDate);

            // 선물 가격으로 KOSPI200 추정 (휴장일에 API 실패 대비)
            if (kospi200Index == null || kospi200Index.compareTo(BigDecimal.ZERO) == 0) {
                kospi200Index = symbolMasterService.getCurrentKospi200Index();
                log.info("Using current KOSPI200 index: {}", kospi200Index);
            } else {
                log.info("Estimated KOSPI200 from futures price: {}", kospi200Index);
            }

            // 옵션 데이터 조회 (추정된 KOSPI200 사용)
            loadHistoricalOptions(token, tradingDate, kospi200Index);

        } catch (Exception e) {
            log.error("Error loading historical data: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load historical data", e);
        }
    }

    /**
     * 과거 선물 데이터 조회
     * 
     * @return 선물 종가 (KOSPI200 추정용)
     */
    private BigDecimal loadHistoricalFutures(String token, String tradingDate) {
        BigDecimal futuresPrice = null;
        try {
            List<FuturesData> futuresList = new ArrayList<>();
            List<String> futureCodes = symbolMasterService.getActiveFuturesCodes();

            for (String code : futureCodes) {
                try {
                    FuturesData futures = fetchHistoricalFuturesPrice(token, code, tradingDate);
                    if (futures != null) {
                        futuresList.add(futures);
                        // 첫 번째 선물의 KOSPI200 지수를 사용 (output1.kospi200_nmix)
                        if (futuresPrice == null && futures.getUnderlyingPrice() != null) {
                            futuresPrice = futures.getUnderlyingPrice();
                            log.info("📊 KOSPI200 index from API: {}", futuresPrice);
                        }
                    }
                    Thread.sleep(100);
                } catch (Exception e) {
                    log.warn("Failed to fetch historical futures {}: {}", code, e.getMessage());
                }
            }

            if (!futuresList.isEmpty()) {
                futuresDataRepository.saveAll(futuresList);
                log.info("✓ Loaded {} historical futures", futuresList.size());
            }
        } catch (Exception e) {
            log.error("Error loading historical futures: {}", e.getMessage());
        }
        return futuresPrice;
    }

    /**
     * 과거 옵션 데이터 조회
     */
    private void loadHistoricalOptions(String token, String tradingDate, BigDecimal kospi200Index) {
        try {
            List<OptionData> optionsList = new ArrayList<>();
            log.info("Using KOSPI200 index for historical options: {}", kospi200Index);

            // ATM 위아래 5개씩 = 총 10~11개 행사가
            // strikeRange는 행사가 개수가 아니라 포인트 범위
            // 5개 * 2.5pt 간격 = 12.5pt 범위 → 안전하게 13으로 설정
            int strikeRange = 13; // ATM ± 13pt (약 5개 행사가)
            BigDecimal strikeInterval = BigDecimal.valueOf(2.5);

            List<OptionCodeInfo> optionCodeInfos = symbolMasterService.getActiveOptionCodes(
                    kospi200Index, strikeRange, strikeInterval);
            List<String> optionCodes = optionCodeInfos.stream()
                    .map(info -> info.code)
                    .toList();

            log.info("Generated {} option codes for historical data", optionCodes.size());

            for (String code : optionCodes) {
                try {
                    OptionData option = fetchHistoricalOptionPrice(token, code, tradingDate, kospi200Index);
                    if (option != null) {
                        optionsList.add(option);
                    }
                    Thread.sleep(100);
                } catch (Exception e) {
                    log.warn("Failed to fetch historical option {}: {}", code, e.getMessage());
                }
            }

            if (!optionsList.isEmpty()) {
                optionDataRepository.saveAll(optionsList);
                log.info("✓ Loaded {} historical options", optionsList.size());
            }
        } catch (Exception e) {
            log.error("Error loading historical options: {}", e.getMessage());
        }
    }

    /**
     * 과거 선물 가격 조회
     */
    private FuturesData fetchHistoricalFuturesPrice(String token, String code, String tradingDate) {
        try {
            String url = config.getBaseUrl() + "/uapi/domestic-futureoption/v1/quotations/inquire-daily-fuopchartprice";

            String queryString = String.format(
                    "FID_COND_MRKT_DIV_CODE=F&FID_INPUT_ISCD=%s&FID_INPUT_DATE_1=%s&FID_INPUT_DATE_2=%s&FID_PERIOD_DIV_CODE=D",
                    code, tradingDate, tradingDate);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "?" + queryString))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + token)
                    .header("appkey", config.getAppKey())
                    .header("appsecret", config.getAppSecret())
                    .header("tr_id", "FHKIF03020100")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());

                // output1에서 KOSPI200 지수 및 미결제약정 추출
                JsonNode output1 = root.path("output1");
                String kospi200Index = output1.path("kospi200_nmix").asText("");
                String openInterestStr = output1.path("hts_otst_stpl_qty").asText("0");

                JsonNode output2 = root.path("output2");
                if (output2.isArray() && output2.size() > 0) {
                    // 마지막 데이터 (최근일) 사용
                    JsonNode lastData = output2.get(output2.size() - 1);
                    return parseHistoricalFuturesData(lastData, code, kospi200Index, openInterestStr);
                }
            }

            return null;
        } catch (Exception e) {
            log.debug("Failed to fetch historical futures {}: {}", code, e.getMessage());
            return null;
        }
    }

    /**
     * 과거 옵션 가격 조회
     */
    private OptionData fetchHistoricalOptionPrice(String token, String code, String tradingDate,
            BigDecimal underlyingPrice) {
        try {
            String url = config.getBaseUrl() + "/uapi/domestic-futureoption/v1/quotations/inquire-daily-fuopchartprice";

            String queryString = String.format(
                    "FID_COND_MRKT_DIV_CODE=O&FID_INPUT_ISCD=%s&FID_INPUT_DATE_1=%s&FID_INPUT_DATE_2=%s&FID_PERIOD_DIV_CODE=D",
                    code, tradingDate, tradingDate);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "?" + queryString))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + token)
                    .header("appkey", config.getAppKey())
                    .header("appsecret", config.getAppSecret())
                    .header("tr_id", "FHKIF03020100")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());

                // output1에서 미결제약정 추출
                JsonNode output1 = root.path("output1");
                String openInterestStr = output1.path("hts_otst_stpl_qty").asText("0");

                JsonNode output2 = root.path("output2");
                if (output2.isArray() && output2.size() > 0) {
                    // 마지막 데이터 (최근일) 사용
                    JsonNode lastData = output2.get(output2.size() - 1);
                    return parseHistoricalOptionData(lastData, code, underlyingPrice, openInterestStr);
                }
            }

            return null;
        } catch (Exception e) {
            log.debug("Failed to fetch historical option {}: {}", code, e.getMessage());
            return null;
        }
    }

    /**
     * 과거 선물 데이터 파싱
     */
    private FuturesData parseHistoricalFuturesData(JsonNode data, String code, String kospi200Index,
            String openInterestStr) {
        try {
            FuturesData futures = new FuturesData();
            futures.setSymbol(code);
            futures.setName("KOSPI200 선물");

            // 기간별시세 API output2 필드명: futs_prpr (현재가/종가)
            String closingPrice = data.path("futs_prpr").asText("0");
            log.info("📊 Historical futures {}: closing price = {}", code, closingPrice);

            futures.setCurrentPrice(new BigDecimal(closingPrice.replace(",", "")));
            futures.setChangeAmount(BigDecimal.ZERO); // 기간별시세 API에는 전일대비 필드 없음
            futures.setChangePercent(BigDecimal.ZERO);
            futures.setOpenPrice(new BigDecimal(data.path("futs_oprc").asText("0").replace(",", "")));
            futures.setHighPrice(new BigDecimal(data.path("futs_hgpr").asText("0").replace(",", "")));
            futures.setLowPrice(new BigDecimal(data.path("futs_lwpr").asText("0").replace(",", "")));
            futures.setVolume(Long.parseLong(data.path("acml_vol").asText("0").replace(",", "")));

            // 거래대금 계산
            String tradingValueStr = data.path("acml_tr_pbmn").asText("0");
            futures.setTradingValue(new BigDecimal(tradingValueStr.replace(",", "")));

            // 미결제약정 (주간장 상품의 경우 output1에서 추출)
            try {
                long openInterest = Long.parseLong(openInterestStr.replace(",", ""));
                futures.setOpenInterest(openInterest);
                log.debug("📊 Open Interest for {}: {}", code, openInterest);
            } catch (Exception e) {
                futures.setOpenInterest(0L);
            }

            futures.setTimestamp(LocalDateTime.now());

            // KOSPI200 지수 저장 (output1.kospi200_nmix)
            if (kospi200Index != null && !kospi200Index.isEmpty()) {
                try {
                    futures.setUnderlyingPrice(new BigDecimal(kospi200Index.replace(",", "")));
                } catch (Exception e) {
                    log.warn("Failed to parse KOSPI200 index: {}", kospi200Index);
                }
            }

            return futures;
        } catch (Exception e) {
            log.warn("Failed to parse historical futures data: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 과거 옵션 데이터 파싱
     */
    private OptionData parseHistoricalOptionData(JsonNode data, String code, BigDecimal underlyingPrice,
            String openInterestStr) {
        try {
            OptionData option = new OptionData();
            option.setSymbol(code);
            option.setOptionType(code.startsWith("B") ? OptionType.CALL : OptionType.PUT);

            // 기간별시세 API output2 필드명: futs_prpr (현재가/종가)
            String closingPrice = data.path("futs_prpr").asText("0");
            String volume = data.path("acml_vol").asText("0");
            String tradingValueStr = data.path("acml_tr_pbmn").asText("0");

            option.setCurrentPrice(new BigDecimal(closingPrice.replace(",", "")));
            option.setVolume(Long.parseLong(volume.replace(",", "")));
            option.setTradingValue(new BigDecimal(tradingValueStr.replace(",", "")));

            // 미결제약정 (output1에서 추출)
            try {
                long openInterest = Long.parseLong(openInterestStr.replace(",", ""));
                option.setOpenInterest(openInterest);
            } catch (Exception e) {
                option.setOpenInterest(0L);
            }

            option.setTimestamp(LocalDateTime.now());

            // KOSPI200 기초자산 가격 설정 (대시보드 표시용)
            option.setUnderlyingPrice(underlyingPrice);

            // 행사가 추출 (코드에서)
            // 코드 형식: "C01601350" = C(Put) + 01 + 601(2026년1월) + 350(행사가)
            // substring(6) = "350" -> 350.0 포인트
            try {
                String strikeStr = code.substring(6); // "C01601350" -> "350"
                option.setStrikePrice(new BigDecimal(strikeStr));
            } catch (Exception e) {
                option.setStrikePrice(BigDecimal.ZERO);
            }

            return option;
        } catch (Exception e) {
            log.warn("Failed to parse historical option data: {}", e.getMessage());
            return null;
        }
    }
}
