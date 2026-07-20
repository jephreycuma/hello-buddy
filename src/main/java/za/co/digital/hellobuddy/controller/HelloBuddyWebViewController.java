package za.co.digital.hellobuddy.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;

import za.co.digital.hellobuddy.dto.ReloadlyTopupResult;
import za.co.digital.hellobuddy.dto.TopupResponse;

@Controller
public class HelloBuddyWebViewController {

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Value("${paystack.api.key}")
	private String paystackSecretKey;

	// Paystack base client configuration
	private final RestClient paystackClient = RestClient.builder()
			.baseUrl("https://api.paystack.co")
			.build();

	// Reloadly internal communication client
	private final RestClient restClient = RestClient.builder()
			.baseUrl("http://localhost:8081") 
			.build();

	@GetMapping("/success")
	public String paymentSuccess(@RequestParam("reference") String reference, Model model) {
		// Keep track of the transaction reference for possible reversal tracking
		String txReference = reference;
		
		try {
			// 1. Verify transaction status and fetch payload metadata via Paystack API
			Map<String, Object> paystackResponse = paystackClient.get()
					.uri("/transaction/verify/{reference}", txReference)
					.header("Authorization", "Bearer " + this.paystackSecretKey)
					.retrieve()
					.body(new ParameterizedTypeReference<Map<String, Object>>() {});

			if (paystackResponse == null || !Boolean.TRUE.equals(paystackResponse.get("status"))) {
				throw new IllegalStateException("Payment verification failed via Paystack gateway.");
			}

			@SuppressWarnings("unchecked")
			Map<String, Object> data = (Map<String, Object>) paystackResponse.get("data");
			String status = (String) data.get("status");

			if (!"success".equalsIgnoreCase(status)) {
				throw new IllegalStateException("Transaction reference status is marked as: " + status);
			}

			@SuppressWarnings("unchecked")
			Map<String, String> metadata = (Map<String, String>) data.get("metadata");
			if (metadata == null) {
				metadata = new HashMap<>();
			}
			
			// Unpack Identification Records from Paystack custom metadata array
			Integer id = Integer.parseInt(metadata.getOrDefault("productId", "0"));
			String name = metadata.getOrDefault("productName", "Hello Buddy Voucher");
			String senderPhone = metadata.getOrDefault("senderPhone", "");
			String recipientPhone = metadata.getOrDefault("recipientPhone", "");
			String recipientEmail = metadata.getOrDefault("recipientEmail", "");
			String countryIso = metadata.getOrDefault("countryIso", "ZA");

			// Fetch Prices
			Double originalPrice = Double.parseDouble(metadata.getOrDefault("originalPrice", "0.0"));
			Double checkoutPriceUsd = Double.parseDouble(metadata.getOrDefault("checkoutPriceUsd", "0.0"));

			if (senderPhone == null || senderPhone.trim().isEmpty()) {
				senderPhone = "0";
			}

			recipientPhone = validatePhoneNumber(recipientPhone, countryIso);
			String cleanSender = senderPhone.replaceAll("\\D", ""); 
			String cleanReceiver = recipientPhone.replaceAll("\\D", ""); 

			// 2. Bind basic details to UI template upfront
			String currencySymbol = redisTemplate.opsForValue().get(countryIso);
			model.addAttribute("productId", id);
			model.addAttribute("productName", name);
			model.addAttribute("productPrice", originalPrice);     
			model.addAttribute("currencySymbol", currencySymbol);
			model.addAttribute("chargedUsd", checkoutPriceUsd);
			model.addAttribute("phoneNumber", recipientPhone);
			model.addAttribute("sessionId", txReference); // Swapped for reference context

			// 3. Request Reloadly Delivery API
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
			}

			// 4. Evaluate response & Trigger Paystack Reversal if order fails
			if (results != null && results.isSuccessful()) {
				TopupResponse successData = results.getTopupResponse();
				model.addAttribute("referenceId", successData.getTransactionId());
			} else {
				System.err.println("Reloadly distribution failure. Initiating automated Paystack refund workflow...");
				triggerPaystackRefund(txReference, "Reloadly distribution failure.");
				model.addAttribute("errorMessage", "Delivery failed. We couldn't fulfill your voucher order, so your payment has been automatically reversed.");
			}

		} catch (Exception e) {
			System.err.println("Critical controller runtime exception breakdown: " + e.getMessage());
			if (txReference != null) {
				triggerPaystackRefund(txReference, "System runtime crash recovery refund.");
			}
			model.addAttribute("errorMessage", "Processing Error: " + e.getMessage() + ". Your payment has been queue-reversed.");
		}

		return "receipt";
	}

	/**
	 * Executes an asynchronous full refund back to the customer via Paystack API.
	 */
	private void triggerPaystackRefund(String reference, String reason) {
		if (reference == null || reference.isEmpty()) {
			System.err.println("Refund execution skipped: Missing reference tracking token link context.");
			return;
		}

		try {
			Map<String, String> requestBody = new HashMap<>();
			requestBody.put("transaction", reference); // Can accept reference token string
			requestBody.put("merchant_note", reason);

			Map<String, Object> refundResponse = paystackClient.post()
					.uri("/refund")
					.header("Authorization", "Bearer " + this.paystackSecretKey)
					.body(requestBody)
					.retrieve()
					.body(new ParameterizedTypeReference<Map<String, Object>>() {});

			if (refundResponse != null && Boolean.TRUE.equals(refundResponse.get("status"))) {
				System.out.println("Paystack Reversal Complete! Refund processed successfully: " + refundResponse.get("message"));
			} else {
				System.err.println("Paystack rejected refund execution request payload instructions.");
			}
			
		} catch (Exception e) {
			System.err.println("CRITICAL: Failed to reverse user funds via Paystack REST API: " + e.getMessage());
		}
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