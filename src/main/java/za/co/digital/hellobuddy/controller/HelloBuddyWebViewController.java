package za.co.digital.hellobuddy.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
import za.co.digital.hellobuddy.dto.ReloadlyTopupResult;
import za.co.digital.hellobuddy.dto.TopupResponse;

@Controller
public class HelloBuddyWebViewController {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    private final RestClient restClient = RestClient.builder()
            .baseUrl("http://localhost:8081") 
            .build();

    @GetMapping("/success")
    public String paymentSuccess(@RequestParam("session_id") String sessionId, Model model) {
        // Keep track of the payment intent id for possible reversal tracking
        String paymentIntentId = null;
        
        try {
            Stripe.apiKey = this.stripeApiKey;
            Session session = Session.retrieve(sessionId);
            paymentIntentId = session.getPaymentIntent(); // Capture the core transaction reference
            
            Map<String, String> metadata = session.getMetadata();

            // 1. Unpack identification records
            Integer id = Integer.parseInt(metadata.getOrDefault("productId", "0"));
            String name = metadata.getOrDefault("productName", "Hello Buddy Voucher");
            String category = metadata.getOrDefault("category", "Data");
            String senderPhone = metadata.getOrDefault("senderPhone", "");
            String recipientPhone = metadata.getOrDefault("recipientPhone", "");
            String recipientEmail = metadata.getOrDefault("recipientEmail", "");
            String countryIso = metadata.getOrDefault("countryIso", "ZA");

            // 2. Fetch Prices
            Double originalPrice = Double.parseDouble(metadata.getOrDefault("originalPrice", "0.0"));
            Double checkoutPriceUsd = Double.parseDouble(metadata.getOrDefault("checkoutPriceUsd", "0.0"));

            if (senderPhone == null || senderPhone.trim().isEmpty()) {
                senderPhone = "0";
            }

            recipientPhone = validatePhoneNumber(recipientPhone, countryIso);
            String cleanSender = senderPhone.replaceAll("\\D", ""); 
            String cleanReceiver = recipientPhone.replaceAll("\\D", ""); 

            // 3. Bind basic details to UI template upfront
            String currencySymbol = getCurrencySymbol(session.getCurrency(), countryIso);
            model.addAttribute("productId", id);
            model.addAttribute("productName", name);
            model.addAttribute("productPrice", originalPrice);     
            model.addAttribute("currencySymbol", currencySymbol);
            model.addAttribute("chargedUsd", checkoutPriceUsd);
            model.addAttribute("phoneNumber", recipientPhone);
            model.addAttribute("sessionId", sessionId);

            // 4. Request Reloadly Delivery API
            ReloadlyTopupResult results = null;
            try {
                results = restClient.post()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/v1/telecom/topups")
                                .queryParam("amount", originalPrice)
                                .queryParam("senderPhoone", Long.parseLong(cleanSender))
                                .queryParam("receiverPhone", Long.parseLong(cleanReceiver))
                                .queryParam("countryISO", countryIso)
                                .queryParam("operatorId", id)
                                .queryParam("senderEmail", recipientEmail)
                                .queryParam("useLocalAmount", true)
                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<ReloadlyTopupResult>() {});
            } catch (Exception e) {
                System.err.println("Network exception calling Reloadly: " + e.getMessage());
                // Fallthrough logic allows it to hit the refund validation layer below
            }

            // 5. Evaluate response & Trigger Stripe Reversal if order fails
            if (results != null && results.isSuccessful()) {
                TopupResponse successData = results.getTopupResponse();
                model.addAttribute("referenceId", successData.getTransactionId());
            } else {
                // Scenario A: Reloadly responded explicitly with an API failure error payload
                System.err.println("Reloadly API indicated fulfillment failure. Initiating automated Stripe refund workflow...");
                
                String refundReason = "Reloadly distribution failure.";
                triggerStripeRefund(paymentIntentId, refundReason);
                
                model.addAttribute("errorMessage", "Delivery failed. We couldn't fulfill your voucher order, so your payment has been automatically reversed.");
            }

        } catch (Exception e) {
            // Scenario B: System-wide processing script crash
            System.err.println("Critical controller runtime exception execution breakdown: " + e.getMessage());
            
            if (paymentIntentId != null) {
                triggerStripeRefund(paymentIntentId, "System runtime crash recovery refund.");
            }
            
            model.addAttribute("errorMessage", "Processing Error: " + e.getMessage() + ". Your payment has been queue-reversed.");
        }

        return "receipt";
    }

    /**
     * Executes an asynchronous full refund back to the customer's bank card via Stripe.
     */
    private void triggerStripeRefund(String paymentIntentId, String reason) {
        if (paymentIntentId == null || paymentIntentId.isEmpty()) {
            System.err.println("Refund execution skipped: Missing Payment Intent ID token link reference.");
            return;
        }

        try {
            Stripe.apiKey = this.stripeApiKey;
            
            Map<String, String> refundMetadata = new HashMap<>();
            refundMetadata.put("reason", reason);
            refundMetadata.put("automated", "true");

            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .putAllMetadata(refundMetadata)
                    .build();

            Refund refund = Refund.create(params);
            System.out.println("Stripe Reversal Complete! Refund processed successfully: ID = " + refund.getId() + " | Status = " + refund.getStatus());
            
        } catch (com.stripe.exception.StripeException se) {
            System.err.println("CRITICAL: Failed to reverse user funds via Stripe API API: " + se.getMessage());
        }
    }

    private String getCurrencySymbol(String stripeCurrency, String countryIso) {
        /*if (stripeCurrency != null && !stripeCurrency.isEmpty()) {
            switch (stripeCurrency.toLowerCase()) {
                case "zar": return "R";
                case "ngn": return "₦";
                case "usd": return "$";
                case "gbp": return "£";
                case "eur": return "€";
                case "kes": return "KSh";
                case "ghs": return "GH₵";
            }
        }*/
        if (countryIso != null) {
            switch (countryIso.toUpperCase()) {
                case "ZA": return "R";
                case "NG": return "₦";
                case "KE": return "KSh";
                case "GH": return "GH₵";
            }
        }
        return "R";
    }

    private String validatePhoneNumber(String recipientPhone, String countryIso) {
        recipientPhone = recipientPhone.replaceAll("\\D", ""); 
        if (countryIso.equalsIgnoreCase("ZA") && !recipientPhone.startsWith("27") && recipientPhone.length() == 10) {
            recipientPhone = "27" + recipientPhone.substring(1);
        } else if (countryIso.equalsIgnoreCase("NG") && !recipientPhone.startsWith("234") && recipientPhone.length() == 11) {
            recipientPhone = "234" + recipientPhone.substring(1);          
        }
        return recipientPhone;
    }
}