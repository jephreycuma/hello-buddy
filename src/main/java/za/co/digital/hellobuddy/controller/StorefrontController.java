package za.co.digital.hellobuddy.controller;

import jakarta.servlet.http.HttpServletRequest;
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
            @RequestParam(value = "logoutSuccess", required = false) String logoutSuccess,
            HttpServletRequest request,
            Model model) {

        // 0. Force session initialization before Thymeleaf starts streaming the response
        request.getSession(true);

        // 1. Handle Reloadly catalog loading
        Map<String, List<ProductItemDTO>> catalogMap = innerMemory.getReloadlyProducts(countryIso);
        model.addAttribute("catalogProducts", catalogMap);

        // 2. Handle Logout Success message feedback
        if ("true".equals(logoutSuccess)) {
            model.addAttribute("successMessage", "You have been successfully logged out.");
        }

        return "index";
    }
}