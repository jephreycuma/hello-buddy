package za.co.digital.hellobuddy.records;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionPageResponse(
    List<Transaction> content,
    PageableInfo pageable,
    long totalElements,
    int totalPages,
    boolean last,
    boolean first,
    SortInfo sort,
    int numberOfElements,
    int size,
    int number,
    boolean empty
) {}