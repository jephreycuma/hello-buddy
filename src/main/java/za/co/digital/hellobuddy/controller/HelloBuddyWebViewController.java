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
import tools.jackson.databind.ObjectMapper;
import za.co.digital.hellobuddy.dto.ReloadlyTopupResult;
import za.co.digital.hellobuddy.dto.TopupResponse;
import za.co.digital.hellobuddy.errors.ReloadlyErrorResponse;

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

            // 2. FETCH THE ORIGINAL LOCAL FACE VALUE (e.g., 200.00 ZAR instead of 14.99 USD)
            Double originalPrice = Double.parseDouble(metadata.getOrDefault("originalPrice", "0.0"));
            Double checkoutPriceUsd = Double.parseDouble(metadata.getOrDefault("checkoutPriceUsd", "0.0"));

            if (senderPhone == null || senderPhone.trim().isEmpty()) {
                senderPhone = "0";
            }

            recipientPhone = validatePhoneNumber(recipientPhone, countryIso);
            String cleanSender = senderPhone.replaceAll("\\D", ""); 
            String cleanReceiver = recipientPhone.replaceAll("\\D", ""); 

            // 3. Request Reloadly to deliver the exact local face-value amount (e.g., R200.00)
            ReloadlyTopupResult results = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/telecom/topups")
                            .queryParam("amount", originalPrice) // <-- TARGET LOCAL PRICE SENT HERE!
                            .queryParam("senderPhoone", Long.parseLong(cleanSender))
                            .queryParam("receiverPhone", Long.parseLong(cleanReceiver))
                            .queryParam("countryISO", countryIso) // e.g., "ZA"
                            .queryParam("operatorId", id)
                            .queryParam("senderEmail", recipientEmail)
                            .queryParam("useLocalAmount", true) // Tells Reloadly: "The amount field above is explicitly local currency"
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<ReloadlyTopupResult>() {});

            // 4. Bind information to receipt view template
            model.addAttribute("productId", id);
            model.addAttribute("productName", name);
            // Show the user the local value delivered, but you can also add "checkoutPriceUsd" to show what they paid in USD!
            model.addAttribute("productPrice", originalPrice);     
            model.addAttribute("chargedUsd", checkoutPriceUsd);
            model.addAttribute("phoneNumber", recipientPhone);
            model.addAttribute("sessionId", sessionId);

            if (results != null && results.isSuccessful()) {
                TopupResponse successData = results.getTopupResponse();
                model.addAttribute("referenceId", successData.getTransactionId());
            } else {
                // Error mapping logic remains unchanged...
            }

        } catch (Exception e) {
            System.err.println("Execution failure handling backend fulfillment: " + e.getMessage());
            model.addAttribute("errorMessage", "Processing Error: " + e.getMessage());
        }

        return "receipt";
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