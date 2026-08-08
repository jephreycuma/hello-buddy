package za.co.digital.hellobuddy.records;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import za.co.digital.hellobuddy.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Transaction(
    Long transactionId,
    TransactionStatus status,
    String operatorTransactionId,
    String customIdentifier,
    String recipientPhone,
    String recipientEmail,
    String senderPhone,
    String countryCode,
    Long operatorId,
    String operatorName,
    BigDecimal discount,
    String discountCurrencyCode,
    BigDecimal requestedAmount,
    String requestedAmountCurrencyCode,
    BigDecimal deliveredAmount,
    String deliveredAmountCurrencyCode,
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime transactionDate,
    
    PinDetail pinDetail,
    BigDecimal fee,
    BalanceInfo balanceInfo
) {}
