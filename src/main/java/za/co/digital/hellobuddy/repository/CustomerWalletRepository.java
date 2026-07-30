package za.co.digital.hellobuddy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.digital.hellobuddy.model.CustomerWallet;
import java.util.Optional;

public interface CustomerWalletRepository extends JpaRepository<CustomerWallet, Long> {
    Optional<CustomerWallet> findByUsername(String username);
    Optional<CustomerWallet> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
