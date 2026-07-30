package za.co.digital.hellobuddy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import za.co.digital.hellobuddy.model.CustomerWallet;
import java.util.Optional;

public interface CustomerWalletRepository extends JpaRepository<CustomerWallet, Long> {
    Optional<CustomerWallet> findByUsername(String username);
    Optional<CustomerWallet> findByEmail(String email);
 // In CustomerWalletRepository.java
    Optional<CustomerWallet> findByUsernameAndEmailAndReferenceNumber(String username, String email, String referenceNumber);
    
    @Query(value = "SELECT NEXT VALUE FOR customer_wallet_seq", nativeQuery = true)
    Long getNextWalletSequence();
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
