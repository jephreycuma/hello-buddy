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

    public ProductItemDTO() {}
    
    public ProductItemDTO(Integer id, String name, String network, BigDecimal price, String displayPrice, String type, String description) {
        this.id = id;
        this.name = name;
        this.network = network;
        this.price = price;
        this.displayPrice = displayPrice;
        this.type = type;
        this.description = description;
    }

    // Include getters and setters for the new network field
    public String getNetwork() { return network; }
    public void setNetwork(String network) { this.network = network; }

    // Keep all your other existing getters and setters exactly as they were...
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getDisplayPrice() { return displayPrice; }
    public void setDisplayPrice(String displayPrice) { this.displayPrice = displayPrice; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}