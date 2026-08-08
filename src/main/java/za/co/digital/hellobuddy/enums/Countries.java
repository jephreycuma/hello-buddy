package za.co.digital.hellobuddy.enums;

import java.util.Arrays;
import java.util.Optional;

public enum Countries {

    // --- INDIA ---
    INDIA("IN"),

    // --- AFRICAN COUNTRIES (54 Sovereign States + Dependencies) ---
    ALGERIA("DZ"),
    ANGOLA("AO"),
    BENIN("BJ"),
    BOTSWANA("BW"),
    BURKINA_FASO("BF"),
    BURUNDI("BI"),
    CABO_VERDE("CV"),
    CAMEROON("CM"),
    CENTRAL_AFRICAN_REPUBLIC("CF"),
    CHAD("TD"),
    COMOROS("KM"),
    CONGO_BRAZZAVILLE("CG"),
    CONGO_KINSHASA("CD"),
    COTE_D_IVOIRE("CI"),
    DJIBOUTI("DJ"),
    EGYPT("EG"),
    EQUATORIAL_GUINEA("GQ"),
    ERITREA("ER"),
    ESWATINI("SZ"),
    ETHIOPIA("ET"),
    GABON("GA"),
    GAMBIA("GM"),
    GHANA("GH"),
    GUINEA("GN"),
    GUINEA_BISSAU("GW"),
    KENYA("KE"),
    LESOTHO("LS"),
    LIBERIA("LR"),
    LIBYA("LY"),
    MADAGASCAR("MG"),
    MALAWI("MW"),
    MALI("ML"),
    MAURITANIA("MR"),
    MAURITIUS("MU"),
    MOROCCO("MA"),
    MOZAMBIQUE("MZ"),
    NAMIBIA("NA"),
    NIGER("NE"),
    NIGERIA("NG"),
    RWANDA("RW"),
    SAO_TOME_AND_PRINCIPE("ST"),
    SENEGAL("SN"),
    SEYCHELLES("SC"),
    SIERRA_LEONE("SL"),
    SOMALIA("SO"),
    SOUTH_AFRICA("ZA"),
    SOUTH_SUDAN("SS"),
    SUDAN("SD"),
    TANZANIA("TZ"),
    TOGO("TG"),
    TUNISIA("TN"),
    UGANDA("UG"),
    ZAMBIA("ZM"),
    ZIMBABWE("ZW");

    private final String isoCode;

    Countries(String isoCode) {
        this.isoCode = isoCode;
    }

    public String getIsoCode() {
        return isoCode;
    }

    /**
     * Looks up an enum constant by its ISO code and returns the country name with
     * underscores replaced by spaces.
     *
     * @param countryIso The 2-letter ISO country code (case-insensitive)
     * @return Formatted country name, or null if not found.
     */
    public static String getCountryName(String countryIso) {
        if (countryIso == null || countryIso.trim().isEmpty()) {
            return null;
        }

        Optional<Countries> matchedCountry = Arrays.stream(Countries.values())
                .filter(country -> country.getIsoCode().equalsIgnoreCase(countryIso.trim()))
                .findFirst();

        return matchedCountry
                .map(country -> country.name().replace('_', ' '))
                .orElse(null);
    }
}
