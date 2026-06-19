package za.co.digital.hellobuddy.controller;

import za.co.digital.hellobuddy.cache.HelloBuddyInnerMemory;
import za.co.digital.hellobuddy.dto.Product;
import za.co.digital.hellobuddy.dto.ProductItemDTO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class StorefrontController {

    // Initialize RestClient targeting your routing-service system deployment base
    private final RestClient restClient = RestClient.builder()
            .baseUrl("http://localhost:8081") // Update this port/host if your routing-service runs elsewhere
            .build();

    @GetMapping("/") // Keeping mapped to a distinct path to avoid collisions with ShopController
    public String showStorefront(@RequestParam(value = "country", required = false, defaultValue = "ZA") String countryIso, 
    	    Model model) {
        Map<String, List<ProductItemDTO>> catalogMap = HelloBuddyInnerMemory.getInstance(restClient, countryIso).getReloadlyProducts(countryIso);
        
        model.addAttribute("javaCatalogData", catalogMap);

        return "index";
    }
    
}