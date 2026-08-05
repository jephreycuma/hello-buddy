package za.co.digital.hellobuddy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import za.co.digital.hellobuddy.dto.CheckoutRequestDTO;
import za.co.digital.hellobuddy.dto.ReloadlyTopupResult;
import za.co.digital.hellobuddy.dto.TopupResponse;
import za.co.digital.hellobuddy.model.CustomerWallet;
import za.co.digital.hellobuddy.repository.CustomerWalletRepository;
import za.co.digital.hellobuddy.service.CustomerWalletService;
import za.co.digital.hellobuddy.service.ProfitValidator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/reloadly")
public class ReloadlyPaymentController {

    private static final Logger logger = Logger.getLogger(ReloadlyPaymentController.class.getName());

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ProfitValidator profitValidator;

    @Autowired
    private CustomerWalletRepository walletRepository;

    @Autowired
    private CustomerWalletService walletService;

    @Value("${south.african.fx:15.35}")
    private String southAfricanFx;

    private final RestClient restClient;

    public ReloadlyPaymentController() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000); // 5s connect timeout
        requestFactory.setReadTimeout(20000);    // 20s read timeout

        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:8081")
                .requestFactory(requestFactory)
                .build();
    }

    @PostMapping("/wallet-topup")
    public ResponseEntity<?> topupCustomerMSISDN(@RequestBody CheckoutRequestDTO requestDTO, HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        // 1. Verify User Authentication
        if (session == null || session.getAttribute("LOGGED_IN_CUSTOMER") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "FAILED", "message", "User session expired. Please log in again."));
        }

        String username = (String) session.getAttribute("LOGGED_IN_CUSTOMER");

        // 2. Fetch Customer Wallet Record
        Optional<CustomerWallet> walletOptional = walletRepository.findByUsername(username);
        if (walletOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "FAILED", "message", "Wallet record not found for user."));
        }

        CustomerWallet customerWallet = walletOptional.get();
        BigDecimal currentDbBalance = customerWallet.getWalletBalance() != null 
                ? customerWallet.getWalletBalance() 
                : BigDecimal.ZERO;

        String countryIso = requestDTO.getCountryIso() != null ? requestDTO.getCountryIso().toUpperCase() : "ZA";

        // 3. Strict Validation on Original Price (Fail Fast)
        if (requestDTO.getOriginalPrice() == null || requestDTO.getOriginalPrice().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "FAILED", "message", "Invalid or missing originalPrice."));
        }

        BigDecimal localPrice;
        try {
            localPrice = new BigDecimal(requestDTO.getOriginalPrice().trim());
            if (localPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", "FAILED", "message", "Original price must be greater than zero."));
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "FAILED", "message", "Invalid price format."));
        }

        // 4. Fetch FX Rates from Redis
        String currencySymbol = redisTemplate.opsForValue().get(countryIso);
        String redisKey = "fx:" + countryIso + "_" + currencySymbol + "_" + requestDTO.getProductId();
        String fxRateStr = redisTemplate.opsForValue().get(redisKey);

        BigDecimal localToUsdFxRate = BigDecimal.ONE;
        if (fxRateStr != null && !fxRateStr.trim().isEmpty()) {
            try {
                localToUsdFxRate = new BigDecimal(fxRateStr.trim());
            } catch (NumberFormatException e) {
                logger.warning("Invalid FX rate in Redis for key [" + redisKey + "]: " + fxRateStr);
            }
        } else if (!"ZA".equalsIgnoreCase(countryIso) && !"US".equalsIgnoreCase(countryIso)) {
            logger.severe("Missing critical Redis FX key [" + redisKey + "] for foreign country " + countryIso);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "FAILED", "message", "Exchange rate unavailable. Please try again later."));
        }

        // 5. South African FX Rate Property
        String saFxRate = redisTemplate.opsForValue().get("fx:ZA_ZAR");
        if (saFxRate == null || saFxRate.trim().isEmpty()) {
            saFxRate = southAfricanFx;
        }

        BigDecimal usdToZarFxRate = BigDecimal.ONE;
        try {
            usdToZarFxRate = new BigDecimal(saFxRate.trim());
        } catch (Exception e) {
            logger.warning("Invalid south.african.fx property value: " + saFxRate + ". Defaulting to 1.0");
        }

        // 6. Calculate Charge in ZAR
        BigDecimal amountInZar = profitValidator.calculatePayStackCharge(localPrice, localToUsdFxRate, usdToZarFxRate);

        // 7. Validate Sufficient Wallet Funds
        if (currentDbBalance.compareTo(amountInZar) < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "status", "FAILED",
                            "message", String.format("Insufficient wallet balance (Available: R %.2f, Required: R %.2f).", currentDbBalance, amountInZar)
                    ));
        }

        // 8. Sanitize Inputs
        int productId;
        try {
            productId = Integer.parseInt(requestDTO.getProductId());
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "FAILED", "message", "Invalid productId format."));
        }

        String rawSender = requestDTO.getSenderPhone() != null ? requestDTO.getSenderPhone() : "0";
        String rawRecipient = requestDTO.getRecipientPhone();

        if (rawRecipient == null || rawRecipient.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "FAILED", "message", "Recipient phone number is required."));
        }

        String cleanSender = rawSender.replaceAll("\\D", "");
        String cleanReceiver = rawRecipient.replaceAll("\\D", "");

        String txReference = "WLT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // 9. Deduct Balance via Proxy-Managed Service
        BigDecimal newDeductedBalance;
        try {
            newDeductedBalance = walletService.deductWallet(username, amountInZar);
            session.setAttribute("WALLET_BALANCE", newDeductedBalance);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "FAILED", "message", "Wallet transaction failed: " + e.getMessage()));
        }

        // Calculate precise USD cost for Reloadly
        BigDecimal localPriceInUsd = profitValidator.convertCountryPriceToUsd(localPrice, localToUsdFxRate);

        ReloadlyTopupResult results;

        try {
            // 10. Dispatch Top-Up to Reloadly Microservice
            results = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/telecom/topups")
                            .queryParam("amount", localPriceInUsd.setScale(2, RoundingMode.HALF_UP).doubleValue())
                            .queryParam("senderPhone", Long.parseLong(cleanSender.isEmpty() ? "0" : cleanSender))
                            .queryParam("receiverPhone", Long.parseLong(cleanReceiver))
                            .queryParam("countryISO", countryIso)
                            .queryParam("operatorId", productId)
                            .queryParam("senderEmail", requestDTO.getRecipientEmail())
                            .queryParam("useLocalAmount", false)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<ReloadlyTopupResult>() {});

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Reloadly API timeout or error: " + e.getMessage(), e);

            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(Map.of(
                            "status", "PENDING",
                            "message", "Top-up request sent but confirmation timed out. Balance updated.",
                            "transactionId", txReference,
                            "newBalance", newDeductedBalance
                    ));
        }

        // 11. Process HTTP Response from Reloadly
        if (results != null && results.isSuccessful()) {
            TopupResponse successData = results.getTopupResponse();

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Top-up completed successfully!",
                    "transactionId", successData != null ? successData.getTransactionId() : txReference,
                    "newBalance", newDeductedBalance
            ));
        } else {
            // Safe Refund via Proxy-Managed Service
            BigDecimal restoredBalance = walletService.reverseWalletDeduction(username, amountInZar);
            session.setAttribute("WALLET_BALANCE", restoredBalance);

            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of(
                            "status", "FAILED",
                            "message", "Reloadly fulfillment failed. Funds returned to wallet.",
                            "newBalance", restoredBalance
                    ));
        }
    }
}