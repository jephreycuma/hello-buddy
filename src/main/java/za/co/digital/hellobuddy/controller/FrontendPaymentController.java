package za.co.digital.hellobuddy.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
public class FrontendPaymentController {

    private final RestClient restClient;

    public FrontendPaymentController(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://localhost:8081").build();
    }

    @PostMapping("/checkout")
    public ResponseEntity<Map<String, Object>> forwardToPaymentGateway(@RequestBody Map<String, Object> orderDetails) {
        // Forward the payment payload safely over to our core backend microservice
        Map<String, Object> response = restClient.post()
                .uri("/api/gateway/process")
                .body(orderDetails)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

        return ResponseEntity.ok(response);
    }
}