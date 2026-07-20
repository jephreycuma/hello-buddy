package za.co.digital.hellobuddy.cache;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

import za.co.digital.hellobuddy.dto.Product;
import za.co.digital.hellobuddy.dto.ProductItemDTO;
import za.co.digital.hellobuddy.util.SpringContextUtil;


public class HelloBuddyInnerMemory {
	
	private static HelloBuddyInnerMemory instance;
	private static Map<String,Map<String, List<ProductItemDTO>>> catalogMaps = new HashMap<>();
	private static long TIME_OUT_IN_MINUTES = 240000;
	
	private HelloBuddyInnerMemory(RestClient restClient, String countryIso, double platformMarkup) {
		loadReloadlyProducts(restClient, countryIso, platformMarkup);
	}

	public static HelloBuddyInnerMemory getInstance(RestClient restClient, String countryIso, double platformMarkup) {
		StringRedisTemplate redisTemplate = getRedisTemplate();
		 String lastLoadTime = redisTemplate.opsForValue().get("loadTime:"+countryIso);
		 long lastLoadTimeMillis = (lastLoadTime != null) ? Long.parseLong(lastLoadTime) : 0;
		if(instance == null) {
			instance = new HelloBuddyInnerMemory(restClient, countryIso, platformMarkup);
		}else if(catalogMaps.get(countryIso) == null ||lastLoadTimeMillis < (System.currentTimeMillis() - TIME_OUT_IN_MINUTES)) {
			instance = new HelloBuddyInnerMemory(restClient, countryIso, platformMarkup);	
		}
		return instance;
	}
	
	public Map<String, List<ProductItemDTO>> getReloadlyProducts(String countryIso){
		return catalogMaps.get(countryIso);
	}
	
	private static StringRedisTemplate getRedisTemplate() {
		 StringRedisTemplate redisTemplate = null;
	    try {
	        redisTemplate = SpringContextUtil.getBean(StringRedisTemplate.class);
	    } catch (Exception ex) {
	        System.err.println("Redis Template could not be fetched from Spring context: " + ex.getMessage());
	    }
	    return redisTemplate;
	}

	private void loadReloadlyProducts(RestClient restClient, String countryIso, double platformMarkup) {
	    Map<String, List<ProductItemDTO>> catalogMap = new HashMap<>();
	    List<ProductItemDTO> airtimeList = new ArrayList<>();
	    List<ProductItemDTO> topupList = new ArrayList<>();
	    List<ProductItemDTO> dataList = new ArrayList<>();
	    List<ProductItemDTO> giftCardsList = new ArrayList<>();
	    
	    System.out.println("Platform markup: " + platformMarkup);
	    StringRedisTemplate redisTemplate = getRedisTemplate();
	    
	    try {
	        List<Product> remoteProducts = restClient.get()
	                .uri(uriBuilder -> uriBuilder
	                        .path("/api/v1/telecom/products")
	                        .queryParam("country", countryIso)
	                        .build())
	                .retrieve()
	                .body(new ParameterizedTypeReference<List<Product>>() {});

	        if (remoteProducts != null) {
	            for (Product prod : remoteProducts) {
	                // MOVE DECLARATION INSIDE THE LOOP: Guarantees no variable leakage across loops
	                ProductItemDTO dto = null;
	                
	                BigDecimal price = BigDecimal.valueOf(prod.getPrice());
	                String displayPrice = prod.getCurrencySymbol() + String.format(java.util.Locale.US, "%.2f", prod.getPrice());
	                
	                String cleanedNetwork = getNetworkName(prod.getNetwork());
	                String detailedName = cleanedNetwork + " " + displayPrice;
	                String type = prod.getType();                   
	                
	                String description = (prod.getDescription() != null && !prod.getDescription().trim().isEmpty()) 
	                                     ? prod.getDescription() 
	                                     : "Premium high-speed standard topup package delivery.";
	                
	                double purchasePrice = prod.getUsdPrice() + platformMarkup;

	                if (redisTemplate != null && prod.getFxRate() != null && prod.getCurrencySymbol() != null) {
	                    
	                    String redisKey = "fx:" + countryIso.toUpperCase() + "_" + prod.getDestinationCurrencyCode().toUpperCase();
	                    String fxValueString = String.valueOf(prod.getFxRate());
	                    
	                    // Save to Redis with a TTL of 1 hour (or customize as needed)
	                    redisTemplate.opsForValue().set(redisKey, fxValueString, 1, TimeUnit.HOURS);
	                    redisTemplate.opsForValue().set(countryIso, prod.getDestinationCurrencyCode().toUpperCase(), 1, TimeUnit.HOURS);
	                    String commisionValueString = (prod.getCommission() != null) ? prod.getCommission().toString() : "0.0";
	                    redisTemplate.opsForValue().set(countryIso+":"+prod.getId(), commisionValueString, 1, TimeUnit.HOURS);
	                }
	                // --- REDIS CACHING BLOCK END ---
	                if (purchasePrice > 0.26) {
	                    dto = new ProductItemDTO(
	                            Integer.parseInt(prod.getId().replaceAll("\\D", "")),
	                            description, 
	                            cleanedNetwork, 
	                            price,
	                            displayPrice,
	                            type,
	                            detailedName,
	                            prod.getLogoUrl(),
	                            purchasePrice
	                    );
	                }

	                // Handle routing strictly for the current product context loop pass
	                if ("AIRTIME TOPUP".equalsIgnoreCase(prod.getType())) {
	                    // Call your local selector layout generator safely
	                    generateLocalDenominations(cleanedNetwork, prod, topupList, platformMarkup);
	                } else if (dto != null) {
	                    // Items that require a valid dto object instance to be captured safely
	                    if ("DATA BUNDLES".equalsIgnoreCase(prod.getType())) {
	                        dataList.add(dto);
	                    } else if ("AIRTIME VOUCHER".equalsIgnoreCase(prod.getType())) {
	                        airtimeList.add(dto);
	                    } else {
	                        giftCardsList.add(dto);
	                    }
	                }
	            }
	        }
	    } catch (Exception e) {
	        System.err.println("Failed to fetch upstream catalog properties: " + e.getMessage());
	        e.printStackTrace(); // Keep stack traces visible for easier troubleshooting
	    }

	    catalogMap.put("Airtime", airtimeList);
	    catalogMap.put("TopUps", topupList); 
	    catalogMap.put("Data", dataList);
	    catalogMap.put("GiftCards", giftCardsList);
	    
	    catalogMaps.put(countryIso, catalogMap); 
	    redisTemplate.opsForValue().set("loadTime:"+countryIso, Long.toString(System.currentTimeMillis()), 1, TimeUnit.HOURS);
	}
	
	private void generateLocalDenominations(String cleanedNetwork, Product prod,List<ProductItemDTO> topupList,double platformMarkup) {
		
		
	    double amounts[] = getMinAndMaxAmounts(prod.getDescription());
	    double minAmount = amounts[0];
	    double maxAmount = amounts[1];
	    
    	String []denominations = denominations(prod.getDescription(), prod, platformMarkup);
    	for(String denomination : denominations) {
    		String displayPrice = prod.getCurrencySymbol() + String.format("%.2f", Double.parseDouble(denomination));
    		BigDecimal price = BigDecimal.valueOf(Double.parseDouble(denomination));
    		String detailedName = cleanedNetwork + " Fixed " + displayPrice;
    		
    		double fxRate = (prod.getFxRate() != null && prod.getFxRate() > 0) ? prod.getFxRate() : 1.0;        
    		double purchasePrice = (Double.parseDouble(denomination) / fxRate) + platformMarkup;
    		if(purchasePrice < 0.5) {
    			purchasePrice =purchasePrice+0.50;
    		}
	    	 ProductItemDTO dto = new ProductItemDTO(
	                 Integer.parseInt(prod.getId().replaceAll("\\D", "")),
	                 detailedName, 
	                 cleanedNetwork, 
	                 price,
	                 displayPrice,
	                 prod.getType(),
	                 "",
	                 prod.getLogoUrl(),
	                 purchasePrice
	         );
	    	 dto.setMinLimit(minAmount);
	    	 dto.setMaxLimit(maxAmount);
	    	 topupList.add(dto); 
	    	//}
    	}
    }
    
    private String getNetworkName(String network) {
        if (network == null || network.trim().isEmpty()) {
            return "Unknown Operator";
        }

        String[] countries = Countries.getCountries();
        String lowerNetwork = network.toLowerCase();
        
        for (String countryName : countries) {
            int index = lowerNetwork.indexOf(countryName.toLowerCase());
            if (index != -1) {
                return network.substring(0, index).trim();
            }
        }
        return network.trim();
    }
    
    private double[] getMinAndMaxAmounts(String text) {
    	double[] minAndMax = new double[2]; 
        
        if (text == null || text.trim().isEmpty()) {
            return minAndMax; // Returns [0.0, 0.0] safely
        }
    	
    	if (text != null && !text.contains("from") && !text.contains("to")) {
            text = text.replace(",", ".");
        }
    	Pattern pattern = Pattern.compile("\\d+[,.]\\d+");
        Matcher matcher = pattern.matcher(text);

        double firstAmount = 0.0;
        double secondAmount = 0.0;

        if (matcher.find()) {
            // Replace comma with dot so Double.parseDouble can read it safely
            String cleanNum = matcher.group().replace(",", ".");
            firstAmount = Double.parseDouble(cleanNum);
            minAndMax[0]=firstAmount;
        }
        if (matcher.find()) {
            String cleanNum = matcher.group().replace(",", ".");
            secondAmount = Double.parseDouble(cleanNum);
            minAndMax[1] = secondAmount;
        }
        return minAndMax;
    }
    
    private String[] denominations(String text, Product prod, double platformMarkup) {
        
        double[] minAndMax = getMinAndMaxAmounts(text);
        double firstAmount = minAndMax[0];
        double secondAmount = minAndMax[1];

        double fxRate = (prod.getFxRate() != null && prod.getFxRate() > 0) ? prod.getFxRate() : 1.0;        
        double minAmount = firstAmount / fxRate;
        double targetFirstAmount = firstAmount;
        
        if (minAmount + platformMarkup <= 0.5) {
            double requiredMinAmountLocal = 0.51 - platformMarkup;
            targetFirstAmount = requiredMinAmountLocal * fxRate;

            // FIX: Only cap at secondAmount if secondAmount is provided/greater than 0
            if (secondAmount > 0 && targetFirstAmount > secondAmount) {
                targetFirstAmount = secondAmount;
            }
        }

        // If there is no range (secondAmount is 0), pass targetFirstAmount for both boundaries
        double endAmount = (secondAmount > 0) ? secondAmount : targetFirstAmount;

        String[] denominationsArray = generate5RandomDenominations(targetFirstAmount, endAmount).toArray(new String[0]);
        return denominationsArray;
    }
    

    private List<String> generate5RandomDenominations(double startAmount, double endAmount) {
        List<Double> numericDenominations = new ArrayList<>();
        Random random = new Random();

        startAmount = Math.round(startAmount * 100.0) / 100.0;
        endAmount = Math.round(endAmount * 100.0) / 100.0;

        boolean useMultiplesOf10 = (endAmount >= 10) || (endAmount % 10 == 0);

        if (startAmount != endAmount) {
            numericDenominations.add(endAmount);
        }

        int maxAttempts = 500; 
        int attempts = 0;

        while (numericDenominations.size() < 4 && attempts < maxAttempts) {
            attempts++;
            double randomValue;

            if (useMultiplesOf10) {
                int minBound = (int) Math.ceil(startAmount / 10.0);
                int maxBound = (int) Math.floor(endAmount / 10.0);

                if (maxBound >= minBound) {
                    int randomMultiplier = random.nextInt((maxBound - minBound) + 1) + minBound;
                    randomValue = randomMultiplier * 10.0;
                } else {
                    randomValue = startAmount + (endAmount - startAmount) * random.nextDouble();
                }
            } else {
                randomValue = startAmount + (endAmount - startAmount) * random.nextDouble();
            }

            randomValue = Math.round(randomValue * 100.0) / 100.0;

            if (randomValue >= startAmount && randomValue <= endAmount && !numericDenominations.contains(randomValue)) {
                numericDenominations.add(randomValue);
            }
        }
        Collections.sort(numericDenominations);
        List<String> finalDenominations = new ArrayList<>();
        for (double val : numericDenominations) {
        	finalDenominations.add(String.format(java.util.Locale.US, "%.2f", val));
        }

        return finalDenominations;
    }
}
