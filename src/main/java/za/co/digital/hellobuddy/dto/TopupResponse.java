package za.co.digital.hellobuddy.dto;

public class TopupResponse {

	private Long transactionId;
	private String status;
	private String operatorTransactionId; // Kept as String to handle null/alphanumeric IDs
	private String customIdentifier;
	private String recipientPhone;
	private String recipientEmail;
	private String senderPhone;
	private String countryCode;
	private Integer operatorId;
	private String operatorName;
	private Double discount;
	private String discountCurrencyCode;
	private Double requestedAmount;
	private String requestedAmountCurrencyCode;
	private Double deliveredAmount;
	private String deliveredAmountCurrencyCode;
	private String transactionDate; // Can be a String or LocalDateTime with custom deserializer
	private String pinDetail; // Kept as String to handle nulls safely
	private Double fee;

	private BalanceInfo balanceInfo;

	// Getters and Setters
	public Long getTransactionId() {
		return transactionId;
	}

	public void setTransactionId(Long transactionId) {
		this.transactionId = transactionId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getOperatorTransactionId() {
		return operatorTransactionId;
	}

	public void setOperatorTransactionId(String operatorTransactionId) {
		this.operatorTransactionId = operatorTransactionId;
	}

	public String getCustomIdentifier() {
		return customIdentifier;
	}

	public void setCustomIdentifier(String customIdentifier) {
		this.customIdentifier = customIdentifier;
	}

	public String getRecipientPhone() {
		return recipientPhone;
	}

	public void setRecipientPhone(String recipientPhone) {
		this.recipientPhone = recipientPhone;
	}

	public String getRecipientEmail() {
		return recipientEmail;
	}

	public void setRecipientEmail(String recipientEmail) {
		this.recipientEmail = recipientEmail;
	}

	public String getSenderPhone() {
		return senderPhone;
	}

	public void setSenderPhone(String senderPhone) {
		this.senderPhone = senderPhone;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public void setCountryCode(String countryCode) {
		this.countryCode = countryCode;
	}

	public Integer getOperatorId() {
		return operatorId;
	}

	public void setOperatorId(Integer operatorId) {
		this.operatorId = operatorId;
	}

	public String getOperatorName() {
		return operatorName;
	}

	public void setOperatorName(String operatorName) {
		this.operatorName = operatorName;
	}

	public Double getDiscount() {
		return discount;
	}

	public void setDiscount(Double discount) {
		this.discount = discount;
	}

	public String getDiscountCurrencyCode() {
		return discountCurrencyCode;
	}

	public void setDiscountCurrencyCode(String discountCurrencyCode) {
		this.discountCurrencyCode = discountCurrencyCode;
	}

	public Double getRequestedAmount() {
		return requestedAmount;
	}

	public void setRequestedAmount(Double requestedAmount) {
		this.requestedAmount = requestedAmount;
	}

	public String getRequestedAmountCurrencyCode() {
		return requestedAmountCurrencyCode;
	}

	public void setRequestedAmountCurrencyCode(String requestedAmountCurrencyCode) {
		this.requestedAmountCurrencyCode = requestedAmountCurrencyCode;
	}

	public Double getDeliveredAmount() {
		return deliveredAmount;
	}

	public void setDeliveredAmount(Double deliveredAmount) {
		this.deliveredAmount = deliveredAmount;
	}

	public String getDeliveredAmountCurrencyCode() {
		return deliveredAmountCurrencyCode;
	}

	public void setDeliveredAmountCurrencyCode(String deliveredAmountCurrencyCode) {
		this.deliveredAmountCurrencyCode = deliveredAmountCurrencyCode;
	}

	public String getTransactionDate() {
		return transactionDate;
	}

	public void setTransactionDate(String transactionDate) {
		this.transactionDate = transactionDate;
	}

	public String getPinDetail() {
		return pinDetail;
	}

	public void setPinDetail(String pinDetail) {
		this.pinDetail = pinDetail;
	}

	public Double getFee() {
		return fee;
	}

	public void setFee(Double fee) {
		this.fee = fee;
	}

	public BalanceInfo getBalanceInfo() {
		return balanceInfo;
	}

	public void setBalanceInfo(BalanceInfo balanceInfo) {
		this.balanceInfo = balanceInfo;
	}
}