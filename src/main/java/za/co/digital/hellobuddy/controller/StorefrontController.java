package za.co.digital.hellobuddy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import za.co.digital.hellobuddy.cache.HelloBuddyInnerMemory;
import za.co.digital.hellobuddy.dto.ProductItemDTO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
public class StorefrontController {

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

        // 0. Force session initialization before Thymeleaf starts rendering
        HttpSession session = request.getSession(true);

        // 1. Check if user is logged in
        boolean isLoggedIn = session.getAttribute("LOGGED_IN_CUSTOMER") != null;

        // 2. Delegate product retrieval to HelloBuddyInnerMemory based on session state
        Map<String, List<ProductItemDTO>> catalogMap = isLoggedIn
                ? innerMemory.getReloadlyProductsForRegisteredUsers(countryIso)            // Full catalog for logged-in users
                : innerMemory.getReloadlyProductsForNonRegisteredUsers(countryIso);  // Profitable-only catalog for non-logged-in users

        model.addAttribute("catalogProducts", catalogMap);

        // 3. Handle Logout Success message feedback
        if ("true".equals(logoutSuccess)) {
            model.addAttribute("successMessage", "You have been successfully logged out.");
        }

        return "index";
    }
}