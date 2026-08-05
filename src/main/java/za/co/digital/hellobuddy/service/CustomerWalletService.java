package za.co.digital.hellobuddy.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.digital.hellobuddy.model.CustomerWallet;
import za.co.digital.hellobuddy.repository.CustomerWalletRepository;

import java.math.BigDecimal;

@Service
public class CustomerWalletService {

    @Autowired
    private CustomerWalletRepository walletRepository;

    /**
     * Deducts amount from customer wallet in an isolated, committed transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BigDecimal deductWallet(String username, BigDecimal amount) {
        CustomerWallet wallet = walletRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Wallet not found for user: " + username));

        BigDecimal currentBalance = wallet.getWalletBalance() != null ? wallet.getWalletBalance() : BigDecimal.ZERO;
        if (currentBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient wallet balance.");
        }

        BigDecimal newBalance = currentBalance.subtract(amount);
        wallet.setWalletBalance(newBalance);
        walletRepository.saveAndFlush(wallet);

        return newBalance;
    }

    /**
     * Reverses a failed deduction and commits the restored balance to DB in an isolated transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BigDecimal reverseWalletDeduction(String username, BigDecimal amount) {
        CustomerWallet wallet = walletRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Wallet not found during refund for user: " + username));

        BigDecimal currentBalance = wallet.getWalletBalance() != null ? wallet.getWalletBalance() : BigDecimal.ZERO;
        BigDecimal newBalance = currentBalance.add(amount);

        wallet.setWalletBalance(newBalance);
        walletRepository.saveAndFlush(wallet);

        return newBalance;
    }
}
