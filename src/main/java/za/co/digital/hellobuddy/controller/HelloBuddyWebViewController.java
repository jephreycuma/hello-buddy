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
            // 1. Initialize Stripe key and retrieve the session metadata payload
            Stripe.apiKey = this.stripeApiKey;
            Session session = Session.retrieve(sessionId);
            Map<String, String> metadata = session.getMetadata();

            // 2. Parse out parameters safely
            Integer id = Integer.parseInt(metadata.getOrDefault("productId", "0"));
            String name = metadata.getOrDefault("productName", "Hello Buddy Voucher");
            Double price = Double.parseDouble(metadata.getOrDefault("price", "0.0"));
            String category = metadata.getOrDefault("category", "Data");
            String senderPhone = metadata.getOrDefault("senderPhone", "");
            String recipientPhone = metadata.getOrDefault("recipientPhone", "");
            String recipientEmail = metadata.getOrDefault("recipientEmail", "");
            String countryIso = metadata.getOrDefault("countryIso", "ZA");

            if (senderPhone == null || senderPhone.trim().isEmpty()) {
                senderPhone = "0";
            }

            recipientPhone = validatePhoneNumber(recipientPhone, countryIso);
            String cleanSender = senderPhone.replaceAll("\\D", ""); 
            String cleanReceiver = recipientPhone.replaceAll("\\D", ""); 

            // 3. Post parameters directly downstream to Routing Service on Port 8081
            ReloadlyTopupResult results = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/telecom/topups")
                            .queryParam("amount", price)
                            .queryParam("senderPhoone", Long.parseLong(cleanSender))
                            .queryParam("receiverPhone", Long.parseLong(cleanReceiver))
                            .queryParam("countryISO", countryIso)
                            .queryParam("operatorId", id)
                            .queryParam("senderEmail", recipientEmail)
                            .queryParam("useLocalAmount", true)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<ReloadlyTopupResult>() {});

            // 4. Bind information to your template layout views
            model.addAttribute("productId", id);
            model.addAttribute("productName", name);
            model.addAttribute("productPrice", price);     
            model.addAttribute("phoneNumber", recipientPhone);
            model.addAttribute("sessionId", sessionId);

            if (results != null && results.isSuccessful()) {
                TopupResponse successData = results.getTopupResponse();
                model.addAttribute("referenceId", successData.getTransactionId());
            } else {
                ObjectMapper mapper = new ObjectMapper();
                try {
                    ReloadlyErrorResponse errorResponse = mapper.readValue(results.getRawBody(), ReloadlyErrorResponse.class);
                    model.addAttribute("errorMessage", errorResponse.getMessage());
                } catch (Exception e) {
                    model.addAttribute("errorMessage", "Delivery Fault: " + (results != null ? results.getRawBody() : "No response"));
                }
            }

            // Generate mock pins for manual validation if category matches
            if ("Airtime".equalsIgnoreCase(category) || "GiftCards".equalsIgnoreCase(category) || "TopUps".equalsIgnoreCase(category)) {
                String mockPin = String.format("%04d-%04d-%04d-%04d", 
                    (int)(Math.random() * 10000), (int)(Math.random() * 10000), 
                    (int)(Math.random() * 10000), (int)(Math.random() * 10000));
                model.addAttribute("voucherPin", mockPin);
            } else {
                model.addAttribute("voucherPin", null);
            }

        } catch (Exception e) {
            System.err.println("Failed to unpack Stripe context or execute backend fulfillment: " + e.getMessage());
            model.addAttribute("errorMessage", "Internal System Processing Fault: " + e.getMessage());
        }

        return "receipt"; // Renders receipt.html template view cleanly
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