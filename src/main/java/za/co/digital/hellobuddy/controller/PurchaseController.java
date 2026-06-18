package za.co.digital.hellobuddy.controller;

import java.net.http.HttpResponse;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;
import za.co.digital.hellobuddy.dto.Product;
import za.co.digital.hellobuddy.dto.ReloadlyTopupResult;
import za.co.digital.hellobuddy.dto.TopupResponse;
import za.co.digital.hellobuddy.errors.ReloadlyErrorResponse;

@Controller
@RequestMapping("/purchase")
public class PurchaseController {

    private final RestClient restClient = RestClient.builder()
            .baseUrl("http://localhost:8081") // Update this port/host if your routing-service runs elsewhere
            .build();
    
	@PostMapping("/confirm")
	public String finalizePurchase(@RequestParam("id") Integer id,
	                               @RequestParam("name") String name,
	                               @RequestParam("price") Double price,
	                               @RequestParam("category") String category, // <-- Added parameter mapping
	                               @RequestParam("transactionId") String transactionId,
	                               @RequestParam(value = "phoneNumber", required = false, defaultValue = "") String phoneNumber,
	                               @RequestParam(value = "recipientPhone", required = false,defaultValue = "") String recipientPhone,
	                               @RequestParam(value = "recipientEmail", required = false) String recipientEmail,
	                               @RequestParam(value = "country", required = false, defaultValue = "ZA") String countryIso,
	                               Model model) {
		

		if(phoneNumber == null || phoneNumber.trim().isEmpty()) {
			phoneNumber = "0";
		}
		
		recipientPhone = validatePhoneNumber(recipientPhone, countryIso);

		String senderPhone = phoneNumber.replaceAll("\\D", ""); // Remove non-digit characters
		String receiverPhone = recipientPhone.replaceAll("\\D", ""); // Remove non-digit characters
		
		ReloadlyTopupResult results = restClient.post()
				.uri(uriBuilder -> uriBuilder
						.path("/api/v1/telecom/topups")
						.queryParam("amount", price)
						.queryParam("senderPhoone", Long.parseLong(senderPhone))
						.queryParam("receiverPhone", Long.parseLong(receiverPhone))
						.queryParam("countryISO", countryIso)
						.queryParam("operatorId", id)
						.queryParam("senderEmail", recipientEmail)
						.queryParam("useLocalAmount", true)
						.build())
				.retrieve()
				.body(new ParameterizedTypeReference<ReloadlyTopupResult>() {});
		
		
	    model.addAttribute("productId", id);
	    model.addAttribute("productName", name);
	    model.addAttribute("productPrice", price);	    
	    model.addAttribute("phoneNumber", recipientPhone);
	    
	    if (results.isSuccessful()) {
	    TopupResponse successData = results.getTopupResponse();
	    model.addAttribute("referenceId", successData.getTransactionId());
		} else {
		    ObjectMapper mapper = new ObjectMapper();
		    
		    try {
		        ReloadlyErrorResponse errorResponse = mapper.readValue(results.getRawBody(), ReloadlyErrorResponse.class);
		        
		        model.addAttribute("errorMessage", errorResponse.getMessage());
		        
		    } catch (Exception e) {
		        model.addAttribute("errorMessage", "An error occurred: " + results.getRawBody());
		    }
		}

	    if ("Airtime".equalsIgnoreCase(category) || "GiftCards".equalsIgnoreCase(category)) {
	        String mockPin = String.format("%04d-%04d-%04d-%04d", 
	            (int)(Math.random() * 10000), (int)(Math.random() * 10000), 
	            (int)(Math.random() * 10000), (int)(Math.random() * 10000));
	        model.addAttribute("voucherPin", mockPin);
	    } else {
	        model.addAttribute("voucherPin", null);
	    }
	    
	    return "receipt"; 
	}
	
	private String validatePhoneNumber(String recipientPhone, String countryIso) {
		recipientPhone = recipientPhone.replaceAll("\\D", ""); // Remove non-digit characters
		if(countryIso.equalsIgnoreCase("ZA") && !recipientPhone.startsWith("+27") && !recipientPhone.startsWith("27") && recipientPhone.length() == 10) {
			recipientPhone = "27" + recipientPhone.substring(1);
		} else if(countryIso.equalsIgnoreCase("NG") && !recipientPhone.startsWith("+234") && !recipientPhone.startsWith("234") && recipientPhone.length() == 11) {
			recipientPhone =  "234" + recipientPhone.substring(1);			
		}
	    return recipientPhone;
	}
}