package mx.edu.clickandtap.api.domain.pagination;

import java.util.List;

/** One page of results plus the totals a client needs to build pagination controls. */
public record PageResult<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
}
