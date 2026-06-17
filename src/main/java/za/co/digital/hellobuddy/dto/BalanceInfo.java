package za.co.digital.hellobuddy.dto;

public class BalanceInfo {

	private Double oldBalance;
	private Double newBalance;
	private Double cost;
	private String currencyCode;
	private String currencyName;
	private String updatedAt;

	// Getters and Setters
	public Double getOldBalance() {
		return oldBalance;
	}

	public void setOldBalance(Double oldBalance) {
		this.oldBalance = oldBalance;
	}

	public Double getNewBalance() {
		return newBalance;
	}

	public void setNewBalance(Double newBalance) {
		this.newBalance = newBalance;
	}

	public Double getCost() {
		return cost;
	}

	public void setCost(Double cost) {
		this.cost = cost;
	}

	public String getCurrencyCode() {
		return currencyCode;
	}

	public void setCurrencyCode(String currencyCode) {
		this.currencyCode = currencyCode;
	}

	public String getCurrencyName() {
		return currencyName;
	}

	public void setCurrencyName(String currencyName) {
		this.currencyName = currencyName;
	}

	public String getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(String updatedAt) {
		this.updatedAt = updatedAt;
	}
}