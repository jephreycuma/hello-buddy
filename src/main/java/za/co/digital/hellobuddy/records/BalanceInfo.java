package za.co.digital.hellobuddy.records;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BalanceInfo(
    BigDecimal oldBalance,
    BigDecimal newBalance,
    BigDecimal cost,
    String currencyCode,
    String currencyName,
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime updatedAt
) {}
