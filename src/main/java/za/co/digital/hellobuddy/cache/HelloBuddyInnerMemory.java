package za.co.digital.hellobuddy.cache;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import za.co.digital.hellobuddy.dto.Product;
import za.co.digital.hellobuddy.dto.ProductItemDTO;
import za.co.digital.hellobuddy.service.ProfitValidator;

@Component
public class HelloBuddyInnerMemory {

    private static final Logger log = LoggerFactory.getLogger(HelloBuddyInnerMemory.class);

    private static final Pattern NON_DIGIT_PATTERN = Pattern.compile("\\D");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\d+[,.]\\d+");
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    // In-memory catalog mapping per country code
    private final Map<String, Map<String, List<ProductItemDTO>>> nonRegisteredUsersCatalogMaps = new ConcurrentHashMap<>();
    private final Map<String, Map<String, List<ProductItemDTO>>> registeredUsersCatalogMaps = new ConcurrentHashMap<>();

    private final StringRedisTemplate redisTemplate;
    private final RestClient restClient;
    private final ProfitValidator profitValidator;
    
    @Value("${south.african.fx:15.35}")
    private String southAfricanFx;

    // Constructor Injection managed by Spring
    public HelloBuddyInnerMemory(
            StringRedisTemplate redisTemplate,
            RestClient restClient,
            ProfitValidator profitValidator) {
        this.redisTemplate = redisTemplate;
        this.restClient = restClient;
        this.profitValidator = profitValidator;
    }

    /**
     * Main entry point to retrieve cached products. 
     * Refreshes from Reloadly ONLY if expired or not in memory.
     */
    public Map<String, List<ProductItemDTO>> getReloadlyProductsForNonRegisteredUsers(String countryIso) {
        long lastLoadTimeMillis = 0;

        if (redisTemplate != null) {
            String lastLoadTime = redisTemplate.opsForValue().get("loadTime:" + countryIso);
            if (lastLoadTime != null) {
                try {
                    lastLoadTimeMillis = Long.parseLong(lastLoadTime);
                } catch (NumberFormatException ignored) {}
            }
        }

        boolean isExpired = (System.currentTimeMillis() - lastLoadTimeMillis) > CACHE_TTL.toMillis();

        // Load if not in memory or cache expired
        if (!nonRegisteredUsersCatalogMaps.containsKey(countryIso) || isExpired) {
            synchronized (this) {
                if (!nonRegisteredUsersCatalogMaps.containsKey(countryIso) || isExpired) {
                	loadReloadlyProductsForNonRegisteredUsers(countryIso);
                }
            }
        }

        return nonRegisteredUsersCatalogMaps.get(countryIso);
    }
    
    /**
     * Main entry point to retrieve cached products. 
     * Refreshes from Reloadly ONLY if expired or not in memory.
     */
    public Map<String, List<ProductItemDTO>> getReloadlyProductsForRegisteredUsers(String countryIso) {
        long lastLoadTimeMillis = 0;

        if (redisTemplate != null) {
            String lastLoadTime = redisTemplate.opsForValue().get("loadTime:" + countryIso);
            if (lastLoadTime != null) {
                try {
                    lastLoadTimeMillis = Long.parseLong(lastLoadTime);
                } catch (NumberFormatException ignored) {}
            }
        }

        boolean isExpired = (System.currentTimeMillis() - lastLoadTimeMillis) > CACHE_TTL.toMillis();

        // Load if not in memory or cache expired
        if (!registeredUsersCatalogMaps.containsKey(countryIso) || isExpired) {
            synchronized (this) {
                if (!registeredUsersCatalogMaps.containsKey(countryIso) || isExpired) {
                	loadReloadlyProductsForRegisteredUsers(countryIso);
                }
            }
        }

        return registeredUsersCatalogMaps.get(countryIso);
    }

    private void loadReloadlyProductsForNonRegisteredUsers(String countryIso) {
        log.info("Fetching fresh Reloadly catalog for country: {}", countryIso);

        Map<String, List<ProductItemDTO>> catalogMap = new ConcurrentHashMap<>();
        List<ProductItemDTO> airtimeList = new ArrayList<>();
        List<ProductItemDTO> topupList = new ArrayList<>();
        List<ProductItemDTO> dataList = new ArrayList<>();
        List<ProductItemDTO> giftCardsList = new ArrayList<>();

        BigDecimal usdToZarRate = new BigDecimal(southAfricanFx);
        if (redisTemplate != null) {
            try {
                String cachedUsdZar = redisTemplate.opsForValue().get("fx:ZA_ZAR");
                if (cachedUsdZar != null && !cachedUsdZar.isBlank()) {
                    usdToZarRate = new BigDecimal(cachedUsdZar.trim());
                }
            } catch (Exception ex) {
                log.warn("Could not fetch USD_ZAR rate from Redis, falling back to default {}: {}", usdToZarRate, ex.getMessage());
            }
        }

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
                    BigDecimal price = BigDecimal.valueOf(prod.getPrice());
                    String displayPrice = prod.getCurrencySymbol() + String.format(Locale.US, "%.2f", prod.getPrice());
                    String cleanedNetwork = getNetworkName(prod.getNetwork());
                    String detailedName = cleanedNetwork + " " + displayPrice;
                    String type = prod.getType();

                    String description = (prod.getDescription() != null && !prod.getDescription().isBlank())
                            ? prod.getDescription()
                            : "Premium high-speed standard topup package delivery.";

                    double purchasePrice = prod.getUsdPrice();

                    if (redisTemplate != null && prod.getFxRate() != null && prod.getCurrencySymbol() != null) {
                        String redisKey = "fx:" + countryIso.toUpperCase() + "_" + prod.getDestinationCurrencyCode().toUpperCase()+"_"+prod.getId();
                        redisTemplate.opsForValue().set(redisKey, String.valueOf(prod.getFxRate()), CACHE_TTL);
                        redisTemplate.opsForValue().set(countryIso, prod.getDestinationCurrencyCode().toUpperCase(), CACHE_TTL);
                        if("ZA".equalsIgnoreCase(countryIso))
                        	redisTemplate.opsForValue().set("fx:ZA_ZAR", String.valueOf(prod.getFxRate()), CACHE_TTL);

                        String commissionValue = (prod.getCommission() != null) ? prod.getCommission().toString() : "0.0";
                        redisTemplate.opsForValue().set(countryIso + ":" + prod.getId(), commissionValue, CACHE_TTL);
                    }

                    int sanitizedId = parseId(prod.getId());

                    if ("AIRTIME TOPUP".equalsIgnoreCase(type)) {
                        generateLocalDenominations(cleanedNetwork, prod, sanitizedId, topupList, usdToZarRate,false);
                    } else {
                        BigDecimal localFxRate = (prod.getFxRate() != null && prod.getFxRate() > 0)
                                ? BigDecimal.valueOf(prod.getFxRate())
                                : BigDecimal.ONE;

                        BigDecimal reloadlyDiscount = (prod.getCommission() != null)
                                ? prod.getCommission()
                                : BigDecimal.ZERO;

                        ProfitValidator.EvaluationResult result = profitValidator.evaluateProduct(
                                price,
                                localFxRate,
                                usdToZarRate,
                                reloadlyDiscount
                        );

                        if (result.isAllowOnStorefront()) {
                            ProductItemDTO dto = new ProductItemDTO(
                                    sanitizedId,
                                    description,
                                    cleanedNetwork,
                                    price,
                                    displayPrice,
                                    type,
                                    detailedName,
                                    prod.getLogoUrl(),
                                    purchasePrice
                            );

                            if ("DATA BUNDLES".equalsIgnoreCase(type)) {
                                dataList.add(dto);
                            } else if ("AIRTIME VOUCHER".equalsIgnoreCase(type)) {
                                airtimeList.add(dto);
                            } else {
                                giftCardsList.add(dto);
                            }
                        } else {
                            log.info("SKIPPED UNPROFITABLE PRODUCT [{}] {}: {}", prod.getId(), detailedName, result);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch upstream catalog properties: {}", e.getMessage(), e);
        }

        catalogMap.put("Airtime", Collections.unmodifiableList(airtimeList));
        catalogMap.put("TopUps", Collections.unmodifiableList(topupList));
        catalogMap.put("Data", Collections.unmodifiableList(dataList));
        catalogMap.put("GiftCards", Collections.unmodifiableList(giftCardsList));

        nonRegisteredUsersCatalogMaps.put(countryIso, catalogMap);

        if (redisTemplate != null) {
            redisTemplate.opsForValue().set("loadTime:" + countryIso, String.valueOf(System.currentTimeMillis()), CACHE_TTL);
        }
    }
    
    
    private void loadReloadlyProductsForRegisteredUsers(String countryIso) {
        log.info("Fetching fresh Reloadly catalog for country: {}", countryIso);

        Map<String, List<ProductItemDTO>> catalogMap = new ConcurrentHashMap<>();
        List<ProductItemDTO> airtimeList = new ArrayList<>();
        List<ProductItemDTO> topupList = new ArrayList<>();
        List<ProductItemDTO> dataList = new ArrayList<>();
        List<ProductItemDTO> giftCardsList = new ArrayList<>();

        BigDecimal usdToZarRate = new BigDecimal(southAfricanFx);
        if (redisTemplate != null) {
            try {
                String cachedUsdZar = redisTemplate.opsForValue().get("fx:ZA_ZAR");
                if (cachedUsdZar != null && !cachedUsdZar.isBlank()) {
                    usdToZarRate = new BigDecimal(cachedUsdZar.trim());
                }
            } catch (Exception ex) {
                log.warn("Could not fetch USD_ZAR rate from Redis, falling back to default {}: {}", usdToZarRate, ex.getMessage());
            }
        }

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
                    BigDecimal price = BigDecimal.valueOf(prod.getPrice());
                    String displayPrice = prod.getCurrencySymbol() + String.format(Locale.US, "%.2f", prod.getPrice());
                    String cleanedNetwork = getNetworkName(prod.getNetwork());
                    String detailedName = cleanedNetwork + " " + displayPrice;
                    String type = prod.getType();

                    String description = (prod.getDescription() != null && !prod.getDescription().isBlank())
                            ? prod.getDescription()
                            : "Premium high-speed standard topup package delivery.";

                    double purchasePrice = prod.getUsdPrice();

                    if (redisTemplate != null && prod.getFxRate() != null && prod.getCurrencySymbol() != null) {
                    	
                    	String redisKey = "fx:"+countryIso.toUpperCase() + "_" + prod.getDestinationCurrencyCode().toUpperCase()+"_"+prod.getId();
                    	
                        redisTemplate.opsForValue().set(redisKey, String.valueOf(prod.getFxRate()), CACHE_TTL);
                        redisTemplate.opsForValue().set(countryIso, prod.getDestinationCurrencyCode().toUpperCase(), CACHE_TTL);
                        if("ZA".equalsIgnoreCase(countryIso))
                        	redisTemplate.opsForValue().set("fx:ZA_ZAR", String.valueOf(prod.getFxRate()), CACHE_TTL);

                        String commissionValue = (prod.getCommission() != null) ? prod.getCommission().toString() : "0.0";
                        redisTemplate.opsForValue().set(countryIso + ":" + prod.getId(), commissionValue, CACHE_TTL);
                    }

                    int sanitizedId = parseId(prod.getId());

                    if ("AIRTIME TOPUP".equalsIgnoreCase(type)) {
                        generateLocalDenominations(cleanedNetwork, prod, sanitizedId, topupList, usdToZarRate, true);
                    } else {
                            ProductItemDTO dto = new ProductItemDTO(
                                    sanitizedId,
                                    description,
                                    cleanedNetwork,
                                    price,
                                    displayPrice,
                                    type,
                                    detailedName,
                                    prod.getLogoUrl(),
                                    purchasePrice
                            );

                        if ("DATA BUNDLES".equalsIgnoreCase(type)) {
                            dataList.add(dto);
                        } else if ("AIRTIME VOUCHER".equalsIgnoreCase(type)) {
                            airtimeList.add(dto);
                        } else {
                            giftCardsList.add(dto);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch upstream catalog properties: {}", e.getMessage(), e);
        }

        catalogMap.put("Airtime", Collections.unmodifiableList(airtimeList));
        catalogMap.put("TopUps", Collections.unmodifiableList(topupList));
        catalogMap.put("Data", Collections.unmodifiableList(dataList));
        catalogMap.put("GiftCards", Collections.unmodifiableList(giftCardsList));

        registeredUsersCatalogMaps.put(countryIso, catalogMap);

        if (redisTemplate != null) {
            redisTemplate.opsForValue().set("loadTime:" + countryIso, String.valueOf(System.currentTimeMillis()), CACHE_TTL);
        }
    }

    private void generateLocalDenominations(
            String cleanedNetwork,
            Product prod,
            int parsedId,
            List<ProductItemDTO> topupList,
            BigDecimal usdToZarRate, boolean isRegisteredUser) {

        double[] amounts = getMinAndMaxAmounts(prod.getDescription());
        double rawMinAmount = amounts[0];
        double maxAmount = amounts[1];

        double calculatedMin = findNextProfitableDenomination(rawMinAmount, maxAmount, prod, usdToZarRate, 5.0);
        double effectiveMinAmount = (calculatedMin > 0 && !isRegisteredUser) ? calculatedMin : rawMinAmount;

        List<String> denominations = denominations(effectiveMinAmount, maxAmount, prod, usdToZarRate,isRegisteredUser);
        double fxRate = (prod.getFxRate() != null && prod.getFxRate() > 0) ? prod.getFxRate() : 1.0;

        for (String denominationStr : denominations) {
            double denomination = Double.parseDouble(denominationStr);
            BigDecimal price = BigDecimal.valueOf(denomination);

            String displayPrice = prod.getCurrencySymbol() + String.format(Locale.US, "%.2f", denomination);
            String detailedName = cleanedNetwork + " Fixed " + displayPrice;
            double purchasePrice = denomination / fxRate;

            ProductItemDTO dto = new ProductItemDTO(
                    parsedId,
                    detailedName,
                    cleanedNetwork,
                    price,
                    displayPrice,
                    prod.getType(),
                    "",
                    prod.getLogoUrl(),
                    purchasePrice
            );

            dto.setMinLimit(effectiveMinAmount);
            dto.setMaxLimit(maxAmount);
            topupList.add(dto);
        }
    }

    private String getNetworkName(String network) {
        if (network == null || network.isBlank()) return "Unknown Operator";
        String lowerNetwork = network.toLowerCase();
        for (String countryName : Countries.getCountries()) {
            int index = lowerNetwork.indexOf(countryName.toLowerCase());
            if (index != -1) return network.substring(0, index).trim();
        }
        return network.trim();
    }

    private double[] getMinAndMaxAmounts(String text) {
        if (text == null || text.isBlank()) return new double[]{0.0, 0.0};
        if (!text.contains("from") && !text.contains("to")) {
            text = text.replace(",", ".");
        }

        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        double firstAmount = 0.0, secondAmount = 0.0;

        if (matcher.find()) firstAmount = Double.parseDouble(matcher.group().replace(",", "."));
        if (matcher.find()) secondAmount = Double.parseDouble(matcher.group().replace(",", "."));

        return new double[]{firstAmount, secondAmount};
    }

    private List<String> denominations(double startAmount, double endAmount, Product prod, BigDecimal usdToZarRate, boolean isRegisteredUser) {
    	List<String> denominations = new ArrayList<>();
        double targetEndAmount = (endAmount > 0) ? endAmount : startAmount;
        if(isRegisteredUser)
        	denominations = generate5RandomRDenominationsForRegistered(startAmount, endAmount);
        else
        	denominations =  generate5RandomDenominations(startAmount, targetEndAmount, prod, usdToZarRate);
        return denominations;
    }

    private List<String> generate5RandomDenominations(double startAmount, double endAmount, Product prod, BigDecimal usdToZarRate) {
        Set<Double> numericDenominations = new TreeSet<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        startAmount = BigDecimal.valueOf(startAmount).setScale(2, RoundingMode.HALF_UP).doubleValue();
        endAmount = BigDecimal.valueOf(endAmount).setScale(2, RoundingMode.HALF_UP).doubleValue();

        double fxRate = (prod.getFxRate() != null && prod.getFxRate() > 0) ? prod.getFxRate() : 1.0;
        BigDecimal localFxRate = BigDecimal.valueOf(fxRate);
        BigDecimal reloadlyDiscount = (prod.getCommission() != null) ? prod.getCommission() : BigDecimal.ZERO;

        java.util.function.DoublePredicate isProfitable = (candidatePrice) -> {
            BigDecimal price = BigDecimal.valueOf(candidatePrice);
            ProfitValidator.EvaluationResult result = profitValidator.evaluateProduct(price, localFxRate, usdToZarRate, reloadlyDiscount);
            return result.isAllowOnStorefront();
        };

        if (Double.compare(startAmount, endAmount) != 0 && isProfitable.test(endAmount)) {
            numericDenominations.add(endAmount);
        }

        boolean useMultiplesOf10 = (endAmount >= 10) || (endAmount % 10 == 0);
        int maxAttempts = 100, attempts = 0;

        while (numericDenominations.size() < 4 && attempts < maxAttempts) {
            attempts++;
            double randomValue;

            if (useMultiplesOf10) {
                int minBound = (int) Math.ceil(startAmount / 10.0);
                int maxBound = (int) Math.floor(endAmount / 10.0);

                if (maxBound >= minBound) {
                    int randomMultiplier = random.nextInt(minBound, maxBound + 1);
                    randomValue = randomMultiplier * 10.0;
                } else {
                    randomValue = random.nextDouble(startAmount, endAmount + 0.01);
                }
            } else {
                randomValue = random.nextDouble(startAmount, endAmount + 0.01);
            }

            randomValue = BigDecimal.valueOf(randomValue).setScale(2, RoundingMode.HALF_UP).doubleValue();

            if (randomValue >= startAmount && randomValue <= endAmount && isProfitable.test(randomValue)) {
                numericDenominations.add(randomValue);
            }
        }

        if (numericDenominations.isEmpty() && isProfitable.test(startAmount)) {
            numericDenominations.add(startAmount);
        }

        List<String> finalDenominations = new ArrayList<>();
        for (double val : numericDenominations) {
            finalDenominations.add(String.format(Locale.US, "%.2f", val));
        }

        return finalDenominations;
    }
    
    private List<String> generate5RandomRDenominationsForRegistered(double startAmount, double endAmount) {
        Set<Double> numericDenominations = new TreeSet<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        startAmount = BigDecimal.valueOf(startAmount).setScale(2, RoundingMode.HALF_UP).doubleValue();
        endAmount = BigDecimal.valueOf(endAmount).setScale(2, RoundingMode.HALF_UP).doubleValue();

        //double fxRate = (prod.getFxRate() != null && prod.getFxRate() > 0) ? prod.getFxRate() : 1.0;
        //BigDecimal localFxRate = BigDecimal.valueOf(fxRate);
        //BigDecimal reloadlyDiscount = (prod.getCommission() != null) ? prod.getCommission() : BigDecimal.ZERO;

       /* java.util.function.DoublePredicate isProfitable = (candidatePrice) -> {
            BigDecimal price = BigDecimal.valueOf(candidatePrice);
            ProfitValidator.EvaluationResult result = profitValidator.evaluateProduct(price, localFxRate, usdToZarRate, reloadlyDiscount);
            return result.isAllowOnStorefront();
        };*/

        if (Double.compare(startAmount, endAmount) != 0) {
            numericDenominations.add(endAmount);
        }

        boolean useMultiplesOf10 = (endAmount >= 10) || (endAmount % 10 == 0);
        int maxAttempts = 100, attempts = 0;

        while (numericDenominations.size() < 4 && attempts < maxAttempts) {
            attempts++;
            double randomValue;

            if (useMultiplesOf10) {
                int minBound = (int) Math.ceil(startAmount / 10.0);
                int maxBound = (int) Math.floor(endAmount / 10.0);

                if (maxBound >= minBound) {
                    int randomMultiplier = random.nextInt(minBound, maxBound + 1);
                    randomValue = randomMultiplier * 10.0;
                } else {
                    randomValue = random.nextDouble(startAmount, endAmount + 0.01);
                }
            } else {
                randomValue = random.nextDouble(startAmount, endAmount + 0.01);
            }

            randomValue = BigDecimal.valueOf(randomValue).setScale(2, RoundingMode.HALF_UP).doubleValue();

            if (randomValue >= startAmount && randomValue <= endAmount) {
                numericDenominations.add(randomValue);
            }
        }

        if (numericDenominations.isEmpty()) {
            numericDenominations.add(startAmount);
        }

        List<String> finalDenominations = new ArrayList<>();
        for (double val : numericDenominations) {
            finalDenominations.add(String.format(Locale.US, "%.2f", val));
        }

        return finalDenominations;
    }

    private double findNextProfitableDenomination(double startAmount, double maxAmount, Product prod, BigDecimal usdToZarRate, double step) {
        double fxRate = (prod.getFxRate() != null && prod.getFxRate() > 0) ? prod.getFxRate() : 1.0;
        BigDecimal localFxRate = BigDecimal.valueOf(fxRate);
        BigDecimal reloadlyDiscount = (prod.getCommission() != null) ? prod.getCommission() : BigDecimal.ZERO;

        double candidate = BigDecimal.valueOf(startAmount).setScale(2, RoundingMode.HALF_UP).doubleValue();

        while (candidate <= maxAmount) {
            BigDecimal price = BigDecimal.valueOf(candidate);
            ProfitValidator.EvaluationResult result = profitValidator.evaluateProduct(price, localFxRate, usdToZarRate, reloadlyDiscount);

            if (result.isAllowOnStorefront()) {
                return candidate;
            }

            candidate = BigDecimal.valueOf(candidate + step).setScale(2, RoundingMode.HALF_UP).doubleValue();
        }

        return -1.0;
    }

    private int parseId(String rawId) {
        if (rawId == null) return 0;
        String clean = NON_DIGIT_PATTERN.matcher(rawId).replaceAll("");
        return clean.isEmpty() ? 0 : Integer.parseInt(clean);
    }
}