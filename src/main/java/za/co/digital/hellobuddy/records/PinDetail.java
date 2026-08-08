package za.co.digital.hellobuddy.records;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PinDetail(
    String serialNumber,
    String info1,
    String info2,
    String info3,
    String value,
    String code
) {}