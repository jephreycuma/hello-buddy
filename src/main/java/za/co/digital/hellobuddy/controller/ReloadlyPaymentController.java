package za.co.digital.hellobuddy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import za.co.digital.hellobuddy.dto.CheckoutRequestDTO;
import za.co.digital.hellobuddy.dto.ReloadlyTopupResult;
import za.co.digital.hellobuddy.dto.TopupResponse;
import za.co.digital.hellobuddy.service.ProfitValidator;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/reloadly")
public class ReloadlyPaymentController {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ProfitValidator profitValidator;

    @Value("${south.african.fx:15.35}")
    private String southAfricanFx;

    // Reloadly internal communication client
    private final RestClient restClient = RestClient.builder()
            .baseUrl("http://localhost:8081")
            .build();

    @PostMapping("/wallet-topup")
    public ResponseEntity<?> topupCustomerMSISDN(@RequestBody CheckoutRequestDTO requestDTO, HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        // 1. Verify User Authentication
        if (session == null || session.getAttribute("LOGGED_IN_CUSTOMER") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "FAILED", "message", "User session expired. Please log in again."));
        }

        String username = (String) session.getAttribute("LOGGED_IN_CUSTOMER");

        // 2. Fetch Wallet Balance
        Object rawBalance = session.getAttribute("WALLET_BALANCE");
        double walletBalance = 0.0;

        if (rawBalance instanceof Number number) {
            walletBalance = number.doubleValue();
        } else if (rawBalance instanceof String str) {
            try {
                walletBalance = Double.parseDouble(str);
            } catch (NumberFormatException ignored) {}
        }

        String countryIso = requestDTO.getCountryIso() != null ? requestDTO.getCountryIso().toUpperCase() : "ZA";

        // 4. Safely Parse Local Price
        BigDecimal localPrice = BigDecimal.ONE;
        if (requestDTO.getOriginalPrice() != null && !requestDTO.getOriginalPrice().trim().isEmpty()) {
            try {
                localPrice = new BigDecimal(requestDTO.getOriginalPrice().trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid originalPrice format: " + requestDTO.getOriginalPrice() + ". Defaulting to 1.0");
            }
        }

        // 5. Fetch FX Rate from Redis with Defensive Null Guards
        String currencySymbol = redisTemplate.opsForValue().get(countryIso);
        String redisKey = "fx:" + countryIso.toUpperCase() + "_" + currencySymbol;
        String fxRateStr = redisTemplate.opsForValue().get(redisKey);

        BigDecimal localToUsdFxRate = BigDecimal.ONE;
        if (fxRateStr != null && !fxRateStr.trim().isEmpty()) {
            try {
                localToUsdFxRate = new BigDecimal(fxRateStr.trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid FX rate format in Redis for key [" + redisKey + "]: " + fxRateStr + ". Defaulting to 1.0");
            }
        } else {
            System.out.println("FX key [" + redisKey + "] missing in Redis. Defaulting local-to-USD rate to 1.0");
        }

        // 6. Safely Parse South African FX Rate Property
        String saFxRate = (redisTemplate.opsForValue().get("fx:ZA_ZAR")!=null && !redisTemplate.opsForValue().get("fx:ZA_ZAR").isEmpty())
        		?redisTemplate.opsForValue().get("fx:ZA_ZAR"):southAfricanFx ;
        BigDecimal usdToZarFxRate = BigDecimal.ONE;
        if (saFxRate != null && !saFxRate.trim().isEmpty()) {
            try {
                usdToZarFxRate = new BigDecimal(saFxRate.trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid south.african.fx property value: " + saFxRate + ". Defaulting to 1.0");
            }
        }

        // 7. Calculate Exact Amount Due in ZAR
        BigDecimal amountInZar = profitValidator.calculatePayStackCharge(localPrice, localToUsdFxRate, usdToZarFxRate);

        // 8. Validate Sufficient Wallet Funds
        if (walletBalance < amountInZar.doubleValue()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "status", "FAILED",
                            "message", String.format("Insufficient wallet balance (Available: R %.2f, Required: R %.2f). Please top up your wallet.", walletBalance, amountInZar)
                    ));
        }

        // 9. Extract and Clean Phone Inputs
        int productId = Integer.parseInt(requestDTO.getProductId());
        String rawSender = requestDTO.getSenderPhone() != null ? requestDTO.getSenderPhone() : "0";
        String rawRecipient = requestDTO.getRecipientPhone();

        String cleanSender = rawSender.replaceAll("\\D", "");
        String cleanReceiver = validatePhoneNumber(rawRecipient, countryIso).replaceAll("\\D", "");

        double originalPrice = requestDTO.getOriginalPrice() != null 
                ? Double.parseDouble(requestDTO.getOriginalPrice()) 
                : amountInZar.doubleValue();

        // Tracking reference
        String txReference = "WLT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try {
            // 10. Dispatch Top-Up to Reloadly Microservice
            ReloadlyTopupResult results = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/telecom/topups")
                            .queryParam("amount", originalPrice)
                            .queryParam("senderPhone", Long.parseLong(cleanSender.isEmpty() ? "0" : cleanSender))
                            .queryParam("receiverPhone", Long.parseLong(cleanReceiver))
                            .queryParam("countryISO", countryIso)
                            .queryParam("operatorId", productId)
                            .queryParam("senderEmail", requestDTO.getRecipientEmail())
                            .queryParam("useLocalAmount", true)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<ReloadlyTopupResult>() {});

            // 11. Handle Fulfillment Response & Deduct Wallet
            if (results != null && results.isSuccessful()) {
                TopupResponse successData = results.getTopupResponse();

                double newBalance = walletBalance - amountInZar.doubleValue();
                session.setAttribute("WALLET_BALANCE", BigDecimal.valueOf(newBalance));

                return ResponseEntity.ok(Map.of(
                        "status", "SUCCESS",
                        "message", "Top-up completed successfully!",
                        "transactionId", successData != null ? successData.getTransactionId() : txReference,
                        "newBalance", newBalance
                ));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of("status", "FAILED", "message", "Reloadly fulfillment failed. No funds were deducted from your wallet."));
            }

        } catch (Exception e) {
            System.err.println("Critical error executing wallet top-up: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "FAILED", "message", "System error processing top-up: " + e.getMessage()));
        }
    }

    private String validatePhoneNumber(String phone, String countryIso) {
        if (phone == null) return "";
        return phone.trim();
    }
}