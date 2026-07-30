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

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
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
import za.co.digital.hellobuddy.service.WalletPasswordResetService;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/wallet")
public class WalletAuthController {

    private final CustomerWalletRepository walletRepository;
    private final WalletPasswordResetService resetService;
    private final PasswordEncoder passwordEncoder;

    // Spring automatically injects all parameters in single-constructor components
    public WalletAuthController(CustomerWalletRepository walletRepository, 
                                WalletPasswordResetService resetService, 
                                PasswordEncoder passwordEncoder) {
        this.walletRepository = walletRepository;
        this.resetService = resetService;
        this.passwordEncoder = passwordEncoder;
    }

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
        String uniqueRef = generateWalletReference();
        		//"HB-" + String.format("%06d", new SecureRandom().nextInt(1000000));
        customer.setReferenceNumber(uniqueRef);
        customer.setWalletBalance(BigDecimal.ZERO);

        // 2. Hash password using injected PasswordEncoder
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
        if ("true".equals(registered)) {
            String username = (String) session.getAttribute("SETUP_2FA_USER");
            if (username != null) {
                walletRepository.findByUsername(username).ifPresent(user -> {
                    model.addAttribute("successMessage", 
                            "Wallet successfully created! Your Unique Identifier is: " + user.getReferenceNumber());
                    model.addAttribute("customerRef", user.getReferenceNumber());
                });
                session.removeAttribute("SETUP_2FA_USER");
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

            session.setAttribute("WALLET_BALANCE", currentBalance);
            String formattedBalance = String.format("R %.2f", currentBalance);

            return ResponseEntity.ok(Map.of(
                "balance", currentBalance,
                "formattedBalance", formattedBalance
            ));
        }

        return ResponseEntity.status(440).body(Map.of("error", "User not found"));
    }
    
    @GetMapping("/change-password")
    public String showChangePasswordForm(HttpSession session) {
        String username = (String) session.getAttribute("LOGGED_IN_CUSTOMER");
        if (username == null) {
            return "redirect:/wallet/login";
        }
        return "wallet-change-password";
    }

    @PostMapping("/change-password")
    public String processChangePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            HttpSession session,
            RedirectAttributes redirectAttributes,
            Model model) {

        String username = (String) session.getAttribute("LOGGED_IN_CUSTOMER");
        if (username == null) {
            return "redirect:/wallet/login";
        }

        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "New passwords do not match.");
            return "wallet-change-password";
        }

        var optionalUser = walletRepository.findByUsername(username);
        if (optionalUser.isPresent()) {
            CustomerWallet user = optionalUser.get();

            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                model.addAttribute("error", "Current password is incorrect.");
                return "wallet-change-password";
            }

            user.setPassword(passwordEncoder.encode(newPassword));
            walletRepository.save(user);

            redirectAttributes.addFlashAttribute("successMessage", "Password updated successfully!");
            return "redirect:/";
        }

        return "redirect:/wallet/login";
    }
    
    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "wallet-forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam String username,
                                        @RequestParam String email,
                                        @RequestParam String walletId,
                                        Model model) {
        Optional<String> tempPasswordOpt = resetService.processPasswordReset(username, email, walletId);

        if (tempPasswordOpt.isPresent()) {
            model.addAttribute("tempPassword", tempPasswordOpt.get());
        } else {
            model.addAttribute("error", "The information provided does not match our records.");
        }

        return "wallet-forgot-password"; // returns the updated view
    }
    
    @Transactional
    private String generateWalletReference() {
        Long nextSeq = walletRepository.getNextWalletSequence();
        return "HB-" + String.format("%06d", nextSeq); 
    }
}