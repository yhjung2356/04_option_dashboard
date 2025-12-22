package com.trading.dashboard.service;

import com.trading.dashboard.model.OptionData;
import com.trading.dashboard.model.OptionType;
import com.trading.dashboard.repository.OptionDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 파일에서 옵션 데이터를 로드하는 서비스
 * KIS API가 작동하지 않을 때 대체 데이터 소스로 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsvDataLoaderService {

    private final OptionDataRepository optionDataRepository;

    /**
     * CSV 파일에서 KOSPI200 옵션 데이터 로드
     * 
     * @param filePath CSV 파일 경로 (예: "data/20251219_KOSPI옵션시세표.csv")
     */
    public void loadOptionsFromCsv(String filePath) {
        log.info("Loading options data from CSV file: {}", filePath);

        try {
            List<OptionData> optionsList = parseCsvFile(filePath);
            
            if (!optionsList.isEmpty()) {
                optionDataRepository.saveAll(optionsList);
                log.info("✓ Loaded {} options from CSV file", optionsList.size());
            } else {
                log.warn("No valid data found in CSV file");
            }
            
        } catch (Exception e) {
            log.error("Failed to load CSV data: {}", e.getMessage(), e);
        }
    }

    /**
     * CSV 파일 파싱
     * 
     * CSV 구조:
     * - CALL: 미결제, 거래량, 전일대비, 등락, 델타, 내재변동성, 행사가, 현재가
     * - PUT: 현재가, 등락, 전일대비, 거래량, 미결제
     */
    private List<OptionData> parseCsvFile(String filePath) throws IOException {
        List<OptionData> options = new ArrayList<>();
        LocalDateTime timestamp = LocalDateTime.now();

        // CSV 파일 읽기 (여러 인코딩 시도)
        String[] encodings = {"UTF-8", "EUC-KR", "MS949"};
        BufferedReader reader = null;
        
        for (String encoding : encodings) {
            try {
                ClassPathResource resource = new ClassPathResource(filePath);
                reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), Charset.forName(encoding))
                );
                break;
            } catch (Exception e) {
                log.debug("Failed to read with encoding {}: {}", encoding, e.getMessage());
            }
        }

        if (reader == null) {
            throw new IOException("Could not read CSV file with any encoding");
        }

        String line;
        int lineNumber = 0;
        int skipHeader = 2; // 헤더 2줄 건너뛰기

        while ((line = reader.readLine()) != null) {
            lineNumber++;
            
            // 헤더 건너뛰기
            if (lineNumber <= skipHeader) {
                continue;
            }

            try {
                OptionData callOption = parseCallOption(line, timestamp);
                OptionData putOption = parsePutOption(line, timestamp);

                if (callOption != null) {
                    options.add(callOption);
                }
                if (putOption != null) {
                    options.add(putOption);
                }

            } catch (Exception e) {
                log.warn("Failed to parse line {}: {} - Error: {}", lineNumber, line, e.getMessage());
            }
        }

        reader.close();
        return options;
    }

    /**
     * CSV 라인에서 CALL 옵션 파싱
     * 
     * CSV 컬럼 (CALL 부분):
     * 0: 빈칸
     * 1: 미결제 (CALL)
     * 2: 거래량 (CALL)
     * 3: 전일대비 (CALL)
     * 4: 등락부호 (CALL)
     * 5: 델타 (CALL)
     * 6: 내재변동성 (CALL)
     * 7: 행사가
     * 8: 현재가 (CALL)
     */
    private OptionData parseCallOption(String line, LocalDateTime timestamp) {
        String[] columns = line.split(",");
        
        if (columns.length < 9) {
            return null;
        }

        try {
            // 행사가
            String strikeStr = cleanValue(columns[7]);
            if (strikeStr.isEmpty() || strikeStr.equals("0")) {
                return null;
            }
            BigDecimal strikePrice = new BigDecimal(strikeStr);

            // 현재가 (CALL)
            String callPriceStr = cleanValue(columns[8]);
            if (callPriceStr.isEmpty() || callPriceStr.equals("0")) {
                return null;
            }
            BigDecimal currentPrice = new BigDecimal(callPriceStr);

            // 거래량 (CALL)
            String volumeStr = cleanValue(columns[2]);
            long volume = volumeStr.isEmpty() ? 0 : Long.parseLong(volumeStr);

            // 미결제약정 (CALL)
            String oiStr = cleanValue(columns[1]);
            long openInterest = oiStr.isEmpty() ? 0 : Long.parseLong(oiStr);

            // 내재변동성 (CALL)
            String ivStr = cleanValue(columns[6]);
            BigDecimal impliedVolatility = ivStr.isEmpty() ? BigDecimal.ZERO : new BigDecimal(ivStr);

            // OptionData 생성
            OptionData option = new OptionData();
            option.setSymbol("CALL_%d".formatted(strikePrice.intValue()));
            option.setStrikePrice(strikePrice);
            option.setOptionType(OptionType.CALL);
            option.setCurrentPrice(currentPrice);
            option.setVolume(volume);
            option.setOpenInterest(openInterest);
            option.setImpliedVolatility(impliedVolatility);
            option.setTimestamp(timestamp);

            return option;

        } catch (Exception e) {
            log.debug("Failed to parse CALL option: {}", e.getMessage());
            return null;
        }
    }

    /**
     * CSV 라인에서 PUT 옵션 파싱
     * 
     * CSV 컬럼 (PUT 부분):
     * 7: 행사가
     * 9: 현재가 (PUT)
     * 10: 등락부호 (PUT)
     * 11: 전일대비 (PUT)
     * 12: 거래량 (PUT)
     * 13: 미결제 (PUT)
     */
    private OptionData parsePutOption(String line, LocalDateTime timestamp) {
        String[] columns = line.split(",");
        
        if (columns.length < 14) {
            return null;
        }

        try {
            // 행사가
            String strikeStr = cleanValue(columns[7]);
            if (strikeStr.isEmpty() || strikeStr.equals("0")) {
                return null;
            }
            BigDecimal strikePrice = new BigDecimal(strikeStr);

            // 현재가 (PUT)
            String putPriceStr = cleanValue(columns[9]);
            if (putPriceStr.isEmpty() || putPriceStr.equals("0")) {
                return null;
            }
            BigDecimal currentPrice = new BigDecimal(putPriceStr);

            // 거래량 (PUT)
            String volumeStr = cleanValue(columns[12]);
            long volume = volumeStr.isEmpty() ? 0 : Long.parseLong(volumeStr);

            // 미결제약정 (PUT)
            String oiStr = cleanValue(columns[13]);
            long openInterest = oiStr.isEmpty() ? 0 : Long.parseLong(oiStr);

            // OptionData 생성
            OptionData option = new OptionData();
            option.setSymbol("PUT_%d".formatted(strikePrice.intValue()));
            option.setStrikePrice(strikePrice);
            option.setOptionType(OptionType.PUT);
            option.setCurrentPrice(currentPrice);
            option.setVolume(volume);
            option.setOpenInterest(openInterest);
            option.setImpliedVolatility(BigDecimal.ZERO); // CSV에 없음
            option.setTimestamp(timestamp);

            return option;

        } catch (Exception e) {
            log.debug("Failed to parse PUT option: {}", e.getMessage());
            return null;
        }
    }

    /**
     * CSV 값 정제 (따옴표, 쉼표, 공백 제거)
     */
    private String cleanValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "")
                    .replace(",", "")
                    .replace("+", "")
                    .trim();
    }
}
