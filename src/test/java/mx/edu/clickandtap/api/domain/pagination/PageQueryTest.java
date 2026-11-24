package mx.edu.clickandtap.api.domain.pagination;

import mx.edu.clickandtap.api.domain.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {
    private static final Set<String> ALLOWED = Set.of("id", "name", "price");

    @Test
    void usesDefaultSortWhenNoneIsGiven() {
        PageQuery query = PageQuery.of(0, 20, null, ALLOWED, "name,asc");

        assertThat(query.sortBy()).isEqualTo("name");
        assertThat(query.descending()).isFalse();
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(20);
    }

    @Test
    void blankSortFallsBackToDefault() {
        PageQuery query = PageQuery.of(0, 20, "   ", ALLOWED, "price,desc");

        assertThat(query.sortBy()).isEqualTo("price");
        assertThat(query.descending()).isTrue();
    }

    @Test
    void parsesDirectionCaseInsensitively() {
        assertThat(PageQuery.of(0, 10, "price,DESC", ALLOWED, "id").descending()).isTrue();
        assertThat(PageQuery.of(0, 10, "price,asc", ALLOWED, "id").descending()).isFalse();
        assertThat(PageQuery.of(0, 10, "price", ALLOWED, "id").descending()).isFalse();
    }

    @Test
    void rejectsFieldsOutsideTheWhitelist() {
        assertThatThrownBy(() -> PageQuery.of(0, 10, "passwordHash", ALLOWED, "id"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid sort field");
    }

    @Test
    void rejectsInjectionAttemptsInTheSortField() {
        assertThatThrownBy(() -> PageQuery.of(0, 10, "name; drop table users", ALLOWED, "id"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsUnknownDirection() {
        assertThatThrownBy(() -> PageQuery.of(0, 10, "price,up", ALLOWED, "id"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("asc or desc");
    }

    @Test
    void rejectsMoreThanTwoSortParts() {
        assertThatThrownBy(() -> PageQuery.of(0, 10, "price,asc,extra", ALLOWED, "id"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> new PageQuery(-1, 10, "id", false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("page");
    }

    @Test
    void rejectsSizesOutsideTheAllowedRange() {
        assertThatThrownBy(() -> new PageQuery(0, 0, "id", false)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> new PageQuery(0, PageQuery.MAX_SIZE + 1, "id", false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void acceptsTheLimits() {
        assertThat(new PageQuery(0, 1, "id", false).size()).isEqualTo(1);
        assertThat(new PageQuery(0, PageQuery.MAX_SIZE, "id", false).size()).isEqualTo(PageQuery.MAX_SIZE);
    }
}
