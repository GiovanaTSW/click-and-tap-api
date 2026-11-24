package mx.edu.clickandtap.api.domain.service;

import mx.edu.clickandtap.api.domain.Product;
import mx.edu.clickandtap.api.domain.ProductType;
import mx.edu.clickandtap.api.domain.exception.BadRequestException;
import mx.edu.clickandtap.api.domain.exception.NotFoundException;
import mx.edu.clickandtap.api.domain.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock ProductRepository productRepository;
    @InjectMocks ProductService service;

    // ---------- create ----------

    @Test
    void createForcesActiveAndClearsTheId() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        Product input = valid();
        input.setId(99L);
        input.setActive(false);

        Product saved = service.create(input);

        assertThat(saved.getId()).isNull();
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void createRequiresNonNegativeStock() {
        Product noStock = valid();
        noStock.setStock(null);
        Product negative = valid();
        negative.setStock(-1);

        assertThatThrownBy(() -> service.create(noStock)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(negative)).isInstanceOf(BadRequestException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void createRejectsNegativePriceAndMissingFields() {
        Product badPrice = valid();
        badPrice.setPrice(new BigDecimal("-0.01"));
        Product noName = valid();
        noName.setName("  ");
        Product noType = valid();
        noType.setType(null);

        assertThatThrownBy(() -> service.create(badPrice)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(noName)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(noType)).isInstanceOf(BadRequestException.class);
    }

    // ---------- image url ----------

    @Test
    void imageUrlWithDangerousSchemeIsRejected() {
        Product p = valid();
        p.setImageUrl("javascript:alert(1)");

        assertThatThrownBy(() -> service.create(p))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("http");
    }

    @Test
    void imageUrlIsTrimmedAndBlankBecomesNull() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        Product withUrl = valid();
        withUrl.setImageUrl("  https://cdn.example.com/a.png  ");
        Product blank = valid();
        blank.setImageUrl("   ");

        assertThat(service.create(withUrl).getImageUrl()).isEqualTo("https://cdn.example.com/a.png");
        assertThat(service.create(blank).getImageUrl()).isNull();
    }

    // ---------- update ----------

    @Test
    void updateChangesProductDataButNeverTheStock() {
        Product existing = valid();
        existing.setId(1L);
        existing.setStock(10);
        when(productRepository.getById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product changes = valid();
        changes.setName("Nuevo nombre");
        changes.setPrice(new BigDecimal("55.00"));
        changes.setStock(9999); // must be ignored
        changes.setActive(false);

        Product saved = service.update(1L, changes);

        assertThat(saved.getName()).isEqualTo("Nuevo nombre");
        assertThat(saved.getPrice()).isEqualByComparingTo("55.00");
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getStock()).isEqualTo(10);
    }

    @Test
    void updateOfUnknownProductIsNotFound() {
        when(productRepository.getById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(404L, valid())).isInstanceOf(NotFoundException.class);
    }

    // ---------- soft delete ----------

    @Test
    void deactivateMarksTheProductInactiveInsteadOfDeletingIt() {
        Product existing = valid();
        existing.setId(1L);
        existing.setActive(true);
        when(productRepository.getById(1L)).thenReturn(Optional.of(existing));

        service.deactivate(1L);

        assertThat(existing.isActive()).isFalse();
        verify(productRepository).save(existing);
    }

    @Test
    void deactivateIsIdempotent() {
        Product existing = valid();
        existing.setId(1L);
        existing.setActive(false);
        when(productRepository.getById(1L)).thenReturn(Optional.of(existing));

        service.deactivate(1L);

        verify(productRepository, never()).save(any());
    }

    @Test
    void inactiveProductLooksMissingToThePublic() {
        Product inactive = valid();
        inactive.setActive(false);
        when(productRepository.getById(1L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.getPublicById(1L)).isInstanceOf(NotFoundException.class);
        assertThat(service.getById(1L)).isSameAs(inactive); // admins can still reach it
    }

    // ---------- stock ----------

    @Test
    void adjustStockRejectsZeroDelta() {
        when(productRepository.getById(1L)).thenReturn(Optional.of(valid()));

        assertThatThrownBy(() -> service.adjustStock(1L, 0)).isInstanceOf(BadRequestException.class);
        verify(productRepository, never()).adjustStock(1L, 0);
    }

    @Test
    void adjustStockRejectsResultsBelowZero() {
        when(productRepository.getById(1L)).thenReturn(Optional.of(valid()));
        when(productRepository.adjustStock(1L, -100)).thenReturn(false);

        assertThatThrownBy(() -> service.adjustStock(1L, -100))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Stock cannot go below zero");
    }

    @Test
    void adjustStockReturnsTheUpdatedProduct() {
        Product reloaded = valid();
        reloaded.setStock(15);
        when(productRepository.getById(1L)).thenReturn(Optional.of(reloaded));
        when(productRepository.adjustStock(1L, 5)).thenReturn(true);

        assertThat(service.adjustStock(1L, 5).getStock()).isEqualTo(15);
    }

    private Product valid() {
        Product p = new Product();
        p.setName("Placa NFC");
        p.setType(ProductType.PLATE);
        p.setPrice(new BigDecimal("249.00"));
        p.setStock(10);
        p.setActive(true);
        p.setCustomizable(true);
        return p;
    }
}
