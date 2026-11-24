package mx.edu.clickandtap.api.domain.service;

import mx.edu.clickandtap.api.domain.repository.DigitalExperienceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlugServiceTest {
    @Mock DigitalExperienceRepository experienceRepository;
    @InjectMocks SlugService service;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Café Luna!            | cafe-luna",
            "Árbol Ñandú           | arbol-nandu",
            "  Mi   Perfil  Pro    | mi-perfil-pro",
            "---hola---            | hola",
            "Tacos_El_Güero 2024   | tacos-el-guero-2024"
    })
    void slugifyProducesLowercaseAsciiWithHyphens(String text, String expected) {
        assertThat(service.slugify(text)).isEqualTo(expected);
    }

    @Test
    void slugifyFallsBackToADefaultWhenNothingUsableRemains() {
        assertThat(service.slugify("¡¡¡ ???")).isEqualTo("experience");
        assertThat(service.slugify("")).isEqualTo("experience");
        assertThat(service.slugify(null)).isEqualTo("experience");
    }

    @Test
    void slugifyCapsTheLengthAndNeverEndsWithAHyphen() {
        String slug = service.slugify("a".repeat(120));
        assertThat(slug).hasSize(80);

        String withSeparatorAtTheCut = service.slugify("a".repeat(79) + " b");
        assertThat(withSeparatorAtTheCut).doesNotEndWith("-");
    }

    @Test
    void generateUniqueReturnsTheBaseWhenItIsFree() {
        when(experienceRepository.existsBySlug("cafe-luna")).thenReturn(false);

        assertThat(service.generateUnique("Café Luna")).isEqualTo("cafe-luna");
    }

    @Test
    void generateUniqueAddsARandomSuffixWhenTheBaseIsTaken() {
        // first call (the base) is taken, the next candidate is free
        when(experienceRepository.existsBySlug(anyString())).thenReturn(true, false);

        assertThat(service.generateUnique("Café Luna")).matches("cafe-luna-[a-z0-9]{4}");
    }

    @Test
    void generateUniqueKeepsTryingUntilItFindsAFreeSlug() {
        when(experienceRepository.existsBySlug(anyString())).thenReturn(true, true, true, false);

        assertThat(service.generateUnique("Café Luna")).matches("cafe-luna-[a-z0-9]{4}");
    }
}
