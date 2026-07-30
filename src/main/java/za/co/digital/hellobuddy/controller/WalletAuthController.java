package za.co.digital.hellobuddy.controller;

import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.util.Utils;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.time.SystemTimeProvider;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.co.digital.hellobuddy.model.CustomerWallet;
import za.co.digital.hellobuddy.repository.CustomerWalletRepository;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Map;

@Controller
@RequestMapping("/wallet")
public class WalletAuthController {

    @Autowired
    private CustomerWalletRepository walletRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @GetMapping("/register")
    public String showRegistrationForm() {
        return "wallet-register";
    }

    @PostMapping("/register")
    public String registerCustomer(@ModelAttribute CustomerWallet customer, HttpSession session, RedirectAttributes redirectAttributes) {
        if (walletRepository.findByUsername(customer.getUsername()).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Username/Email already registered.");
            return "redirect:/wallet/register";
        }
        if (walletRepository.findByEmail(customer.getEmail()).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Email address is already registered.");
            return "redirect:/wallet/register";
        }

        // 1. Generate unique reference ID
        String uniqueRef = "HB-" + String.format("%06d", new SecureRandom().nextInt(1000000));
        customer.setReferenceNumber(uniqueRef);
        customer.setWalletBalance(BigDecimal.ZERO);

        // 2. Hash password
        customer.setPassword(passwordEncoder.encode(customer.getPassword()));

        // 3. Generate 2FA Secret Key
        String secretKey = new DefaultSecretGenerator().generate();
        customer.setSecretKey(secretKey);

        // Save entity to DB
        walletRepository.save(customer);

        // Store pending user in session for 2FA onboarding
        session.setAttribute("SETUP_2FA_USER", customer.getUsername());

        return "redirect:/wallet/setup-2fa";
    }

    @GetMapping("/setup-2fa")
    public String show2FASetup(HttpSession session, Model model) {
        String username = (String) session.getAttribute("SETUP_2FA_USER");
        if (username == null) {
            return "redirect:/wallet/register";
        }

        var optionalUser = walletRepository.findByUsername(username);
        if (optionalUser.isEmpty()) {
            return "redirect:/wallet/register";
        }

        CustomerWallet user = optionalUser.get();

        // Build QR code data
        QrData data = new QrData.Builder()
                .label(user.getUsername())
                .secret(user.getSecretKey())
                .issuer("Hello Buddy")
                .build();

        // Render QR Code as Base64 image
        QrGenerator generator = new ZxingPngQrGenerator();
        try {
            byte[] imageData = generator.generate(data);
            String mimeType = generator.getImageMimeType();
            String dataUri = Utils.getDataUriForImage(imageData, mimeType);

            model.addAttribute("qrCodeImage", dataUri);
            model.addAttribute("secretKey", user.getSecretKey());
            
            // Returns your exact template name: wallet-2fa-setup.html
            return "wallet-2fa-setup"; 
        } catch (Exception e) {
            model.addAttribute("error", "Failed to generate QR Code. Please try again.");
            return "wallet-register";
        }
    }

    @GetMapping("/login")
    public String showLoginForm(@RequestParam(value = "registered", required = false) String registered, 
                                HttpSession session, 
                                Model model) {
        // If arriving from the "I've Scanned It" button, finalize registration messaging
        if ("true".equals(registered)) {
            String username = (String) session.getAttribute("SETUP_2FA_USER");
            if (username != null) {
                walletRepository.findByUsername(username).ifPresent(user -> {
                    model.addAttribute("successMessage", 
                            "Wallet successfully created! Your Unique Identifier is: " + user.getReferenceNumber());
                    model.addAttribute("customerRef", user.getReferenceNumber());
                });
                session.removeAttribute("SETUP_2FA_USER"); // Clear setup session token
            }
        }
        return "wallet-login";
    }

    @PostMapping("/login")
    public String processLoginStep1(@RequestParam String username,
                                    @RequestParam String password,
                                    HttpSession session,
                                    Model model) {
        var optionalUser = walletRepository.findByUsername(username);

        if (optionalUser.isPresent()) {
            CustomerWallet user = optionalUser.get();
            if (passwordEncoder.matches(password, user.getPassword())) {
                session.setAttribute("PENDING_2FA_USER", username);
                return "redirect:/wallet/verify-2fa";
            }
        }

        model.addAttribute("error", "Invalid username or password.");
        return "wallet-login";
    }

    @GetMapping("/verify-2fa")
    public String show2FAForm(HttpSession session) {
        if (session.getAttribute("PENDING_2FA_USER") == null) {
            return "redirect:/wallet/login";
        }
        return "wallet-2fa";
    }

    @PostMapping("/verify-2fa")
    public String verify2FA(@RequestParam String otpCode, HttpSession session, Model model) {
        String username = (String) session.getAttribute("PENDING_2FA_USER");
        if (username == null) {
            return "redirect:/wallet/login";
        }

        var optionalUser = walletRepository.findByUsername(username);
        if (optionalUser.isPresent()) {
            CustomerWallet user = optionalUser.get();

            CodeGenerator codeGenerator = new DefaultCodeGenerator();
            CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, new SystemTimeProvider());

            if (verifier.isValidCode(user.getSecretKey(), otpCode)) {
                session.removeAttribute("PENDING_2FA_USER");

                session.setAttribute("LOGGED_IN_CUSTOMER", user.getUsername());
                session.setAttribute("CUSTOMER_REF", user.getReferenceNumber());
                session.setAttribute("WALLET_BALANCE", user.getWalletBalance());

                return "redirect:/?walletLogin=success";
            }
        }

        model.addAttribute("error", "Invalid 6-digit Authenticator code.");
        return "wallet-2fa";
    }
    
    @GetMapping("/balance")
    @ResponseBody
    public ResponseEntity<?> getUpdatedBalance(HttpSession session) {
        String username = (String) session.getAttribute("LOGGED_IN_CUSTOMER");
        
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        var optionalUser = walletRepository.findByUsername(username);
        if (optionalUser.isPresent()) {
            BigDecimal currentBalance = optionalUser.get().getWalletBalance();
            if (currentBalance == null) {
                currentBalance = BigDecimal.ZERO;
            }

            // Keep session updated with fresh database value
            session.setAttribute("WALLET_BALANCE", currentBalance);

            // Format for UI (R 0.00)
            String formattedBalance = String.format("R %.2f", currentBalance);

            return ResponseEntity.ok(Map.of(
                "balance", currentBalance,
                "formattedBalance", formattedBalance
            ));
        }

        return ResponseEntity.status(440).body(Map.of("error", "User not found"));
    }
}