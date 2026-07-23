package za.co.digital.hellobuddy.cache;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

import za.co.digital.hellobuddy.dto.Product;
import za.co.digital.hellobuddy.dto.ProductItemDTO;
import za.co.digital.hellobuddy.service.ProfitValidator;
import za.co.digital.hellobuddy.util.SpringContextUtil;

public class HelloBuddyInnerMemory {

    private static final Logger log = LoggerFactory.getLogger(HelloBuddyInnerMemory.class);

    // Pre-compiled Patterns to avoid CPU overhead during looping
    private static final Pattern NON_DIGIT_PATTERN = Pattern.compile("\\D");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\d+[,.]\\d+");

    // Cache parameters
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final Map<String, Map<String, List<ProductItemDTO>>> catalogMaps = new ConcurrentHashMap<>();

    private static volatile HelloBuddyInnerMemory instance;

    private HelloBuddyInnerMemory(RestClient restClient, String countryIso, double platformMarkup) {
        loadReloadlyProducts(restClient, countryIso, platformMarkup);
    }

    public static HelloBuddyInnerMemory getInstance(RestClient restClient, String countryIso, double platformMarkup) {
        StringRedisTemplate redisTemplate = getRedisTemplate();
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

        if (instance == null || catalogMaps.get(countryIso) == null || isExpired) {
            synchronized (HelloBuddyInnerMemory.class) {
                if (instance == null || catalogMaps.get(countryIso) == null || isExpired) {
                    instance = new HelloBuddyInnerMemory(restClient, countryIso, platformMarkup);
                }
            }
        }
        return instance;
    }

    public Map<String, List<ProductItemDTO>> getReloadlyProducts(String countryIso) {
        return catalogMaps.get(countryIso);
    }

    private static StringRedisTemplate getRedisTemplate() {
        try {
            return SpringContextUtil.getBean(StringRedisTemplate.class);
        } catch (Exception ex) {
            log.error("Redis Template could not be fetched from Spring context: {}", ex.getMessage());
            return null;
        }
    }


    private void loadReloadlyProducts(RestClient restClient, String countryIso, double platformMarkup) {
        Map<String, List<ProductItemDTO>> catalogMap = new ConcurrentHashMap<>();
        List<ProductItemDTO> airtimeList = new ArrayList<>();
        List<ProductItemDTO> topupList = new ArrayList<>();
        List<ProductItemDTO> dataList = new ArrayList<>();
        List<ProductItemDTO> giftCardsList = new ArrayList<>();

        StringRedisTemplate redisTemplate = getRedisTemplate();
        ProfitValidator profitValidator = new ProfitValidator();

        // 1. Retrieve the base USD to ZAR exchange rate for foreign product conversions
        BigDecimal usdToZarRate = new BigDecimal("18.50"); // Safe baseline fallback rate
        if (redisTemplate != null) {
            try {
                String cachedUsdZar = redisTemplate.opsForValue().get("fx:USD_ZAR");
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

                    double purchasePrice = prod.getUsdPrice() + platformMarkup;

                    // Redis Writes & FX Tracking Optimization
                    if (redisTemplate != null && prod.getFxRate() != null && prod.getCurrencySymbol() != null) {
                        String redisKey = "fx:" + countryIso.toUpperCase() + "_" + prod.getDestinationCurrencyCode().toUpperCase();
                        redisTemplate.opsForValue().set(redisKey, String.valueOf(prod.getFxRate()), CACHE_TTL);
                        redisTemplate.opsForValue().set(countryIso, prod.getDestinationCurrencyCode().toUpperCase(), CACHE_TTL);

                        String commissionValue = (prod.getCommission() != null) ? prod.getCommission().toString() : "0.0";
                        redisTemplate.opsForValue().set(countryIso + ":" + prod.getId(), commissionValue, CACHE_TTL);
                    }

                    int sanitizedId = parseId(prod.getId());

                    if ("AIRTIME TOPUP".equalsIgnoreCase(type)) {
                        generateLocalDenominations(cleanedNetwork, prod, sanitizedId, topupList, platformMarkup, profitValidator, usdToZarRate);
                    } else if (purchasePrice > 0.26) {

                        // FX Rate from product's local currency to USD
                        BigDecimal localFxRate = (prod.getFxRate() != null && prod.getFxRate() > 0)
                                ? BigDecimal.valueOf(prod.getFxRate())
                                : BigDecimal.ONE;

                        BigDecimal reloadlyDiscount = (prod.getCommission() != null)
                                ? prod.getCommission()
                                : BigDecimal.ZERO;

                        // EVALUATE PROFIT IN ZAR FOR ALL CURRENCIES
                        ProfitValidator.EvaluationResult result = profitValidator.evaluateProduct(
                                price,
                                localFxRate,
                                usdToZarRate,
                                reloadlyDiscount
                        );

                        // ONLY ADD PRODUCT IF PAYSTACK PAYOUT > RELOADLY COST
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

        catalogMaps.put(countryIso, catalogMap);

        if (redisTemplate != null) {
            redisTemplate.opsForValue().set("loadTime:" + countryIso, String.valueOf(System.currentTimeMillis()), CACHE_TTL);
        }
    }

    private void generateLocalDenominations(
            String cleanedNetwork,
            Product prod,
            int parsedId,
            List<ProductItemDTO> topupList,
            double platformMarkup,
            ProfitValidator profitValidator,
            BigDecimal usdToZarRate) {

        double[] amounts = getMinAndMaxAmounts(prod.getDescription());
        double minAmount = amounts[0];
        double maxAmount = amounts[1];

        List<String> denominations = denominations(prod.getDescription(), prod, platformMarkup);
        double fxRate = (prod.getFxRate() != null && prod.getFxRate() > 0) ? prod.getFxRate() : 1.0;
        BigDecimal localFxRate = BigDecimal.valueOf(fxRate);

        BigDecimal reloadlyDiscount = (prod.getCommission() != null)
                ? prod.getCommission()
                : BigDecimal.ZERO;

        for (String denominationStr : denominations) {
            double denomination = Double.parseDouble(denominationStr);
            BigDecimal price = BigDecimal.valueOf(denomination);

            // EVALUATE PROFITABILITY IN ZAR
            ProfitValidator.EvaluationResult result = profitValidator.evaluateProduct(
                    price,
                    localFxRate,
                    usdToZarRate,
                    reloadlyDiscount
            );

            if (result.isAllowOnStorefront()) {
                String displayPrice = prod.getCurrencySymbol() + String.format(Locale.US, "%.2f", denomination);
                String detailedName = cleanedNetwork + " Fixed " + displayPrice;

                double purchasePrice = (denomination / fxRate) + platformMarkup;
                if (purchasePrice < 0.5) {
                    purchasePrice += 0.50;
                }

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
                dto.setMinLimit(minAmount);
                dto.setMaxLimit(maxAmount);
                topupList.add(dto);
            } else {
                log.info("SKIPPED UNPROFITABLE DENOMINATION [{}] R{}: {}", cleanedNetwork, denominationStr, result);
            }
        }
    }

    private String getNetworkName(String network) {
        if (network == null || network.isBlank()) {
            return "Unknown Operator";
        }

        String lowerNetwork = network.toLowerCase();
        for (String countryName : Countries.getCountries()) {
            int index = lowerNetwork.indexOf(countryName.toLowerCase());
            if (index != -1) {
                return network.substring(0, index).trim();
            }
        }
        return network.trim();
    }

    private double[] getMinAndMaxAmounts(String text) {
        if (text == null || text.isBlank()) {
            return new double[]{0.0, 0.0};
        }

        if (!text.contains("from") && !text.contains("to")) {
            text = text.replace(",", ".");
        }

        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        double firstAmount = 0.0;
        double secondAmount = 0.0;

        if (matcher.find()) {
            firstAmount = Double.parseDouble(matcher.group().replace(",", "."));
        }
        if (matcher.find()) {
            secondAmount = Double.parseDouble(matcher.group().replace(",", "."));
        }

        return new double[]{firstAmount, secondAmount};
    }

    private List<String> denominations(String text, Product prod, double platformMarkup) {
        double[] minAndMax = getMinAndMaxAmounts(text);
        double firstAmount = minAndMax[0];
        double secondAmount = minAndMax[1];

        double fxRate = (prod.getFxRate() != null && prod.getFxRate() > 0) ? prod.getFxRate() : 1.0;
        double minAmount = firstAmount / fxRate;
        double targetFirstAmount = firstAmount;

        if (minAmount + platformMarkup <= 0.5) {
            double requiredMinAmountLocal = 0.51 - platformMarkup;
            targetFirstAmount = requiredMinAmountLocal * fxRate;

            if (secondAmount > 0 && targetFirstAmount > secondAmount) {
                targetFirstAmount = secondAmount;
            }
        }

        double endAmount = (secondAmount > 0) ? secondAmount : targetFirstAmount;
        return generate5RandomDenominations(targetFirstAmount, endAmount);
    }

    private List<String> generate5RandomDenominations(double startAmount, double endAmount) {
        Set<Double> numericDenominations = new TreeSet<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        startAmount = BigDecimal.valueOf(startAmount).setScale(2, RoundingMode.HALF_UP).doubleValue();
        endAmount = BigDecimal.valueOf(endAmount).setScale(2, RoundingMode.HALF_UP).doubleValue();

        if (Double.compare(startAmount, endAmount) != 0) {
            numericDenominations.add(endAmount);
        }

        boolean useMultiplesOf10 = (endAmount >= 10) || (endAmount % 10 == 0);
        int maxAttempts = 50;
        int attempts = 0;

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

        List<String> finalDenominations = new ArrayList<>();
        for (double val : numericDenominations) {
            finalDenominations.add(String.format(Locale.US, "%.2f", val));
        }

        return finalDenominations;
    }

    private int parseId(String rawId) {
        if (rawId == null) return 0;
        String clean = NON_DIGIT_PATTERN.matcher(rawId).replaceAll("");
        return clean.isEmpty() ? 0 : Integer.parseInt(clean);
    }
}