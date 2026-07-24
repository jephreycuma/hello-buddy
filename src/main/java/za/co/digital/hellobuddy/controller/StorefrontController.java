package za.co.digital.hellobuddy.controller;

import za.co.digital.hellobuddy.cache.HelloBuddyInnerMemory;
import za.co.digital.hellobuddy.dto.ProductItemDTO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;

@Controller
public class StorefrontController {

    @Value("${platform.markup:0.0}")
    private double platformMarkup;

    private final HelloBuddyInnerMemory innerMemory;

    // Spring constructor injection
    public StorefrontController(HelloBuddyInnerMemory innerMemory) {
        this.innerMemory = innerMemory;
    }

    @GetMapping("/")
    public String showStorefront(
            @RequestParam(value = "country", required = false, defaultValue = "ZA") String countryIso, 
            Model model) {
            
        // Clean, direct call to the Spring-managed component
        Map<String, List<ProductItemDTO>> catalogMap = innerMemory.getReloadlyProducts(countryIso);
        
        model.addAttribute("catalogProducts", catalogMap);

        return "index";
    }
}