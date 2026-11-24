package mx.edu.clickandtap.api.domain.service;

import mx.edu.clickandtap.api.domain.DigitalExperience;
import mx.edu.clickandtap.api.domain.ExperienceLink;
import mx.edu.clickandtap.api.domain.ExperienceType;
import mx.edu.clickandtap.api.domain.exception.BadRequestException;
import mx.edu.clickandtap.api.domain.exception.ForbiddenException;
import mx.edu.clickandtap.api.domain.exception.NotFoundException;
import mx.edu.clickandtap.api.domain.repository.DigitalExperienceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DigitalExperienceServiceTest {
    @Mock DigitalExperienceRepository experienceRepository;
    @Mock SlugService slugService;
    @InjectMocks DigitalExperienceService service;

    // ---------- create ----------

    @Test
    void createGeneratesSlugFromTheTitleAndAssignsTheOwner() {
        when(slugService.generateUnique("Mi Perfil")).thenReturn("mi-perfil");
        when(experienceRepository.save(any(DigitalExperience.class))).thenAnswer(inv -> inv.getArgument(0));
        DigitalExperience data = experience("Mi Perfil", link("Web", "https://example.com"));
        data.setId(55L);

        DigitalExperience saved = service.create(7L, data);

        assertThat(saved.getId()).isNull();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getSlug()).isEqualTo("mi-perfil");
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void createUsesTheRequestedSlugAsTheBase() {
        when(slugService.generateUnique("mi-menu")).thenReturn("mi-menu");
        when(experienceRepository.save(any(DigitalExperience.class))).thenAnswer(inv -> inv.getArgument(0));
        DigitalExperience data = experience("Menú del día");
        data.setSlug("mi-menu");

        assertThat(service.create(7L, data).getSlug()).isEqualTo("mi-menu");
    }

    @Test
    void createNumbersLinksInOrderAndTrimsTheirUrls() {
        when(slugService.generateUnique("Mi Perfil")).thenReturn("mi-perfil");
        when(experienceRepository.save(any(DigitalExperience.class))).thenAnswer(inv -> inv.getArgument(0));
        DigitalExperience data = experience("Mi Perfil",
                link("Web", "  https://example.com  "), link("Mail", "mailto:a@b.com"), link("Tel", "tel:+5255"));

        List<ExperienceLink> links = service.create(7L, data).getLinks();

        assertThat(links).extracting(ExperienceLink::getPosition).containsExactly(0, 1, 2);
        assertThat(links.get(0).getUrl()).isEqualTo("https://example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "JAVASCRIPT:alert(1)", "data:text/html,<script>", "ftp://x.com",
            "//evil.com", "file:///etc/passwd", "vbscript:x", "   "})
    void createRejectsDangerousOrUnsupportedLinkSchemes(String url) {
        when(slugService.generateUnique("Mi Perfil")).thenReturn("mi-perfil");
        DigitalExperience data = experience("Mi Perfil", link("Click", url));

        assertThatThrownBy(() -> service.create(7L, data)).isInstanceOf(BadRequestException.class);
        verify(experienceRepository, never()).save(any());
    }

    @Test
    void createRequiresTitleTypeAndLinkLabels() {
        DigitalExperience noTitle = experience(" ");
        DigitalExperience noType = experience("Mi Perfil");
        noType.setType(null);
        when(slugService.generateUnique("Mi Perfil")).thenReturn("mi-perfil");
        DigitalExperience noLabel = experience("Mi Perfil", link("", "https://example.com"));

        assertThatThrownBy(() -> service.create(7L, noTitle)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(7L, noType)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(7L, noLabel)).isInstanceOf(BadRequestException.class);
    }

    // ---------- update ----------

    @Test
    void updateNeverChangesTheSlugBecauseItMayBePrintedOnATag() {
        DigitalExperience existing = stored(1L, 7L, "slug-impreso");
        when(experienceRepository.getById(1L)).thenReturn(Optional.of(existing));
        when(experienceRepository.save(any(DigitalExperience.class))).thenAnswer(inv -> inv.getArgument(0));
        DigitalExperience changes = experience("Titulo nuevo", link("Web", "https://example.com"));
        changes.setSlug("otro-slug");
        changes.setActive(false);

        DigitalExperience saved = service.update(1L, 7L, false, changes);

        assertThat(saved.getSlug()).isEqualTo("slug-impreso");
        assertThat(saved.getTitle()).isEqualTo("Titulo nuevo");
        assertThat(saved.isActive()).isFalse();
    }

    @Test
    void updateReplacesLinksAndRenumbersThem() {
        DigitalExperience existing = stored(1L, 7L, "s");
        existing.setLinks(new ArrayList<>(List.of(link("Viejo", "https://old.com"))));
        when(experienceRepository.getById(1L)).thenReturn(Optional.of(existing));
        when(experienceRepository.save(any(DigitalExperience.class))).thenAnswer(inv -> inv.getArgument(0));

        DigitalExperience saved = service.update(1L, 7L, false,
                experience("T", link("A", "https://a.com"), link("B", "https://b.com")));

        assertThat(saved.getLinks()).extracting(ExperienceLink::getLabel).containsExactly("A", "B");
        assertThat(saved.getLinks()).extracting(ExperienceLink::getPosition).containsExactly(0, 1);
    }

    @Test
    void anotherUserCannotUpdateOrDeleteSomeoneElsesExperience() {
        when(experienceRepository.getById(1L)).thenReturn(Optional.of(stored(1L, 7L, "s")));

        assertThatThrownBy(() -> service.update(1L, 8L, false, experience("T")))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.delete(1L, 8L, false)).isInstanceOf(ForbiddenException.class);
        verify(experienceRepository, never()).save(any());
        verify(experienceRepository, never()).delete(any());
    }

    @Test
    void adminCanDeleteAnyExperience() {
        when(experienceRepository.getById(1L)).thenReturn(Optional.of(stored(1L, 7L, "s")));

        service.delete(1L, 99L, true);

        verify(experienceRepository).delete(1L);
    }

    @Test
    void updateOfUnknownExperienceIsNotFound() {
        when(experienceRepository.getById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(404L, 7L, false, experience("T")))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- public page ----------

    @Test
    void publicPageHidesInactiveExperiences() {
        DigitalExperience inactive = stored(1L, 7L, "oculta");
        inactive.setActive(false);
        when(experienceRepository.getBySlug("oculta")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.getPublicBySlug("oculta")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void publicPageOnlyShowsActiveLinks() {
        DigitalExperience exp = stored(1L, 7L, "visible");
        ExperienceLink hidden = link("Oculto", "https://hidden.com");
        hidden.setActive(false);
        exp.setLinks(new ArrayList<>(List.of(link("Visible", "https://visible.com"), hidden)));
        when(experienceRepository.getBySlug("visible")).thenReturn(Optional.of(exp));

        List<ExperienceLink> links = service.getPublicBySlug("visible").getLinks();

        assertThat(links).extracting(ExperienceLink::getLabel).containsExactly("Visible");
    }

    @Test
    void publicPageOfUnknownSlugIsNotFound() {
        when(experienceRepository.getBySlug("nada")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublicBySlug("nada")).isInstanceOf(NotFoundException.class);
    }

    // ---------- helpers ----------

    private DigitalExperience experience(String title, ExperienceLink... links) {
        DigitalExperience e = new DigitalExperience();
        e.setTitle(title);
        e.setType(ExperienceType.PROFILE);
        e.setActive(true);
        e.setLinks(new ArrayList<>(List.of(links)));
        return e;
    }

    private DigitalExperience stored(long id, long userId, String slug) {
        DigitalExperience e = experience("Original");
        e.setId(id);
        e.setUserId(userId);
        e.setSlug(slug);
        return e;
    }

    private ExperienceLink link(String label, String url) {
        ExperienceLink l = new ExperienceLink();
        l.setLabel(label);
        l.setUrl(url);
        l.setActive(true);
        return l;
    }
}
