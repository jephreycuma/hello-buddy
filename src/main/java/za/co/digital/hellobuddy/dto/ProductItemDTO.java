package za.co.digital.hellobuddy.dto;

import java.math.BigDecimal;

public class ProductItemDTO {
	private Integer id;
	private String name;
	private String network; // <-- Added this field
	private BigDecimal price;
	private String displayPrice;
	private String type;
	private String description;
	private String logoUrl;
    private Double usdPrice;
    private double minLimit;
    private double maxLimit; 

	public ProductItemDTO() {
	}

	public ProductItemDTO(Integer id, String name, String network, BigDecimal price, String displayPrice, String type,
			String description,String logoUrl,Double usdPrice) {
		this.id = id;
		this.name = name;
		this.network = network;
		this.price = price;
		this.displayPrice = displayPrice;
		this.type = type;
		this.description = description;
		this.logoUrl = logoUrl;
		this.usdPrice = usdPrice;
	}

	// Include getters and setters for the new network field
	public String getNetwork() {
		return network;
	}

	public void setNetwork(String network) {
		this.network = network;
	}

	// Keep all your other existing getters and setters exactly as they were...
	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public String getDisplayPrice() {
		return displayPrice;
	}

	public void setDisplayPrice(String displayPrice) {
		this.displayPrice = displayPrice;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getLogoUrl() {
		return logoUrl;
	}

	public void setLogoUrl(String logoUrl) {
		this.logoUrl = logoUrl;
	}

	public Double getUsdPrice() {
		return usdPrice;
	}

	public void setUsdPrice(Double usdPrice) {
		this.usdPrice = usdPrice;
	}

	public double getMinLimit() {
		return minLimit;
	}

	public void setMinLimit(double minLimit) {
		this.minLimit = minLimit;
	}

	public double getMaxLimit() {
		return maxLimit;
	}

	public void setMaxLimit(double maxLimit) {
		this.maxLimit = maxLimit;
	}
}