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

		BigDecimal priceInZar;
		if (safeLocalFx.compareTo(BigDecimal.ONE) == 0 && safeUsdToZar.compareTo(BigDecimal.ONE) == 0) {
			priceInZar = rawPrice;
		} else {
			priceInZar = rawPrice.divide(safeLocalFx, 6, RoundingMode.HALF_UP).multiply(safeUsdToZar).setScale(2,
					RoundingMode.HALF_UP);
		}

		BigDecimal paystackFeeZar = priceInZar.multiply(this.paystackPercentIncVat).add(this.paystackFixedIncVat)
				.setScale(2, RoundingMode.HALF_UP);
		BigDecimal paystackPayoutZar = priceInZar.subtract(paystackFeeZar).setScale(2, RoundingMode.HALF_UP);

		BigDecimal discountFraction = BigDecimal.ZERO;
		if (reloadlyDiscount != null && reloadlyDiscount.compareTo(BigDecimal.ZERO) > 0) {
			discountFraction = (reloadlyDiscount.compareTo(BigDecimal.ONE) > 0)
					? reloadlyDiscount.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)
					: reloadlyDiscount;
		}

		BigDecimal priceInUsd = rawPrice.divide(safeLocalFx, 6, RoundingMode.HALF_UP);
		BigDecimal netCostUsd = priceInUsd.multiply(BigDecimal.ONE.subtract(discountFraction));
		BigDecimal reloadlyCostZar = netCostUsd.multiply(safeUsdToZar).setScale(2, RoundingMode.HALF_UP);

		BigDecimal netDiffZar = paystackPayoutZar.subtract(reloadlyCostZar).setScale(2, RoundingMode.HALF_UP);
		boolean shouldDisplayOnStorefront = netDiffZar.compareTo(BigDecimal.ZERO) > 0;

		return new EvaluationResult(shouldDisplayOnStorefront, priceInZar, paystackPayoutZar, reloadlyCostZar,
				netDiffZar);
	}

	public BigDecimal calculatePayStackCharge(BigDecimal storefrontLocalPrice, BigDecimal localToUsdFxRate,
			BigDecimal usdToZarFxRate) {
		if (storefrontLocalPrice == null || storefrontLocalPrice.compareTo(BigDecimal.ZERO) <= 0) {
			return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		}

		if (localToUsdFxRate == null || localToUsdFxRate.compareTo(BigDecimal.ZERO) <= 0) {
			localToUsdFxRate = BigDecimal.ONE;
		}

		if (usdToZarFxRate == null || usdToZarFxRate.compareTo(BigDecimal.ZERO) <= 0) {
			usdToZarFxRate = BigDecimal.ONE;
		}

		if (localToUsdFxRate.compareTo(BigDecimal.ONE) == 0 && usdToZarFxRate.compareTo(BigDecimal.ONE) == 0) {
			return storefrontLocalPrice.setScale(2, RoundingMode.HALF_UP);
		}

		BigDecimal usdPrice = storefrontLocalPrice.divide(localToUsdFxRate, 6, RoundingMode.HALF_UP);
		BigDecimal finalAmountZar = usdPrice.multiply(usdToZarFxRate);

		return finalAmountZar.setScale(2, RoundingMode.HALF_UP);
	}

	public BigDecimal convertCountryPriceToUsd(BigDecimal localPrice, BigDecimal localToUsdFxRate) {
		if (localPrice == null || localPrice.compareTo(BigDecimal.ZERO) <= 0) {
			return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
		}

		if (localToUsdFxRate == null || localToUsdFxRate.compareTo(BigDecimal.ZERO) <= 0) {
			localToUsdFxRate = BigDecimal.ONE;
		}

		// Kept 4 decimal places to prevent micro-rounding truncation on small FX values
		return localPrice.divide(localToUsdFxRate, 4, RoundingMode.HALF_UP);
	}
}