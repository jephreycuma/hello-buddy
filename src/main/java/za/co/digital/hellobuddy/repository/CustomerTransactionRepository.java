package za.co.digital.hellobuddy.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import za.co.digital.hellobuddy.enums.TransactionStatus;
import za.co.digital.hellobuddy.model.CustomerTransaction;

public interface CustomerTransactionRepository extends JpaRepository<CustomerTransaction, Long> {
	
	List<CustomerTransaction> findByTransactionStatus(TransactionStatus status);
}
