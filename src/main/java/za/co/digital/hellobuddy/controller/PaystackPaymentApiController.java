package za.co.digital.hellobuddy.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/paystack")
public class PaystackPaymentApiController {
	private static final Logger logger = Logger.getLogger(PaystackPaymentApiController.class.getName());

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${paystack.api.key}")
    private String paystackApiKey;

    @Value("${paystack.initialize.url}")
    private String paystackUrl;

    @Value("${hello.buddy.url}")
    private String helloBuddyUrl;

    @Value("${stripe.fixed.fee}")
    private String stripeFixedFee;

    @Value("${stripe.percentage.fee}")
    private String stripePercentageFee;
    @Value("${strip.minimum.amount}")
    private String stripeMinimumAmount;
    @Value("${customer.default.email}")
    private String defaultCustomerEmail;
    @Value("${south.african.fx}")
    private String southAfricanFx;

    @PostMapping("/create-session")
    public ResponseEntity<Map<String, Object>> processPaystackSession(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();

        try {
        	logger.info("Hello Buddy frontend received Paystack payload: " + payload);

            // Extract customer email (Paystack strictly requires this)
            //String customerEmail = (String) payload.getOrDefault("recipientEmail", defaultCustomerEmail);
            
            String customerEmail = (String) payload.get("recipientEmail");
            if (customerEmail == null || customerEmail.trim().isEmpty()) {
                customerEmail = defaultCustomerEmail;
            }
            
            String originalLocalPrice = (String) payload.getOrDefault("originalPrice", "0.0"); 
            String currentRegion = (String) payload.getOrDefault("countryIso", "ZA"); 
            String productName = (String) payload.getOrDefault("productName", "Hello Buddy Voucher");
            //String currencyCode = (String) payload.getOrDefault("currency", "ZAR").toString().toUpperCase();
            String currencyCode = "ZAR";

            // Your existing Redis FX logic
            String currencySymbol = redisTemplate.opsForValue().get(currentRegion);
            String redisKey = "fx:" + currentRegion.toUpperCase() + "_" + (currencySymbol != null ? currencySymbol.toUpperCase() : "ZAR");
            String cachedFx = redisTemplate.opsForValue().get(redisKey);
            String discount = redisTemplate.opsForValue().get(currentRegion + ":" + payload.getOrDefault("productId", ""));
            String southAfricaFx = redisTemplate.opsForValue().get("fx:ZA_ZAR");
            
            if (southAfricaFx == null || southAfricaFx.trim().isEmpty()) {
				southAfricaFx = southAfricanFx;
			}
            
            logger.info("Retrieved FX from Redis for " + redisKey + ": " + cachedFx);
            logger.info("South African FX from Redis for : " + southAfricaFx);
            
            BigDecimal fxRate = (cachedFx != null) ? new BigDecimal(cachedFx) : BigDecimal.ONE;
            BigDecimal discountPct = (discount != null) ? new BigDecimal(discount) : BigDecimal.ZERO;
            BigDecimal localPrice = new BigDecimal(originalLocalPrice);
            
            BigDecimal finalizedChargeAmount = calculatePayStackCharge(localPrice, fxRate,new BigDecimal(southAfricaFx));
            
            // Paystack expects amount in cents/sub-units (e.g., R10.50 -> 1050)
            long paystackAmountCents = Math.round(finalizedChargeAmount.setScale(2, RoundingMode.HALF_UP).doubleValue() * 100);

            // Construct Metadata Payload
            Map<String, String> metadata = new HashMap<>();
            metadata.put("productId", (String) payload.getOrDefault("productId", ""));
            metadata.put("productName", productName);
            metadata.put("category", (String) payload.getOrDefault("category", ""));
            metadata.put("senderPhone", (String) payload.getOrDefault("senderPhone", ""));
            metadata.put("recipientPhone", (String) payload.getOrDefault("recipientPhone", ""));
            metadata.put("countryIso", currentRegion);
            metadata.put("originalPrice", originalLocalPrice);
            metadata.put("checkoutPriceUsd", finalizedChargeAmount.toString());

            // Construct Paystack API Request Body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("email", customerEmail);
            requestBody.put("amount", paystackAmountCents);
            requestBody.put("currency", currencyCode); // Supports ZAR natively
            requestBody.put("callback_url", helloBuddyUrl + "/success");
            requestBody.put("metadata", metadata);

            // Set headers for Paystack Authorization
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + this.paystackApiKey);
            headers.set("Cache-Control", "no-cache");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Post to Paystack Endpoint
            ResponseEntity<Map> apiResponse = restTemplate.postForEntity(paystackUrl, entity, Map.class);
            
            if (apiResponse.getStatusCode() == HttpStatus.OK && apiResponse.getBody() != null) {
                Map<String, Object> body = apiResponse.getBody();
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                
                // Return parameters matching what your frontend is accustomed to handling
                response.put("success", true);
                response.put("stripeUrl", data.get("authorization_url")); // Front-end can still read this redirect property
                return ResponseEntity.ok(response);
            }

            response.put("success", false);
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            System.err.println("Paystack session initialization fault: " + e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    private BigDecimal calculateStripeCharge(BigDecimal originalLocalPrice, BigDecimal fxRate, BigDecimal reloadlyDiscount) {
        BigDecimal STRIPE_FIXED = new BigDecimal(stripeFixedFee);
        BigDecimal STRIPE_INVERSE_PERCENT = BigDecimal.ONE.subtract(new BigDecimal(stripePercentageFee));
        
        if (reloadlyDiscount == null)
        	reloadlyDiscount = BigDecimal.ZERO;
        
        if (reloadlyDiscount.compareTo(BigDecimal.ONE) > 0) {
            reloadlyDiscount = reloadlyDiscount.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        }

        BigDecimal baseCostUsd = originalLocalPrice
                .divide(fxRate, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.ONE.subtract(reloadlyDiscount));

        BigDecimal derivedAmount = baseCostUsd.add(STRIPE_FIXED)
                .divide(STRIPE_INVERSE_PERCENT, 2, RoundingMode.HALF_UP);
        
        BigDecimal minimumAmount = new BigDecimal(stripeMinimumAmount);
        if (derivedAmount.compareTo(minimumAmount) < 0) {
			derivedAmount = minimumAmount;
		}
        return derivedAmount;
    }
    


    /**
     * Calculates the exact final PayStack charge in ZAR matching the storefront display price.
     *
     * @param storefrontLocalPrice The exact display price shown to the user on the storefront (e.g., R7.00, R110.00, or foreign denomination).
     * @param localToUsdFxRate     FX rate from local currency to USD (set to 1.0 if local currency is ZAR or USD).
     * @param usdToZarFxRate       FX rate from USD to ZAR (set to 1.0 if local currency is ZAR).
     * @return Exact ZAR charge to be sent to PayStack (e.g., 7.00 or 110.00).
     */
    private BigDecimal calculatePayStackCharge(
            BigDecimal storefrontLocalPrice,
            BigDecimal localToUsdFxRate,
            BigDecimal usdToZarFxRate) {

        // 1. Safety check for null or non-positive prices
        if (storefrontLocalPrice == null || storefrontLocalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // 2. Default FX rates to 1.0 if missing or invalid
        if (localToUsdFxRate == null || localToUsdFxRate.compareTo(BigDecimal.ZERO) <= 0) {
            localToUsdFxRate = BigDecimal.ONE;
        }

        if (usdToZarFxRate == null || usdToZarFxRate.compareTo(BigDecimal.ZERO) <= 0) {
            usdToZarFxRate = BigDecimal.ONE;
        }

        // 3. Direct ZAR Product: Return exact storefront price without modifications
        if (localToUsdFxRate.compareTo(BigDecimal.ONE) == 0 && usdToZarFxRate.compareTo(BigDecimal.ONE) == 0) {
            return storefrontLocalPrice.setScale(2, RoundingMode.HALF_UP);
        }

        // 4. Foreign Product: Convert Local -> USD -> ZAR using exact storefront face value
        BigDecimal usdPrice = storefrontLocalPrice.divide(localToUsdFxRate, 6, RoundingMode.HALF_UP);
        BigDecimal finalAmountZar = usdPrice.multiply(usdToZarFxRate);

        // 5. Return exact rounded price to send to PayStack
        return finalAmountZar.setScale(2, RoundingMode.HALF_UP);
    }
}