package mx.edu.clickandtap.api.persistence;

import mx.edu.clickandtap.api.domain.pagination.PageQuery;
import mx.edu.clickandtap.api.domain.pagination.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.function.Function;

/** Translates between the domain's page types and Spring Data's. Only the persistence layer uses it. */
final class PageSupport {
    private PageSupport() {}

    static Pageable toPageable(PageQuery query) {
        Sort.Direction direction = query.descending() ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, query.sortBy());
        // Tie-breaker: without a unique last column, rows with equal values can jump between pages.
        if (!"id".equals(query.sortBy())) sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
        return PageRequest.of(query.page(), query.size(), sort);
    }

    static <E, D> PageResult<D> toResult(Page<E> page, Function<E, D> mapper) {
        return new PageResult<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
