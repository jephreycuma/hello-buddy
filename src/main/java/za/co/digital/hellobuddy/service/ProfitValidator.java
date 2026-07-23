package za.co.digital.hellobuddy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ProfitValidator {

    // Paystack SA Standard Fee: 2.7% + R2.00 (excl. 15% VAT)
    // Inclusive of VAT = 3.105% + R2.30
    private static final BigDecimal PAYSTACK_PERCENT_INC_VAT = new BigDecimal("0.03105");
    private static final BigDecimal PAYSTACK_FIXED_INC_VAT = new BigDecimal("2.30");

    public static class EvaluationResult {
        private final boolean allowOnStorefront;
        private final BigDecimal priceInZar;
        private final BigDecimal paystackPayoutZar;
        private final BigDecimal reloadlyCostZar;
        private final BigDecimal profitOrLossZar;

        public EvaluationResult(
                boolean allowOnStorefront,
                BigDecimal priceInZar,
                BigDecimal paystackPayoutZar,
                BigDecimal reloadlyCostZar,
                BigDecimal profitOrLossZar) {
            this.allowOnStorefront = allowOnStorefront;
            this.priceInZar = priceInZar;
            this.paystackPayoutZar = paystackPayoutZar;
            this.reloadlyCostZar = reloadlyCostZar;
            this.profitOrLossZar = profitOrLossZar;
        }

        public boolean isAllowOnStorefront() { return allowOnStorefront; }
        public BigDecimal getPriceInZar() { return priceInZar; }
        public BigDecimal getPaystackPayoutZar() { return paystackPayoutZar; }
        public BigDecimal getReloadlyCostZar() { return reloadlyCostZar; }
        public BigDecimal getProfitOrLossZar() { return profitOrLossZar; }

        @Override
        public String toString() {
            return String.format(
                "Price: R%s | Paystack Payout: R%s | Reloadly Cost: R%s | Net: R%s | Display: %s",
                priceInZar, paystackPayoutZar, reloadlyCostZar, profitOrLossZar, allowOnStorefront ? "YES" : "NO (FILTERED)"
            );
        }
    }

    /**
     * Evaluates product profitability in ZAR.
     *
     * @param rawPrice           Price from Reloadly (e.g. 299 NGN or 5.00 ZAR)
     * @param localFxRateToUsd   Local currency FX rate to USD (from prod.getFxRate()). Pass 1.0 for ZAR.
     * @param usdToZarRate       Current exchange rate from USD to ZAR. Pass 1.0 for ZAR.
     * @param reloadlyDiscount   Discount percentage from Reloadly (e.g., 5.0 for 5%)
     */
    public EvaluationResult evaluateProduct(
            BigDecimal rawPrice,
            BigDecimal localFxRateToUsd,
            BigDecimal usdToZarRate,
            BigDecimal reloadlyDiscount) {

        if (rawPrice == null || rawPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return new EvaluationResult(false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        // Safe FX fallbacks
        BigDecimal safeLocalFx = (localFxRateToUsd != null && localFxRateToUsd.compareTo(BigDecimal.ZERO) > 0)
                ? localFxRateToUsd : BigDecimal.ONE;

        BigDecimal safeUsdToZar = (usdToZarRate != null && usdToZarRate.compareTo(BigDecimal.ZERO) > 0)
                ? usdToZarRate : BigDecimal.ONE;

        // 1. CONVERT RETAIL PRICE TO ZAR FIRST
        // Price in ZAR = (rawPrice / localFxRateToUsd) * usdToZarRate
        BigDecimal priceInZar;
        if (safeLocalFx.compareTo(BigDecimal.ONE) == 0 && safeUsdToZar.compareTo(BigDecimal.ONE) == 0) {
            priceInZar = rawPrice; // Local South Africa
        } else {
            priceInZar = rawPrice
                    .divide(safeLocalFx, 6, RoundingMode.HALF_UP)
                    .multiply(safeUsdToZar)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // 2. CALCULATE PAYSTACK DEDUCTIONS IN ZAR
        BigDecimal paystackFeeZar = priceInZar
                .multiply(PAYSTACK_PERCENT_INC_VAT)
                .add(PAYSTACK_FIXED_INC_VAT)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal paystackPayoutZar = priceInZar.subtract(paystackFeeZar).setScale(2, RoundingMode.HALF_UP);

        // 3. CALCULATE RELOADLY COST IN ZAR
        BigDecimal discountFraction = BigDecimal.ZERO;
        if (reloadlyDiscount != null && reloadlyDiscount.compareTo(BigDecimal.ZERO) > 0) {
            discountFraction = (reloadlyDiscount.compareTo(BigDecimal.ONE) > 0)
                    ? reloadlyDiscount.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)
                    : reloadlyDiscount;
        }

        BigDecimal reloadlyCostZar = priceInZar
                .multiply(BigDecimal.ONE.subtract(discountFraction))
                .setScale(2, RoundingMode.HALF_UP);

        // 4. CORE DECISION CHECK: Paystack Payout > Reloadly Wholesale Cost
        BigDecimal netDiffZar = paystackPayoutZar.subtract(reloadlyCostZar).setScale(2, RoundingMode.HALF_UP);
        boolean shouldDisplayOnStorefront = netDiffZar.compareTo(BigDecimal.ZERO) > 0;

        return new EvaluationResult(
                shouldDisplayOnStorefront,
                priceInZar,
                paystackPayoutZar,
                reloadlyCostZar,
                netDiffZar
        );
    }
}