package za.co.digital.hellobuddy.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import za.co.digital.hellobuddy.enums.TransactionStatus;

@Entity
@Table(name = "customer_transaction")
public class CustomerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "amount_in_zar", precision = 18, scale = 2)
    private BigDecimal amountInZAR;

    @Column(name = "local_amount", precision = 18, scale = 2)
    private BigDecimal localAmount;

    @Column(name = "wallet_id", nullable = false)
    private String walletId;

    @Column(name = "network")
    private String network;

    @Column(name = "product_description")
    private String productDescription;

    @Column(name = "country", length = 100)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false)
    private TransactionStatus transactionStatus;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "transaction_date_time", nullable = false)
    private LocalDateTime transactionDateTime;

    @Column(name = "sender_phone", length = 20)
    private String senderPhone;

    @Column(name = "receiver_phone", length = 20)
    private String receiverPhone;

    @Column(name = "email_address")
    private String emailAddress;
    
    @Column(name = "custom_identifier", length = 100)
    private String customIdentifier;

    public CustomerTransaction() {
    }

    public CustomerTransaction(Long id, BigDecimal amountInZAR, BigDecimal localAmount, 
                               String walletId, String network, String productDescription, 
                               String country, TransactionStatus transactionStatus, 
                               String errorMessage, LocalDateTime transactionDateTime,
                               String senderPhone, String receiverPhone, String emailAddress) {
        this.id = id;
        this.amountInZAR = amountInZAR;
        this.localAmount = localAmount;
        this.walletId = walletId;
        this.network = network;
        this.productDescription = productDescription;
        this.country = country;
        this.transactionStatus = transactionStatus;
        this.errorMessage = errorMessage;
        this.transactionDateTime = transactionDateTime;
        this.senderPhone = senderPhone;
        this.receiverPhone = receiverPhone;
        this.emailAddress = emailAddress;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getAmountInZAR() {
        return amountInZAR;
    }

    public void setAmountInZAR(BigDecimal amountInZAR) {
        this.amountInZAR = amountInZAR;
    }

    public BigDecimal getLocalAmount() {
        return localAmount;
    }

    public void setLocalAmount(BigDecimal localAmount) {
        this.localAmount = localAmount;
    }

    public String getWalletId() {
        return walletId;
    }

    public void setWalletId(String walletId) {
        this.walletId = walletId;
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public String getProductDescription() {
        return productDescription;
    }

    public void setProductDescription(String productDescription) {
        this.productDescription = productDescription;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public TransactionStatus getTransactionStatus() {
        return transactionStatus;
    }

    public void setTransactionStatus(TransactionStatus transactionStatus) {
        this.transactionStatus = transactionStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getTransactionDateTime() {
        return transactionDateTime;
    }

    public void setTransactionDateTime(LocalDateTime transactionDateTime) {
        this.transactionDateTime = transactionDateTime;
    }

    public String getSenderPhone() {
        return senderPhone;
    }

    public void setSenderPhone(String senderPhone) {
        this.senderPhone = senderPhone;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }
    
	public String getCustomIdentifier() {
		return customIdentifier;
	}

	public void setCustomIdentifier(String customIdentifier) {
		this.customIdentifier = customIdentifier;
	}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerTransaction that = (CustomerTransaction) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CustomerTransaction{" +
                "id=" + id +
                ", amountInZAR=" + amountInZAR +
                ", localAmount=" + localAmount +
                ", walletId='" + walletId + '\'' +
                ", network='" + network + '\'' +
                ", productDescription='" + productDescription + '\'' +
                ", country='" + country + '\'' +
                ", transactionStatus=" + transactionStatus +
                ", errorMessage='" + errorMessage + '\'' +
                ", transactionDateTime=" + transactionDateTime +
                ", senderPhone='" + senderPhone + '\'' +
                ", receiverPhone='" + receiverPhone + '\'' +
                ", emailAddress='" + emailAddress + '\'' +
                '}';
    }
}