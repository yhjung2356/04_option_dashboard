package com.trading.dashboard.exception;

/**
 * 데이터 조회 실패 시 발생하는 예외
 */
public class DataFetchException extends RuntimeException {

    private final String source;
    private final String symbol;

    public DataFetchException(String message, String source, String symbol) {
        super(String.format("%s from %s for symbol %s", message, source, symbol));
        this.source = source;
        this.symbol = symbol;
    }

    public DataFetchException(String message, String source, String symbol, Throwable cause) {
        super(String.format("%s from %s for symbol %s", message, source, symbol), cause);
        this.source = source;
        this.symbol = symbol;
    }

    public String getSource() {
        return source;
    }

    public String getSymbol() {
        return symbol;
    }
}
