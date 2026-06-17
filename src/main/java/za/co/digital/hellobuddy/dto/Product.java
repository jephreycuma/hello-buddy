package za.co.digital.hellobuddy.dto;

public class Product {
    private String id;
    private String network; // VODACOM, MTN, CELL_C, TELKOM
    private String type;    // AIRTIME, DATA
    private String pinType; // PIN, PINLESS
    private double price;
    private String description;
    private boolean generateLocalDenominations;
    private String currencySymbol; // New field for currency symbol
    private String destinationCurrencyCode; // New field for destination currency code

    // Constructor
    public Product(String id, String network, String type, String pinType, double price, String description,boolean generateLocalDenominations, String currencySymbol, String destinationCurrencyCode) {
        this.id = id;
        this.network = network;
        this.type = type;
        this.pinType = pinType;
        this.price = price;
        this.description = description;
        this.generateLocalDenominations = generateLocalDenominations;
        this.currencySymbol = currencySymbol; // Initialize the new field
        this.destinationCurrencyCode = destinationCurrencyCode; // Initialize the new field
    }

    // Getters and Setters
    public String getId() { return id; }
    public String getNetwork() { return network; }
    public String getType() { return type; }
    public String getPinType() { return pinType; }
    public double getPrice() { return price; }
    public String getDescription() { return description; }

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
}