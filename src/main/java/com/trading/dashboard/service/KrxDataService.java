package com.trading.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.dashboard.model.*;
import com.trading.dashboard.repository.FuturesDataRepository;
import com.trading.dashboard.repository.OptionDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * KRX(한국거래소) 실제 데이터 조회 서비스
 * KRX 정보데이터시스템 API를 통해 실제 거래 데이터를 가져옴
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KrxDataService {

    private final FuturesDataRepository futuresDataRepository;
    private final OptionDataRepository optionDataRepository;
    private final TradingCalendarService tradingCalendarService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // KRX 정보데이터시스템 API
    private static final String KRX_API_BASE = "http://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd";

    /**
     * 전거래일 데이터 로드
     */
    public void loadPreviousTradingDayData() {
        // TradingCalendarService를 통해 실제 전거래일 계산 (휴장일 고려)
        String tradingDate = tradingCalendarService.getPreviousTradingDay();

        log.info("========================================");
        log.info("Loading KRX data for date: {}", tradingDate);
        log.info("========================================");

        try {
            // KOSPI200 선물 데이터 로드
            loadFuturesData(tradingDate);

            // KOSPI200 옵션 데이터 로드
            loadOptionsData(tradingDate);

            log.info("========================================");
            log.info("KRX data load completed successfully!");
            log.info("========================================");

        } catch (Exception e) {
            log.error("Failed to load KRX data: {}", e.getMessage(), e);
            log.warn("Falling back to sample data generation...");
            // 실패 시 샘플 데이터 사용
        }
    }

    /**
     * KOSPI200 선물 데이터 로드
     */
    private void loadFuturesData(String tradingDate) throws IOException, InterruptedException {
        log.info("Loading KOSPI200 Futures data...");

        // KRX API 요청 (선물 시세)
        String requestBody = ("bld=dbms/MDC/STAT/standard/MDCSTAT30301" +
                "&locale=ko_KR" +
                "&trdDd=%s" +
                "&prodId=1" + // KOSPI200 선물
                "&share=1" +
                "&money=1").formatted(
                        tradingDate);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(KRX_API_BASE))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "http://data.krx.co.kr/")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("X-Requested-With", "XMLHttpRequest")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        log.info("Futures API status: {}", response.statusCode());
        String responseBody = response.body();
        log.info("Response body preview: {}", responseBody.substring(0, Math.min(500, responseBody.length())));

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode dataArray = root.get("OutBlock_1");

            if (dataArray != null && dataArray.isArray()) {
                List<FuturesData> futuresList = new ArrayList<>();
                LocalDateTime timestamp = LocalDateTime.now();

                for (JsonNode item : dataArray) {
                    try {
                        FuturesData futures = parseFuturesData(item, timestamp);
                        if (futures != null) {
                            futuresList.add(futures);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse futures data item: {}", e.getMessage());
                    }
                }

                if (!futuresList.isEmpty()) {
                    futuresDataRepository.saveAll(futuresList);
                    log.info("✓ Loaded {} KOSPI200 futures contracts", futuresList.size());
                } else {
                    log.warn("No futures data available, using sample data");
                }
            }
        } else {
            log.error("KRX API returned status: {}", response.statusCode());
        }
    }

    /**
     * KOSPI200 옵션 데이터 로드
     */
    private void loadOptionsData(String tradingDate) throws IOException, InterruptedException {
        log.info("Loading KOSPI200 Options data...");

        // KRX API 요청 (옵션 시세)
        String requestBody = ("bld=dbms/MDC/STAT/standard/MDCSTAT30401" +
                "&locale=ko_KR" +
                "&trdDd=%s" +
                "&prodId=1" + // KOSPI200 옵션
                "&share=1" +
                "&money=1").formatted(
                        tradingDate);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(KRX_API_BASE))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "http://data.krx.co.kr/")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("X-Requested-With", "XMLHttpRequest")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        log.info("Options API status: {}", response.statusCode());
        String responseBody = response.body();
        log.info("Response body preview: {}", responseBody.substring(0, Math.min(500, responseBody.length())));

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode dataArray = root.get("OutBlock_1");

            if (dataArray != null && dataArray.isArray()) {
                List<OptionData> optionsList = new ArrayList<>();
                LocalDateTime timestamp = LocalDateTime.now();

                for (JsonNode item : dataArray) {
                    try {
                        OptionData option = parseOptionData(item, timestamp);
                        if (option != null) {
                            optionsList.add(option);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse option data item: {}", e.getMessage());
                    }
                }

                if (!optionsList.isEmpty()) {
                    optionDataRepository.saveAll(optionsList);
                    log.info("✓ Loaded {} KOSPI200 option contracts", optionsList.size());
                } else {
                    log.warn("No options data available, using sample data");
                }
            }
        } else {
            log.error("KRX API returned status: {}", response.statusCode());
        }
    }

    /**
     * JSON 데이터를 FuturesData 객체로 변환
     */
    private FuturesData parseFuturesData(JsonNode item, LocalDateTime timestamp) {
        try {
            String symbol = item.get("ISU_SRT_CD").asText(); // 종목코드
            String name = item.get("ISU_ABBRV").asText(); // 종목명

            // 가격 정보
            BigDecimal currentPrice = new BigDecimal(item.get("TDD_CLSPRC").asText().replace(",", ""));
            BigDecimal changeAmount = new BigDecimal(item.get("CMPPREVDD_PRC").asText().replace(",", ""));
            BigDecimal changePercent = new BigDecimal(item.get("FLUC_RT").asText().replace(",", ""));

            // 거래 정보
            Long volume = Long.parseLong(item.get("ACC_TRDVOL").asText().replace(",", ""));
            BigDecimal tradingValue = new BigDecimal(item.get("ACC_TRDVAL").asText().replace(",", ""));
            Long openInterest = Long.parseLong(item.get("OPNINT_QTY").asText().replace(",", ""));

            // 가격 범위
            BigDecimal openPrice = new BigDecimal(item.get("TDD_OPNPRC").asText().replace(",", ""));
            BigDecimal highPrice = new BigDecimal(item.get("TDD_HGPRC").asText().replace(",", ""));
            BigDecimal lowPrice = new BigDecimal(item.get("TDD_LWPRC").asText().replace(",", ""));

            return FuturesData.builder()
                    .symbol(symbol)
                    .name(name)
                    .currentPrice(currentPrice)
                    .changeAmount(changeAmount)
                    .changePercent(changePercent)
                    .volume(volume)
                    .tradingValue(tradingValue)
                    .openInterest(openInterest)
                    .openPrice(openPrice)
                    .highPrice(highPrice)
                    .lowPrice(lowPrice)
                    .timestamp(timestamp)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing futures data: {}", e.getMessage());
            return null;
        }
    }

    /**
     * JSON 데이터를 OptionData 객체로 변환
     */
    private OptionData parseOptionData(JsonNode item, LocalDateTime timestamp) {
        try {
            String symbol = item.get("ISU_SRT_CD").asText();
            String optionTypeStr = item.get("ISU_CD").asText();

            // 콜/풋 구분
            OptionType optionType = optionTypeStr.contains("2") ? OptionType.CALL : OptionType.PUT;

            // 행사가
            BigDecimal strikePrice = new BigDecimal(item.get("X_PRC").asText().replace(",", ""));

            // 가격 정보
            BigDecimal currentPrice = new BigDecimal(item.get("TDD_CLSPRC").asText().replace(",", ""));

            // 거래 정보
            Long volume = Long.parseLong(item.get("ACC_TRDVOL").asText().replace(",", ""));
            BigDecimal tradingValue = new BigDecimal(item.get("ACC_TRDVAL").asText().replace(",", ""));
            Long openInterest = Long.parseLong(item.get("OPNINT_QTY").asText().replace(",", ""));

            // IV (내재변동성)
            BigDecimal iv = item.has("IMP_VLAT") ? new BigDecimal(item.get("IMP_VLAT").asText().replace(",", ""))
                    : BigDecimal.ZERO;

            return OptionData.builder()
                    .symbol(symbol)
                    .optionType(optionType)
                    .strikePrice(strikePrice)
                    .currentPrice(currentPrice)
                    .volume(volume)
                    .tradingValue(tradingValue)
                    .openInterest(openInterest)
                    .impliedVolatility(iv)
                    .timestamp(timestamp)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing option data: {}", e.getMessage());
            return null;
        }
    }
}
