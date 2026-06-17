package za.co.digital.hellobuddy.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class MockPaymentApiController {

    @PostMapping("/charge")
    public ResponseEntity<Map<String, Object>> processMockPayment(@RequestBody Map<String, String> payload) {
        Map<String, Object> response = new HashMap<>();
        
        //TODO: Implement more robust validation and error handling as needed for production scenarios
        // Basic validation check simulation
        System.out.println("Received payment request: " + payload);
        String cardNumber = payload.get("cardNumber");
        if (cardNumber == null || cardNumber.replaceAll("\\s", "").length() < 16) {
            response.put("success", false);
            response.put("message", "Invalid card layout specifications.");
            return ResponseEntity.badRequest().body(response);
        }

        // Simulate successful gateway authorization
        response.put("success", true);
        response.put("transactionId", "TXN-" + System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
}