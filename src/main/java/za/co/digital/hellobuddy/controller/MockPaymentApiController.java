package za.co.digital.hellobuddy.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

@RestController
@RequestMapping("/api/stripe")
public class MockPaymentApiController {

    @Value("${stripe.api.key}")
    private String stripeApiKey;
    @Value("${hello.buddy.url}")
    private String helloBuddyUrl;

    @PostMapping("/create-session")
    public ResponseEntity<Map<String, Object>> processStripeSession(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            System.out.println("Hello Buddy frontend received checkout payload: " + payload);
            
            Stripe.apiKey = this.stripeApiKey;
            String productPriceUsd = payload.get("price").toString(); 
            String originalLocalPrice = (String) payload.getOrDefault("originalPrice", "0.0"); 
            String currentRegion = (String) payload.getOrDefault("countryIso", "ZA"); 
            String productName = (String) payload.getOrDefault("productName", "Hello Buddy Voucher");
            String currencyCode = (String) payload.getOrDefault("currency", "zar");
            
            double orderAmount = 0.0;
            Object priceObj = payload.get("price");
            if (priceObj != null && !priceObj.toString().trim().isEmpty()) {
                orderAmount = Double.parseDouble(priceObj.toString());
            } else {
                response.put("success", false);
                response.put("message", "Validation Failed: Product price property is missing.");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Clean up Country ISO fallback based on incoming currency parameters
            String resolvedCountryIso = currentRegion;
            if (currencyCode.equalsIgnoreCase("ngn")) {
                resolvedCountryIso = "NG";
            } else if (currencyCode.equalsIgnoreCase("zar")) {
                resolvedCountryIso = "ZA";
            }

            Map<String, String> metadata = new HashMap<>();
            metadata.put("productId", (String) payload.getOrDefault("productId", ""));
            metadata.put("productName", productName);
            metadata.put("checkoutPriceUsd", productPriceUsd); 
            metadata.put("originalPrice", originalLocalPrice);
            metadata.put("category", (String) payload.getOrDefault("category", ""));
            metadata.put("senderPhone", (String) payload.getOrDefault("senderPhone", ""));
            metadata.put("recipientPhone", (String) payload.getOrDefault("recipientPhone", ""));
            metadata.put("recipientEmail", (String) payload.getOrDefault("recipientEmail", ""));
            metadata.put("countryIso", resolvedCountryIso); // FIXED: No longer blindly overwrites to "ZA"

            long stripeAmount = Math.round(orderAmount * 100);
            SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(helloBuddyUrl + "/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(helloBuddyUrl + "/")
                .putAllMetadata(metadata)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currencyCode.toLowerCase()) // Dynamically processes ngn, zar, usd, etc.
                                .setUnitAmount(stripeAmount)
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(productName)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build();

            Session session = Session.create(params);

            response.put("success", true);
            response.put("stripeUrl", session.getUrl()); 
            return ResponseEntity.ok(response);

        } catch (StripeException e) {
            System.err.println("Stripe SDK Gateway Exception: " + e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        } catch (Exception e) {
            System.err.println("Internal payload conversion fault: " + e.getMessage());
            response.put("success", false);
            return ResponseEntity.internalServerError().body(response);
        }
    }
}