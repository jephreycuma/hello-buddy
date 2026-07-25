package za.co.digital.hellobuddy.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ProfitValidator {

	private final BigDecimal paystackPercentIncVat;
	private final BigDecimal paystackFixedIncVat;

	public ProfitValidator(@Value("${paystack.fee.percentage-inc-vat:0.03335}") BigDecimal paystackPercentIncVat,
			@Value("${paystack.fee.fixed-inc-vat:1.15}") BigDecimal paystackFixedIncVat) {
		this.paystackPercentIncVat = paystackPercentIncVat;
		this.paystackFixedIncVat = paystackFixedIncVat;
	}

	public static class EvaluationResult {
		private final boolean allowOnStorefront;
		private final BigDecimal priceInZar;
		private final BigDecimal paystackPayoutZar;
		private final BigDecimal reloadlyCostZar;
		private final BigDecimal profitOrLossZar;

		public EvaluationResult(boolean allowOnStorefront, BigDecimal priceInZar, BigDecimal paystackPayoutZar,
				BigDecimal reloadlyCostZar, BigDecimal profitOrLossZar) {
			this.allowOnStorefront = allowOnStorefront;
			this.priceInZar = priceInZar;
			this.paystackPayoutZar = paystackPayoutZar;
			this.reloadlyCostZar = reloadlyCostZar;
			this.profitOrLossZar = profitOrLossZar;
		}

		public boolean isAllowOnStorefront() {
			return allowOnStorefront;
		}

		public BigDecimal getPriceInZar() {
			return priceInZar;
		}

		public BigDecimal getPaystackPayoutZar() {
			return paystackPayoutZar;
		}

		public BigDecimal getReloadlyCostZar() {
			return reloadlyCostZar;
		}

		public BigDecimal getProfitOrLossZar() {
			return profitOrLossZar;
		}

		@Override
		public String toString() {
			return String.format("Price: R%s | Paystack Payout: R%s | Reloadly Cost: R%s | Net: R%s | Display: %s",
					priceInZar, paystackPayoutZar, reloadlyCostZar, profitOrLossZar,
					allowOnStorefront ? "YES" : "NO (FILTERED)");
		}
	}

	/**
	 * Evaluates product profitability in ZAR using configured Paystack fee rates.
	 */
	public EvaluationResult evaluateProduct(BigDecimal rawPrice, BigDecimal localFxRateToUsd, BigDecimal usdToZarRate,
			BigDecimal reloadlyDiscount) {

		if (rawPrice == null || rawPrice.compareTo(BigDecimal.ZERO) <= 0) {
			return new EvaluationResult(false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
		}

		BigDecimal safeLocalFx = (localFxRateToUsd != null && localFxRateToUsd.compareTo(BigDecimal.ZERO) > 0)
				? localFxRateToUsd
				: BigDecimal.ONE;

		BigDecimal safeUsdToZar = (usdToZarRate != null && usdToZarRate.compareTo(BigDecimal.ZERO) > 0) ? usdToZarRate
				: BigDecimal.ONE;

		// 1. Convert retail price to ZAR (retail price in local currency converted via operator FX to USD, then to ZAR)
		BigDecimal priceInZar;
		if (safeLocalFx.compareTo(BigDecimal.ONE) == 0 && safeUsdToZar.compareTo(BigDecimal.ONE) == 0) {
			priceInZar = rawPrice;
		} else {
			priceInZar = rawPrice.divide(safeLocalFx, 6, RoundingMode.HALF_UP).multiply(safeUsdToZar).setScale(2,
					RoundingMode.HALF_UP);
		}

		// 2. Calculate Paystack fees using injected properties
		BigDecimal paystackFeeZar = priceInZar.multiply(this.paystackPercentIncVat).add(this.paystackFixedIncVat)
				.setScale(2, RoundingMode.HALF_UP);

		BigDecimal paystackPayoutZar = priceInZar.subtract(paystackFeeZar).setScale(2, RoundingMode.HALF_UP);

		// 3. Calculate Reloadly cost in ZAR
		// Parse discount/commission (handles both 5.0 for 5% or 0.05)
		BigDecimal discountFraction = BigDecimal.ZERO;
		if (reloadlyDiscount != null && reloadlyDiscount.compareTo(BigDecimal.ZERO) > 0) {
			discountFraction = (reloadlyDiscount.compareTo(BigDecimal.ONE) > 0)
					? reloadlyDiscount.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)
					: reloadlyDiscount;
		}

		// Step A: Convert local price to USD using product operator FX rate (prod.getFxRate())
		BigDecimal priceInUsd = rawPrice.divide(safeLocalFx, 6, RoundingMode.HALF_UP);

		// Step B: Calculate Net USD cost after applying Reloadly discount
		BigDecimal netCostUsd = priceInUsd.multiply(BigDecimal.ONE.subtract(discountFraction));

		// Step C: Convert Net USD cost to ZAR using South Africa Reloadly account FX rate
		BigDecimal reloadlyCostZar = netCostUsd.multiply(safeUsdToZar).setScale(2, RoundingMode.HALF_UP);

		// 4. Margin decision
		BigDecimal netDiffZar = paystackPayoutZar.subtract(reloadlyCostZar).setScale(2, RoundingMode.HALF_UP);
		boolean shouldDisplayOnStorefront = netDiffZar.compareTo(BigDecimal.ZERO) > 0;

		return new EvaluationResult(shouldDisplayOnStorefront, priceInZar, paystackPayoutZar, reloadlyCostZar,
				netDiffZar);
	}
}