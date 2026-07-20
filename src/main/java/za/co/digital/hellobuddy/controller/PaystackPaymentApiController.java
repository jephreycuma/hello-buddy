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
            
            BigDecimal finalizedChargeAmount = calculatePayStackCharge(localPrice, fxRate,new BigDecimal(southAfricaFx), discountPct);
            
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
    
    private BigDecimal calculatePayStackCharge( BigDecimal originalLocalPrice, BigDecimal localToUsdFxRate, BigDecimal usdToZarFxRate,BigDecimal reloadlyDiscount) {
    	    BigDecimal STRIPE_FIXED = new BigDecimal(stripeFixedFee);
    	    BigDecimal STRIPE_INVERSE_PERCENT = BigDecimal.ONE.subtract(new BigDecimal(stripePercentageFee));
    	    
    	    if (reloadlyDiscount == null) {
    	        reloadlyDiscount = BigDecimal.ZERO;
    	    }
    	    
    	    if (reloadlyDiscount.compareTo(BigDecimal.ONE) > 0) {
    	        reloadlyDiscount = reloadlyDiscount.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
    	    }

    	    // 1. Convert local airtime price (MZN) to USD using the local FX rate
    	    BigDecimal baseCostUsd = originalLocalPrice
    	            .divide(localToUsdFxRate, 4, RoundingMode.HALF_UP)
    	            .multiply(BigDecimal.ONE.subtract(reloadlyDiscount));

    	    // 2. Add PayStack/Stripe processing markup in USD
    	    BigDecimal derivedAmountUsd = baseCostUsd.add(STRIPE_FIXED)
    	            .divide(STRIPE_INVERSE_PERCENT, 4, RoundingMode.HALF_UP); 
    	    
    	    BigDecimal minimumAmountUsd = new BigDecimal(stripeMinimumAmount);
    	    if (derivedAmountUsd.compareTo(minimumAmountUsd) < 0) {
    	        derivedAmountUsd = minimumAmountUsd;
    	    }
    	    
    	    // 3. CORRECTED: Convert USD to ZAR using the proper ZAR exchange rate
    	    BigDecimal finalAmountZar = derivedAmountUsd.multiply(usdToZarFxRate).setScale(2, RoundingMode.HALF_UP);
    	    
    	    return finalAmountZar;
    	}
}