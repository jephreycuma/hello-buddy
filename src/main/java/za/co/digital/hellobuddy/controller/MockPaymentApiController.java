package za.co.digital.hellobuddy.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value; // Import this
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.stripe.Stripe; // Import this
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

@RestController
@RequestMapping("/api/stripe")
public class MockPaymentApiController {

    // 1. Inject the key directly from application.properties
    @Value("${stripe.api.key}")
    private String stripeApiKey;
    @Value("${hello.buddy.url}")
    private String helloBuddyUrl;

    @PostMapping("/create-session")
    public ResponseEntity<Map<String, Object>> processStripeSession(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            System.out.println("Hello Buddy frontend received checkout payload: " + payload);
            
            // 2. CRITICAL: Authenticate your Stripe SDK using your secret key
            Stripe.apiKey = this.stripeApiKey;
            String productPriceUsd = payload.get("price").toString(); // The $14.99 USD retail checkout amount
         // Safely fetch your custom raw original localized price sent from the frontend DOM attributes
            String originalLocalPrice = (String) payload.getOrDefault("originalPrice", "0.0"); 
            String currentRegion = (String) payload.getOrDefault("countryIso", "ZA"); // Storefront context ("ZA" or "NG")
            
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
            
            Map<String, String> metadata = new HashMap<>();
            metadata.put("productId", (String) payload.getOrDefault("productId", ""));
            metadata.put("productName", (String) payload.getOrDefault("productName", ""));
            metadata.put("checkoutPriceUsd", productPriceUsd); // Kept for bookkeeping/receipts
            metadata.put("originalPrice", originalLocalPrice);
            //metadata.put("price", priceObj != null ? priceObj.toString() : "0.0");
            metadata.put("category", (String) payload.getOrDefault("category", ""));
            metadata.put("senderPhone", (String) payload.getOrDefault("senderPhone", ""));
            metadata.put("recipientPhone", (String) payload.getOrDefault("recipientPhone", ""));
            metadata.put("recipientEmail", (String) payload.getOrDefault("recipientEmail", ""));
            metadata.put("countryIso", currentRegion);
            // Derive country code based on the storefront region
            metadata.put("countryIso", currencyCode.equalsIgnoreCase("ngn") ? "NG" : "ZA");

            // Build checkout session parameters
            long stripeAmount = Math.round(orderAmount * 100);
            SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(helloBuddyUrl+"/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(helloBuddyUrl+"/")
                .putAllMetadata(metadata)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currencyCode.toLowerCase())
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