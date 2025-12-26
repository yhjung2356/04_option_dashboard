package com.trading.dashboard.repository;

import com.trading.dashboard.model.OptionData;
import com.trading.dashboard.model.OptionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OptionDataRepository extends JpaRepository<OptionData, Long> {

    List<OptionData> findByOptionTypeOrderByStrikePriceAsc(OptionType optionType);

    List<OptionData> findByTimestampAfter(LocalDateTime timestamp);

    @Query("SELECT SUM(o.volume) FROM OptionData o WHERE o.optionType = :optionType")
    Long sumVolumeByOptionType(OptionType optionType);

    @Query("SELECT SUM(o.openInterest) FROM OptionData o WHERE o.optionType = :optionType")
    Long sumOpenInterestByOptionType(OptionType optionType);

    @Query("SELECT SUM(o.tradingValue) FROM OptionData o WHERE o.optionType = :optionType")
    BigDecimal sumTradingValueByOptionType(OptionType optionType);

    @Query("SELECT o FROM OptionData o ORDER BY o.volume DESC LIMIT :limit")
    List<OptionData> findTopByVolumeDesc(int limit);

    @Query("SELECT o FROM OptionData o ORDER BY o.tradingValue DESC LIMIT :limit")
    List<OptionData> findTopByTradingValueDesc(int limit);

    @Query("SELECT o FROM OptionData o ORDER BY o.openInterest DESC LIMIT :limit")
    List<OptionData> findTopByOpenInterestDesc(int limit);

    @Query("SELECT o FROM OptionData o ORDER BY o.strikePrice ASC")
    List<OptionData> findAllOrderByStrikePrice();

    List<OptionData> findByStrikePrice(BigDecimal strikePrice);

    java.util.Optional<OptionData> findBySymbol(String symbol);
}
