package za.co.digital.hellobuddy.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_wallet")
public class CustomerWallet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false, unique = true)
	private String username;

	@Column(nullable = false, length = 255)
	private String password;

	@Column(unique = true, nullable = false)
	private String referenceNumber;

	@Column(nullable = false)
	private String firstName;

	@Column(nullable = false)
	private String lastName;

	@Column(nullable = false)
	private BigDecimal walletBalance = BigDecimal.ZERO;

	private String twoFactorCode;
	private LocalDateTime twoFactorExpiry;

	@Column(name = "secret_key")
	private String secretKey;

	@Column(name = "require_password_change", nullable = false)
	private boolean requirePasswordChange = false;
	@Column(name = "allow_two_factor", nullable = false)
	private boolean isTwoFactorEnabled = false;

	public CustomerWallet() {
	}

	public CustomerWallet(String email, String username, String password, String referenceNumber, String firstName, String lastName, boolean isTwoFactorEnabled) {
		this.email = email;
		this.username = username;
		this.password = password;
		this.referenceNumber = referenceNumber;
		this.firstName = firstName;
		this.lastName = lastName;
		this.walletBalance = BigDecimal.ZERO;
		this.requirePasswordChange = false;
		this.isTwoFactorEnabled = isTwoFactorEnabled;
	}

	// Getters and Setters
	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getReferenceNumber() {
		return referenceNumber;
	}

	public void setReferenceNumber(String referenceNumber) {
		this.referenceNumber = referenceNumber;
	}

	// Alias Getter & Setter for customerRef (Maps to referenceNumber)
	public String getCustomerRef() {
		return referenceNumber;
	}

	public void setCustomerRef(String customerRef) {
		this.referenceNumber = customerRef;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public BigDecimal getWalletBalance() {
		return walletBalance;
	}

	public void setWalletBalance(BigDecimal walletBalance) {
		this.walletBalance = walletBalance;
	}

	public String getTwoFactorCode() {
		return twoFactorCode;
	}

	public void setTwoFactorCode(String twoFactorCode) {
		this.twoFactorCode = twoFactorCode;
	}

	public LocalDateTime getTwoFactorExpiry() {
		return twoFactorExpiry;
	}

	public void setTwoFactorExpiry(LocalDateTime twoFactorExpiry) {
		this.twoFactorExpiry = twoFactorExpiry;
	}

	public String getSecretKey() {
		return secretKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	public boolean isRequirePasswordChange() {
		return requirePasswordChange;
	}

	public void setRequirePasswordChange(boolean requirePasswordChange) {
		this.requirePasswordChange = requirePasswordChange;
	}

	public boolean isTwoFactorEnabled() {
		return isTwoFactorEnabled;
	}

	public void setTwoFactorEnabled(boolean isTwoFactorEnabled) {
		this.isTwoFactorEnabled = isTwoFactorEnabled;
	}
}