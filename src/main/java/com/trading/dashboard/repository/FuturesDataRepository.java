package com.trading.dashboard.repository;

import com.trading.dashboard.model.FuturesData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FuturesDataRepository extends JpaRepository<FuturesData, Long> {

    List<FuturesData> findByTimestampAfter(LocalDateTime timestamp);

    @Query("SELECT SUM(f.volume) FROM FuturesData f")
    Long sumAllVolume();

    @Query("SELECT SUM(f.openInterest) FROM FuturesData f")
    Long sumAllOpenInterest();

    @Query("SELECT SUM(f.tradingValue) FROM FuturesData f")
    BigDecimal sumAllTradingValue();

    @Query("SELECT f FROM FuturesData f ORDER BY f.volume DESC LIMIT :limit")
    List<FuturesData> findTopByVolumeDesc(int limit);

    @Query("SELECT f FROM FuturesData f ORDER BY f.tradingValue DESC LIMIT :limit")
    List<FuturesData> findTopByTradingValueDesc(int limit);

    FuturesData findBySymbol(String symbol);

    java.util.Optional<FuturesData> findOptionalBySymbol(String symbol);
}
