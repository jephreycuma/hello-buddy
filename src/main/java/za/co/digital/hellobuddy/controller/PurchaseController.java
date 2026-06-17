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

import za.co.digital.hellobuddy.dto.Product;
import za.co.digital.hellobuddy.dto.TopupResponse;

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
	                               @RequestParam(value = "recipientPhone", required = false,defaultValue = "0728703170") String recipientPhone,
	                               @RequestParam(value = "recipientEmail", required = false) String recipientEmail,
	                               @RequestParam(value = "country", required = false, defaultValue = "ZA") String countryIso,
	                               Model model) {
		

		if(phoneNumber == null || phoneNumber.trim().isEmpty()) {
			phoneNumber = "0";
		}
		if(recipientPhone == null || recipientPhone.trim().isEmpty()) {
			recipientPhone = "0";
		}
		String senderPhone = phoneNumber.replaceAll("\\D", ""); // Remove non-digit characters
		String receiverPhone = recipientPhone.replaceAll("\\D", ""); // Remove non-digit characters
		
		TopupResponse response = restClient.post()
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
				.body(new ParameterizedTypeReference<TopupResponse>() {});
		
		
		//System.out.println("TopupResponse received in PurchaseController: " + response.toString());
	    
	    model.addAttribute("productId", id);
	    model.addAttribute("productName", name);
	    model.addAttribute("productPrice", price);
	    model.addAttribute("referenceId", transactionId);
	    model.addAttribute("phoneNumber", recipientPhone);
	    System.out.println("Category received in PurchaseController: " + category);
	    System.out.println("Country ISO received in PurchaseController: " + countryIso);
	    // Clean evaluation using the concrete Category string instead of checking keywords!
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
}