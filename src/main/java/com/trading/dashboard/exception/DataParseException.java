package com.trading.dashboard.exception;

/**
 * JSON 파싱 실패 시 발생하는 예외
 */
public class DataParseException extends RuntimeException {

    private final String rawData;

    public DataParseException(String message, String rawData) {
        super(message);
        this.rawData = rawData;
    }

    public DataParseException(String message, String rawData, Throwable cause) {
        super(message, cause);
        this.rawData = rawData;
    }

    public String getRawData() {
        return rawData;
    }
}
