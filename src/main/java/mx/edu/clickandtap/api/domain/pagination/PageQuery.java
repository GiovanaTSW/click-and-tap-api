package mx.edu.clickandtap.api.domain.pagination;

import mx.edu.clickandtap.api.domain.exception.BadRequestException;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Framework-free description of "which page, how big, sorted by what".
 * The persistence layer translates it to Spring Data's Pageable, so the domain never imports Spring Data.
 */
public record PageQuery(int page, int size, String sortBy, boolean descending) {
    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 0) throw new BadRequestException("page must be zero or greater");
        if (size < 1 || size > MAX_SIZE) throw new BadRequestException("size must be between 1 and " + MAX_SIZE);
    }

    /**
     * Builds a query from request parameters.
     *
     * @param sort          "field" or "field,asc" / "field,desc"; null or blank uses {@code defaultSort}
     * @param allowedFields whitelist of sortable fields, so clients cannot sort by arbitrary columns
     */
    public static PageQuery of(int page, int size, String sort, Set<String> allowedFields, String defaultSort) {
        String spec = (sort == null || sort.isBlank()) ? defaultSort : sort.trim();
        String[] parts = spec.split(",");
        if (parts.length > 2) throw new BadRequestException("sort must look like 'field' or 'field,asc|desc'");

        String field = parts[0].trim();
        if (!allowedFields.contains(field)) {
            throw new BadRequestException("Invalid sort field. Allowed: " + new TreeSet<>(allowedFields));
        }
        boolean descending = false;
        if (parts.length == 2) {
            String direction = parts[1].trim().toLowerCase(Locale.ROOT);
            if (direction.equals("desc")) {
                descending = true;
            } else if (!direction.equals("asc")) {
                throw new BadRequestException("Sort direction must be asc or desc");
            }
        }
        return new PageQuery(page, size, field, descending);
    }
}
