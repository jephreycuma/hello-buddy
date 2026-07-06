package za.co.digital.hellobuddy.controller;

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
        try {
            Stripe.apiKey = this.stripeApiKey;
            Session session = Session.retrieve(sessionId);
            Map<String, String> metadata = session.getMetadata();

            // 1. Unpack identification records
            Integer id = Integer.parseInt(metadata.getOrDefault("productId", "0"));
            String name = metadata.getOrDefault("productName", "Hello Buddy Voucher");
            String category = metadata.getOrDefault("category", "Data");
            String senderPhone = metadata.getOrDefault("senderPhone", "");
            String recipientPhone = metadata.getOrDefault("recipientPhone", "");
            String recipientEmail = metadata.getOrDefault("recipientEmail", "");
            String countryIso = metadata.getOrDefault("countryIso", "ZA");

            // 2. Fetch prices
            Double originalPrice = Double.parseDouble(metadata.getOrDefault("originalPrice", "0.0"));
            Double checkoutPriceUsd = Double.parseDouble(metadata.getOrDefault("checkoutPriceUsd", "0.0"));

            if (senderPhone == null || senderPhone.trim().isEmpty()) {
                senderPhone = "0";
            }

            recipientPhone = validatePhoneNumber(recipientPhone, countryIso);
            String cleanSender = senderPhone.replaceAll("\\D", ""); 
            String cleanReceiver = recipientPhone.replaceAll("\\D", ""); 

            // 3. Request Reloadly to deliver
            ReloadlyTopupResult results = restClient.post()
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

            // 4. Resolve Currency Symbol cleanly
            // Fallback checking sequence: session currency string -> country ISO string
            String currencySymbol = getCurrencySymbol(session.getCurrency(), countryIso);

            // 5. Bind information to receipt view template
            model.addAttribute("productId", id);
            model.addAttribute("productName", name);
            model.addAttribute("productPrice", originalPrice);     
            model.addAttribute("currencySymbol", currencySymbol); // <-- NEW ATTR PASSED TO VIEW
            model.addAttribute("chargedUsd", checkoutPriceUsd);
            model.addAttribute("phoneNumber", recipientPhone);
            model.addAttribute("sessionId", sessionId);

            if (results != null && results.isSuccessful()) {
                TopupResponse successData = results.getTopupResponse();
                model.addAttribute("referenceId", successData.getTransactionId());
            } else {
                // Error mapping logic...
            }

        } catch (Exception e) {
            System.err.println("Execution failure handling backend fulfillment: " + e.getMessage());
            model.addAttribute("errorMessage", "Processing Error: " + e.getMessage());
        }

        return "receipt";
    }

    /**
     * Determines the appropriate local currency symbol using Stripe currency or Country ISO fallbacks.
     */
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
        
        // Fallback safety layer checking via Metadata Country code if currency isn't captured
        if (countryIso != null) {
            switch (countryIso.toUpperCase()) {
                case "ZA": return "R";
                case "NG": return "₦";
                case "KE": return "KSh";
                case "GH": return "GH₵";
            }
        }
        return "R"; // Final global default
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