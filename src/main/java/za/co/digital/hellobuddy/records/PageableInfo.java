package za.co.digital.hellobuddy.records;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PageableInfo(
    SortInfo sort,
    int pageNumber,
    int pageSize,
    long offset,
    boolean paged,
    boolean unpaged
) {}