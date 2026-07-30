package za.co.digital.hellobuddy.service;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import za.co.digital.hellobuddy.model.CustomerWallet;
import za.co.digital.hellobuddy.repository.CustomerWalletRepository;

@Service
public class WalletPasswordResetService {

    @Autowired
    private CustomerWalletRepository walletRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    
    private final static Logger logger = Logger.getLogger(WalletPasswordResetService.class.getName());

    @Transactional
    public Optional<String> processPasswordReset(String username, String email, String walletId) {
        // Query the database to verify all three matching parameters
        Optional<CustomerWallet> walletOpt = walletRepository
            .findByUsernameAndEmailAndReferenceNumber(username, email, walletId);

        if (walletOpt.isEmpty()) {
            return Optional.empty(); // Details do not match record
        }

        CustomerWallet walletUser = walletOpt.get();

        // 1. Generate strong temporary password
        String tempPassword = generateStrongPassword(12);

        // 2. Hash and update password in DB, flag user for mandatory password change
        walletUser.setPassword(passwordEncoder.encode(tempPassword));
        walletUser.setRequirePasswordChange(true);
        walletRepository.save(walletUser);

        logger.info("Successfully generated temporary password for user: " + username);

        // 3. Return plain text temporary password to be rendered on UI
        return Optional.of(tempPassword);
    }

    private String generateStrongPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}