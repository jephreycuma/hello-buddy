package za.co.digital.hellobuddy.cache;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import za.co.digital.hellobuddy.dto.Product;
import za.co.digital.hellobuddy.dto.ProductItemDTO;

public class HelloBuddyInnerMemory {
	
	private static HelloBuddyInnerMemory instance;
	Map<String, List<ProductItemDTO>> catalogMap = new HashMap<>();
	private static Map<String,Map<String, List<ProductItemDTO>>> catalogMaps = new HashMap<>();
	private static long southAfricaLastLoadTime = 0;//86400000 millisecs = 24 hours
	private static long nigeriaLastLoadTime = 0;
	private static long TIME_OUT_IN_MINUTES = 240000;
	
	private HelloBuddyInnerMemory(RestClient restClient, String countryIso) {
		loadReloadlyProducts(restClient, countryIso);
	}

	public static HelloBuddyInnerMemory getInstance(RestClient restClient, String countryIso) {
		if(instance == null) {
			instance = new HelloBuddyInnerMemory(restClient, countryIso);
		}else if(catalogMaps.get(countryIso) == null ||
				(countryIso.equals("ZA") && southAfricaLastLoadTime < (System.currentTimeMillis() - TIME_OUT_IN_MINUTES))) {
			instance = new HelloBuddyInnerMemory(restClient, countryIso);	
		}else if(catalogMaps.get(countryIso) == null ||
				(countryIso.equals("NG") && nigeriaLastLoadTime < (System.currentTimeMillis() - TIME_OUT_IN_MINUTES))) {
			instance = new HelloBuddyInnerMemory(restClient, countryIso);
		}
		return instance;
	}
	
	public Map<String, List<ProductItemDTO>> getReloadlyProducts(String countryIso){
		return catalogMaps.get(countryIso);
	}
	
	private void loadReloadlyProducts(RestClient restClient, String countryIso) {
		
		 // Initialize the targeted container array contexts
        List<ProductItemDTO> airtimeList = new ArrayList<>();
        List<ProductItemDTO> topupList = new ArrayList<>();
        List<ProductItemDTO> dataList = new ArrayList<>();
        List<ProductItemDTO> giftCardsList = new ArrayList<>();
        
        try {
            // 1. Fetch the products from the remote routing-service API endpoint
        	List<Product> remoteProducts = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/telecom/products")
                            .queryParam("country", countryIso) // Sends ?country=ZA or ?country=NG to your backend service
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Product>>() {});

            if (remoteProducts != null) {
                for (Product prod : remoteProducts) {
                	
                    BigDecimal price = BigDecimal.valueOf(prod.getPrice());
                    String displayPrice = prod.getCurrencySymbol() + String.format("%.2f", prod.getPrice());
                    
                    String cleanedNetwork = getNetworkName(prod.getNetwork());
                    
                    String detailedName = cleanedNetwork + " " + displayPrice;
                    
                    String type = prod.getType();
                    
                    String description = (prod.getDescription() != null && !prod.getDescription().trim().isEmpty()) 
                                         ? prod.getDescription() 
                                         : "Premium high-speed standard topup package delivery.";

                    // Build the updated product DTO
                    ProductItemDTO dto = new ProductItemDTO(
                            Integer.parseInt(prod.getId().replaceAll("\\D", "")),
                            description, 
                            cleanedNetwork, 
                            price,
                            displayPrice,
                            type,
                            detailedName,
                            prod.getLogoUrl(),
                            prod.getUsdPrice()
                    );

                    if ("DATA BUNDLES".equalsIgnoreCase(prod.getType())) {
                        dataList.add(dto);
                    } else if ("AIRTIME VOUCHER".equalsIgnoreCase(prod.getType())) {
                        airtimeList.add(dto);
                    } else if ("AIRTIME TOPUP".equalsIgnoreCase(prod.getType())) {
                    	generateLocalDenominations(cleanedNetwork, prod,topupList);
                        //topupList.add(dto);                        
                    } else {
                        giftCardsList.add(dto);
                    }
                }
            }
        } catch (Exception e) {
            // Fallback strategy: log failure and render empty lists gracefully or load system default objects
            System.err.println("Failed to fetch upstream catalog properties: " + e.getMessage());
        }

        // 4. Wrap elements back into the structure your script layer expects
        catalogMap.put("Airtime", airtimeList);
        catalogMap.put("TopUps", topupList); 
        catalogMap.put("Data", dataList);
        catalogMap.put("GiftCards", giftCardsList);
        
        catalogMaps.put(countryIso,catalogMap);	
        updateLoadTime(countryIso);
	}
	
	private void updateLoadTime(String countryIso) {
		switch(countryIso) {
		case "ZA":
			southAfricaLastLoadTime = System.currentTimeMillis();
			break;
		case "NG":
			nigeriaLastLoadTime = System.currentTimeMillis();
		break;
		default:
			
		}
	}
	
private void generateLocalDenominations(String cleanedNetwork, Product prod,List<ProductItemDTO> topupList) {
    	
    	String []denominations = denominations(prod);
    	for(String denomination : denominations) {
    		//prod.getDescription(), detailedName
    		String displayPrice = prod.getCurrencySymbol() + String.format("%.2f", Double.parseDouble(denomination));
    		BigDecimal price = BigDecimal.valueOf(Double.parseDouble(denomination));
    		String detailedName = cleanedNetwork + " Fixed " + displayPrice;
    	 ProductItemDTO dto = new ProductItemDTO(
                 Integer.parseInt(prod.getId().replaceAll("\\D", "")),
                 detailedName, 
                 cleanedNetwork, 
                 price,
                 displayPrice,
                 prod.getType(),
                 "",
                 prod.getLogoUrl(),
                 prod.getUsdPrice()
         );
    	 topupList.add(dto); 
    	}
    }
    
    private String[] denominations(Product prod) {
    	String []denominations = {"10.00", "20.00", "25.00", "50.00", "100.00", "150.00", "200.00", "250.00", "500.00", "1000.00"};
    	
    	switch(prod.getDestinationCurrencyCode()) {
    		case "ZAR":
				return denominations;
			case "NGN":
				return new String[] {"100.00", "200.00", "500.00", "1000.00", "1500.00", "2000.00", "2500.00", "5000.00", "10000.00"};
			default:
				return denominations; // Default to ZAR denominations if currency is unrecognized
    	}
    }
    
    private String getNetworkName(String network) {
    	if(network != null) {
			if(network.contains("Telkom Mobile South Africa")) {
				return "Telkom";
			}
    	}
		switch(network) {
		case "Vodacom South Africa":
		case "Vodacom South Africa Data":
			return "Vodacom";
		case "MTN South Africa":
		case "MTN South Africa Data":
			return "MTN";
		case "Cell C South Africa":
		case "Cell C South Africa Data":
			return "Cell C";
		case "ONE kuphela Universal South Africa PIN":
			return "ONE";
		case "Airtel Nigeria Bundle":
		case "Airtel Nigeria Data":
		case "Airtel Nigeria Special Bundle":
		case "Airtel Nigeria":
			return "Airtel";
		case "Glo Nigeria":
		case "Glo Nigeria Bundle":
		case "Glo Nigeria Data":
		case "Glo Nigeria Special Bundle":
			return "Glo";
		case "MTN Nigeria":
		case "MTN Nigeria Bundles":
		case "MTN Nigeria Data":
			return "MTN Nigeria";
		case "T2 Mobile Nigeria":
		case "T2 Mobile Nigeria Data":
			return "T2 Mobile";
		default:
			return network;
		}
	}
}
