package za.co.digital.hellobuddy.dto;

import java.math.BigDecimal;

public class Product {
	private String id;
	private String network;
	private String type; 
	private String pinType; 
	private double price; 
	private String description;
	private boolean generateLocalDenominations;
	private String currencySymbol; 
	private String destinationCurrencyCode; 
	private String logoUrl;
	private BigDecimal commission;
	
	// FX Data Translation Fields
	private Double fxRate;      // Exchange rate from Reloadly (e.g., 18.15)
	private Double usdPrice;    // Calculated wholesale or markup cost in USD

	// Updated Constructor
	public Product(String id, String network, String type, String pinType, double price, String description,
			boolean generateLocalDenominations, String currencySymbol, String destinationCurrencyCode, String logoUrl,
			Double fxRate, BigDecimal commission) {
		this.id = id;
		this.network = network;
		this.type = type;
		this.pinType = pinType;
		this.price = price;
		this.description = description;
		this.generateLocalDenominations = generateLocalDenominations;
		this.currencySymbol = currencySymbol; 
		this.destinationCurrencyCode = destinationCurrencyCode; 
		this.logoUrl = logoUrl;
		this.commission = commission;
		
		// Map the FX context details dynamically
		this.fxRate = fxRate;
		if (fxRate != null && fxRate > 0) {
			this.usdPrice = this.price / fxRate;
		} else {
			this.usdPrice = 0.0;
		}
	}

	// Getters and Setters
	public String getId() {
		return id;
	}

	public String getNetwork() {
		return network;
	}

	public String getType() {
		return type;
	}

	public String getPinType() {
		return pinType;
	}

	public double getPrice() {
		return price;
	}

	public String getDescription() {
		return description;
	}

	public boolean isGenerateLocalDenominations() {
		return generateLocalDenominations;
	}

	public void setGenerateLocalDenominations(boolean generateLocalDenominations) {
		this.generateLocalDenominations = generateLocalDenominations;
	}

	public String getCurrencySymbol() {
		return currencySymbol;
	}

	public void setCurrencySymbol(String currencySymbol) {
		this.currencySymbol = currencySymbol;
	}

	public String getDestinationCurrencyCode() {
		return destinationCurrencyCode;
	}

	public void setDestinationCurrencyCode(String destinationCurrencyCode) {
		this.destinationCurrencyCode = destinationCurrencyCode;
	}

	public String getLogoUrl() {
		return logoUrl;
	}

	public void setLogoUrl(String logoUrl) {
		this.logoUrl = logoUrl;
	}

	public Double getFxRate() {
		return fxRate;
	}

	// Setting the fxRate automatically updates the calculated usdPrice field
	public void setFxRate(Double fxRate) {
		this.fxRate = fxRate;
		if (this.price > 0 && fxRate != null && fxRate > 0) {
			this.usdPrice = this.price / fxRate;
		}
	}

	public Double getUsdPrice() {
		return usdPrice;
	}

	public void setUsdPrice(Double usdPrice) {
		this.usdPrice = usdPrice;
	}

	public BigDecimal getCommission() {
		return commission;
	}

	public void setCommission(BigDecimal commission) {
		this.commission = commission;
	}
}