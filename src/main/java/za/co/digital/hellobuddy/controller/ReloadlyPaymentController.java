package za.co.digital.hellobuddy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import za.co.digital.hellobuddy.dto.CheckoutRequestDTO;
import za.co.digital.hellobuddy.dto.ReloadlyTopupResult;
import za.co.digital.hellobuddy.dto.TopupResponse;
import za.co.digital.hellobuddy.model.CustomerWallet;
import za.co.digital.hellobuddy.repository.CustomerWalletRepository;
import za.co.digital.hellobuddy.service.ProfitValidator;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    @Value("${south.african.fx:15.35}")
    private String southAfricanFx;

    // Reloadly internal communication client
    private final RestClient restClient = RestClient.builder()
            .baseUrl("http://localhost:8081")
            .build();

    @PostMapping("/wallet-topup")
    @Transactional
    public ResponseEntity<?> topupCustomerMSISDN(@RequestBody CheckoutRequestDTO requestDTO, HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        // 1. Verify User Authentication
        if (session == null || session.getAttribute("LOGGED_IN_CUSTOMER") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "FAILED", "message", "User session expired. Please log in again."));
        }

        String username = (String) session.getAttribute("LOGGED_IN_CUSTOMER");

        // 2. Fetch Customer Wallet Entity and Balance from Database
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

        // 3. Safely Parse Local Price
        BigDecimal localPrice = BigDecimal.ONE;
        if (requestDTO.getOriginalPrice() != null && !requestDTO.getOriginalPrice().trim().isEmpty()) {
            try {
                localPrice = new BigDecimal(requestDTO.getOriginalPrice().trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid originalPrice format: " + requestDTO.getOriginalPrice() + ". Defaulting to 1.0");
            }
        }

        // 4. Fetch FX Rate from Redis with Defensive Null Guards
        String currencySymbol = redisTemplate.opsForValue().get(countryIso);
        String redisKey = "fx:" + countryIso.toUpperCase() + "_" + currencySymbol+"_"+requestDTO.getProductId();
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

        // 5. Safely Parse South African FX Rate Property
        String saFxRate = (redisTemplate.opsForValue().get("fx:ZA_ZAR") != null && !redisTemplate.opsForValue().get("fx:ZA_ZAR").isEmpty())
                ? redisTemplate.opsForValue().get("fx:ZA_ZAR") 
                : southAfricanFx;
        
        BigDecimal usdToZarFxRate = BigDecimal.ONE;
        if (saFxRate != null && !saFxRate.trim().isEmpty()) {
            try {
                usdToZarFxRate = new BigDecimal(saFxRate.trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid south.african.fx property value: " + saFxRate + ". Defaulting to 1.0");
            }
        }
        
        logger.info("Using FX rates - Local to USD: " + localToUsdFxRate + ", USD to ZAR: " + usdToZarFxRate);

        // 6. Calculate Exact Amount Due in ZAR
        BigDecimal amountInZar = profitValidator.calculatePayStackCharge(localPrice, localToUsdFxRate, usdToZarFxRate);

        // 7. Validate Sufficient Wallet Funds against Database Balance
        if (currentDbBalance.compareTo(amountInZar) < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "status", "FAILED",
                            "message", String.format("Insufficient wallet balance (Available: R %.2f, Required: R %.2f). Please top up your wallet.", currentDbBalance, amountInZar)
                    ));
        }

        // 8. Extract and Clean Phone Inputs
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
            // 9. Dispatch Top-Up to Reloadly Microservice
            ReloadlyTopupResult results = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/telecom/topups")
                            .queryParam("amount", originalPrice)
                            .queryParam("senderPhoone", Long.parseLong(cleanSender.isEmpty() ? "0" : cleanSender))
                            .queryParam("receiverPhone", Long.parseLong(cleanReceiver))
                            .queryParam("countryISO", countryIso)
                            .queryParam("operatorId", productId)
                            .queryParam("senderEmail", requestDTO.getRecipientEmail())
                            .queryParam("useLocalAmount", true)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<ReloadlyTopupResult>() {});

            // 10. Handle Fulfillment Response & Update DB and Session
            if (results != null && results.isSuccessful()) {
                TopupResponse successData = results.getTopupResponse();

                // Compute exact new balance
                BigDecimal newBalance = currentDbBalance.subtract(amountInZar);

                // Update database
                customerWallet.setWalletBalance(newBalance);
                walletRepository.save(customerWallet);

                // Update HTTP Session so the portal UI gets updated immediately
                session.setAttribute("WALLET_BALANCE", newBalance);

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