package za.co.digital.hellobuddy.records;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SortInfo(
    boolean sorted,
    boolean unsorted,
    boolean empty
) {}