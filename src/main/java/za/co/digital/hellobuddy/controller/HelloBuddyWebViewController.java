package za.co.digital.hellobuddy.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;

import za.co.digital.hellobuddy.dto.ReloadlyTopupResult;
import za.co.digital.hellobuddy.dto.TopupResponse;
import za.co.digital.hellobuddy.service.ProfitValidator;

@Controller
public class HelloBuddyWebViewController {

    private static final Logger logger = Logger.getLogger(HelloBuddyWebViewController.class.getName());

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ProfitValidator profitValidator;

    @Value("${paystack.api.key}")
    private String paystackSecretKey;

    @Value("${south.african.fx:15.35}")
    private String southAfricanFx;

    // Build RestClients with strict timeouts
    private final RestClient paystackClient;
    private final RestClient restClient;

    public HelloBuddyWebViewController() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(60000);
        requestFactory.setReadTimeout(60000);

        this.paystackClient = RestClient.builder()
                .baseUrl("https://api.paystack.co")
                .requestFactory(requestFactory)
                .build();

        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:8081")
                .requestFactory(requestFactory)
                .build();
    }

    @GetMapping("/success")
    public String paymentSuccess(@RequestParam("reference") String reference, Model model) {
        String txReference = reference;
        Object paystackNumericId = null;

        // Idempotency check: Protect against browser refresh double-fulfillment
        String processedKey = "processed_tx:" + txReference;
        Boolean isAlreadyProcessed = redisTemplate.hasKey(processedKey);
        if (Boolean.TRUE.equals(isAlreadyProcessed)) {
            logger.info("Transaction reference " + txReference + " was already processed. Rendering receipt.");
            model.addAttribute("errorMessage", "This transaction has already been processed.");
            return "receipt";
        }

        try {
            // 1. Verify transaction status via Paystack API
            Map<String, Object> paystackResponse = paystackClient.get()
                    .uri("/transaction/verify/{reference}", txReference)
                    .header("Authorization", "Bearer " + this.paystackSecretKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (paystackResponse == null || !Boolean.TRUE.equals(paystackResponse.get("status"))) {
                throw new IllegalStateException("Payment verification failed via Paystack gateway.");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) paystackResponse.get("data");
            String status = (String) data.get("status");
            paystackNumericId = data.get("id"); // Extract numeric ID for refunds

            if (!"success".equalsIgnoreCase(status)) {
                throw new IllegalStateException("Transaction status is: " + status);
            }

            @SuppressWarnings("unchecked")
            Map<String, String> metadata = (Map<String, String>) data.get("metadata");
            if (metadata == null) {
                metadata = new HashMap<>();
            }

            // Unpack Metadata
            Integer productId = Integer.parseInt(metadata.getOrDefault("productId", "0"));
            String name = metadata.getOrDefault("productName", "Hello Buddy Voucher");
            String senderPhone = metadata.getOrDefault("senderPhone", "0");
            String recipientPhone = metadata.getOrDefault("recipientPhone", "");
            String recipientEmail = metadata.getOrDefault("recipientEmail", "");
            String countryIso = metadata.getOrDefault("countryIso", "ZA");

            Double originalPrice = Double.parseDouble(metadata.getOrDefault("originalPrice", "0.0"));
            Double checkoutPriceUsd = Double.parseDouble(metadata.getOrDefault("checkoutPriceUsd", "0.0"));

            String cleanSender = senderPhone.replaceAll("\\D", "");
            String cleanReceiver = validatePhoneNumber(recipientPhone, countryIso).replaceAll("\\D", "");

            if (cleanSender.isEmpty()) {
                cleanSender = "0";
            }

            // Bind details to UI model
            String currencySymbol = redisTemplate.opsForValue().get(countryIso);
            model.addAttribute("productId", productId);
            model.addAttribute("productName", name);
            model.addAttribute("productPrice", originalPrice);
            model.addAttribute("currencySymbol", currencySymbol);
            model.addAttribute("chargedUsd", checkoutPriceUsd);
            model.addAttribute("phoneNumber", recipientPhone);
            model.addAttribute("sessionId", txReference);

            // Fetch FX rate
            String redisKey = "fx:" + countryIso.toUpperCase() + "_" + currencySymbol + "_" + productId;
            String fxRateStr = redisTemplate.opsForValue().get(redisKey);

            BigDecimal localToUsdFxRate = BigDecimal.ONE;
            if (fxRateStr != null && !fxRateStr.trim().isEmpty()) {
                try {
                    localToUsdFxRate = new BigDecimal(fxRateStr.trim());
                } catch (NumberFormatException e) {
                    logger.warning("Invalid FX rate in Redis for key [" + redisKey + "]: " + fxRateStr);
                }
            }

            BigDecimal localPriceBd = profitValidator.convertCountryPriceToUsd(new BigDecimal(originalPrice), localToUsdFxRate);

            // 2. Request Reloadly Delivery API
            ReloadlyTopupResult results = null;
            boolean requestTimedOut = false;

            try {
                final String finalCleanSender = cleanSender;
                final String finalCleanReceiver = cleanReceiver;

                results = restClient.post()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/v1/telecom/topups")
                                .queryParam("amount", localPriceBd.setScale(2, RoundingMode.HALF_UP).doubleValue())
                                .queryParam("senderPhone", Long.parseLong(finalCleanSender))
                                .queryParam("receiverPhone", Long.parseLong(finalCleanReceiver))
                                .queryParam("countryISO", countryIso)
                                .queryParam("operatorId", productId)
                                .queryParam("senderEmail", recipientEmail)
                                .queryParam("useLocalAmount", false)
                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<ReloadlyTopupResult>() {});

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Network/Timeout exception calling Reloadly: " + e.getMessage(), e);
                requestTimedOut = true;
            }

            // 3. Process Response
            if (results != null && results.isSuccessful()) {
                // Mark transaction as processed in Redis (24-hour expiration)
                redisTemplate.opsForValue().set(processedKey, "SUCCESS", 24, TimeUnit.HOURS);

                TopupResponse successData = results.getTopupResponse();
                model.addAttribute("referenceId", successData != null ? successData.getTransactionId() : txReference);

            } else if (requestTimedOut) {
                // DO NOT auto-refund on timeouts! Mark for manual review / webhook check
                logger.warning("Topup status UNKNOWN for ref: " + txReference + ". Skipping refund to avoid double loss.");
                model.addAttribute("errorMessage", "Your transaction is being processed. If your top-up isn't delivered within 5 minutes, please contact support with Reference: " + txReference);

            } else {
                // Explicit rejection from Reloadly API: Safe to refund
                logger.warning("Reloadly distribution failed explicitly. Initiating refund...");
                triggerPaystackRefund(paystackNumericId != null ? paystackNumericId.toString() : txReference, "Reloadly distribution failure.");
                model.addAttribute("errorMessage", "Delivery failed. We couldn't fulfill your voucher order, so your payment has been automatically reversed.");
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Runtime exception in paymentSuccess: " + e.getMessage(), e);
            model.addAttribute("errorMessage", "Processing Error: " + e.getMessage());
        }

        return "receipt";
    }

    /**
     * Executes a refund back to the customer via Paystack API.
     */
    private void triggerPaystackRefund(String transactionIdentifier, String reason) {
        if (transactionIdentifier == null || transactionIdentifier.isEmpty()) {
            logger.warning("Refund execution skipped: Missing transaction identifier.");
            return;
        }

        try {
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("transaction", transactionIdentifier);
            requestBody.put("merchant_note", reason);

            Map<String, Object> refundResponse = paystackClient.post()
                    .uri("/refund")
                    .header("Authorization", "Bearer " + this.paystackSecretKey)
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (refundResponse != null && Boolean.TRUE.equals(refundResponse.get("status"))) {
                logger.info("Paystack Refund Complete! Message: " + refundResponse.get("message"));
            } else {
                logger.warning("Paystack rejected refund execution: " + refundResponse);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "CRITICAL: Failed to refund user via Paystack: " + e.getMessage(), e);
        }
    }

    private String validatePhoneNumber(String recipientPhone, String countryIso) {
        if (recipientPhone == null) return "";
        recipientPhone = recipientPhone.replaceAll("\\D", "");
        if ("ZA".equalsIgnoreCase(countryIso) && !recipientPhone.startsWith("27") && recipientPhone.length() == 10) {
            recipientPhone = "27" + recipientPhone.substring(1);
        } else if ("NG".equalsIgnoreCase(countryIso) && !recipientPhone.startsWith("234") && recipientPhone.length() == 11) {
            recipientPhone = "234" + recipientPhone.substring(1);
        }
        return recipientPhone;
    }
}