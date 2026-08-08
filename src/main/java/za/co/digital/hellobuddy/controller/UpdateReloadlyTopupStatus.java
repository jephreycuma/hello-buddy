package za.co.digital.hellobuddy.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import za.co.digital.hellobuddy.enums.TransactionStatus;
import za.co.digital.hellobuddy.model.CustomerTransaction;
import za.co.digital.hellobuddy.records.Transaction;
import za.co.digital.hellobuddy.records.TransactionPageResponse;
import za.co.digital.hellobuddy.repository.CustomerTransactionRepository;
import za.co.digital.hellobuddy.service.CustomerWalletService;

import java.util.List;

@Component
public class UpdateReloadlyTopupStatus {

    private static final Logger logger = LoggerFactory.getLogger(UpdateReloadlyTopupStatus.class);

    private final RestClient restClient;
    private final CustomerTransactionRepository transactionRepository;
    private final CustomerWalletService walletService;

    public UpdateReloadlyTopupStatus(
            CustomerTransactionRepository transactionRepository,
            CustomerWalletService walletService) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(60000);
        requestFactory.setReadTimeout(60000);

        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:8081")
                .requestFactory(requestFactory)
                .build();

        this.transactionRepository = transactionRepository;
        this.walletService = walletService;
    }

    /**
     * Reads the cron expression directly from application.properties / application.yml.
     * Default fallback is every 1 minute ("0 * * * * *") if key is not found.
     */
    @Scheduled(cron = "${status.cron.schedular:0 * * * * *}")
    public void processPendingReloadlyTransactions() {
        logger.info("Executing scheduled job: Syncing PENDING transactions...");

        List<CustomerTransaction> pendingTransactions = transactionRepository.findByTransactionStatus(TransactionStatus.PENDING);

        if (pendingTransactions.isEmpty()) {
            logger.info("No PENDING transactions found.");
            return;
        }

        for (CustomerTransaction transaction : pendingTransactions) {
            try {
                syncTransactionStatus(transaction);
            } catch (Exception e) {
                logger.error("Failed to sync status for Custom ID [{}]: {}", 
                        transaction.getCustomIdentifier(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void syncTransactionStatus(CustomerTransaction tx) {
        String customIdentifier = tx.getCustomIdentifier();

        TransactionPageResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/telecom/status")
                        .queryParam("customIdentifier", customIdentifier)
                        .build())
                .retrieve()
                .body(TransactionPageResponse.class);

        if (response == null || response.content() == null || response.content().isEmpty()) {
            logger.warn("No response content returned for identifier: {}", customIdentifier);
            return;
        }

        Transaction remoteTx = response.content().get(0);
        TransactionStatus remoteStatus = remoteTx.status();

        if (remoteStatus == null) {
            logger.warn("Received null status for transaction identifier: {}", customIdentifier);
            return;
        }

        if (remoteStatus == TransactionStatus.SUCCESSFUL) {
            tx.setTransactionStatus(TransactionStatus.SUCCESSFUL);
            transactionRepository.save(tx);
            logger.info("Transaction ID [{}] status updated to SUCCESSFUL", tx.getCustomIdentifier());

        } else if (remoteStatus == TransactionStatus.FAILED || remoteStatus == TransactionStatus.REVERSED) {
            tx.setTransactionStatus(TransactionStatus.FAILED);
            transactionRepository.save(tx);

            // Reverses the wallet deduction using the walletId and amount from the transaction record
            walletService.reverseWalletDeduction(tx.getWalletId(), tx.getAmountInZAR());

            logger.info("Customer ID [{}] marked FAILED. Reverted amount [{}]", 
                    tx.getCustomIdentifier(), tx.getAmountInZAR());
        }
    }
}