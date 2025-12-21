package com.trading.dashboard.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.trading.dashboard.config.KisApiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 한국투자증권 WebSocket 실시간 시세 수신
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KisWebSocketService {

    private final KisApiConfig config;
    private final KisApiService kisApiService;
    private final Gson gson = new Gson();
    private WebSocketClient client;

    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("Initializing KIS WebSocket Service...");
        log.info("========================================");
    }

    /**
     * WebSocket 연결 시작
     */
    public void connect() {
        try {
            // WebSocket URL (실전투자)
            String wsUrl = "ws://ops.koreainvestment.com:21000";
            
            log.info("Connecting to KIS WebSocket: {}", wsUrl);

            client = new WebSocketClient(new URI(wsUrl)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("✓ KIS WebSocket connected!");
                    
                    // 접속 후 승인 요청
                    try {
                        sendApprovalRequest();
                        
                        // 선물 시세 구독
                        subscribeFutures("A0163000");  // 3월물
                        
                        // 옵션 시세 구독 (주요 ATM)
                        subscribeOption("B0161565");  // 콜 400
                        subscribeOption("C0161565");  // 풋 400
                        
                    } catch (Exception e) {
                        log.error("Failed to subscribe: {}", e.getMessage());
                    }
                }

                @Override
                public void onMessage(String message) {
                    handleRealtimeData(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("KIS WebSocket closed: {} - {}", code, reason);
                }

                @Override
                public void onError(Exception ex) {
                    log.error("KIS WebSocket error: {}", ex.getMessage());
                }
            };

            client.connect();
            
        } catch (Exception e) {
            log.error("Failed to connect KIS WebSocket: {}", e.getMessage(), e);
        }
    }

    /**
     * 승인 요청
     */
    private void sendApprovalRequest() throws Exception {
        String token = kisApiService.getAccessToken();
        
        Map<String, Object> approval = new HashMap<>();
        approval.put("header", Map.of(
            "approval_key", token,
            "custtype", "P",  // 개인
            "tr_type", "1",   // 등록
            "content-type", "utf-8"
        ));
        
        String approvalJson = gson.toJson(approval);
        client.send(approvalJson);
        
        log.info("✓ Sent approval request");
    }

    /**
     * 선물 시세 구독
     */
    private void subscribeFutures(String code) {
        Map<String, Object> request = new HashMap<>();
        
        Map<String, String> header = new HashMap<>();
        header.put("tr_id", "H0STCNT0");  // 선물 실시간 체결가
        header.put("tr_key", code);
        
        request.put("header", header);
        request.put("body", Map.of("input", Map.of("tr_id", "H0STCNT0", "tr_key", code)));
        
        String requestJson = gson.toJson(request);
        client.send(requestJson);
        
        log.info("✓ Subscribed to futures: {}", code);
    }

    /**
     * 옵션 시세 구독
     */
    private void subscribeOption(String code) {
        Map<String, Object> request = new HashMap<>();
        
        Map<String, String> header = new HashMap<>();
        header.put("tr_id", "H0STCNI0");  // 옵션 실시간 체결가
        header.put("tr_key", code);
        
        request.put("header", header);
        request.put("body", Map.of("input", Map.of("tr_id", "H0STCNI0", "tr_key", code)));
        
        String requestJson = gson.toJson(request);
        client.send(requestJson);
        
        log.info("✓ Subscribed to option: {}", code);
    }

    /**
     * 실시간 데이터 처리
     */
    private void handleRealtimeData(String message) {
        try {
            // KIS WebSocket 응답 파싱
            JsonObject json = gson.fromJson(message, JsonObject.class);
            
            if (json.has("header")) {
                String trId = json.getAsJsonObject("header").get("tr_id").getAsString();
                
                if ("H0STCNT0".equals(trId)) {
                    // 선물 체결 데이터
                    handleFuturesData(json);
                } else if ("H0STCNI0".equals(trId)) {
                    // 옵션 체결 데이터
                    handleOptionData(json);
                }
            }
            
        } catch (Exception e) {
            log.debug("Received message: {}", message);
        }
    }

    /**
     * 선물 실시간 데이터 처리
     */
    private void handleFuturesData(JsonObject json) {
        try {
            JsonObject body = json.getAsJsonObject("body");
            String code = body.get("MKSC_SHRN_ISCD").getAsString();
            String price = body.get("STCK_PRPR").getAsString();
            String volume = body.get("CNTG_VOL").getAsString();
            
            log.info("📈 [FUTURES] {} - Price: {}, Volume: {}", code, price, volume);
            
            // TODO: DB 업데이트 또는 WebSocket 브로드캐스트
            
        } catch (Exception e) {
            log.debug("Error handling futures data: {}", e.getMessage());
        }
    }

    /**
     * 옵션 실시간 데이터 처리
     */
    private void handleOptionData(JsonObject json) {
        try {
            JsonObject body = json.getAsJsonObject("body");
            String code = body.get("MKSC_SHRN_ISCD").getAsString();
            String price = body.get("STCK_PRPR").getAsString();
            String volume = body.get("CNTG_VOL").getAsString();
            
            log.info("📊 [OPTION] {} - Price: {}, Volume: {}", code, price, volume);
            
            // TODO: DB 업데이트 또는 WebSocket 브로드캐스트
            
        } catch (Exception e) {
            log.debug("Error handling option data: {}", e.getMessage());
        }
    }

    /**
     * WebSocket 연결 해제
     */
    @PreDestroy
    public void disconnect() {
        if (client != null && client.isOpen()) {
            client.close();
            log.info("KIS WebSocket disconnected");
        }
    }
}
