package mx.edu.clickandtap.api.domain.service;

import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.DigitalExperience;
import mx.edu.clickandtap.api.domain.ExperienceLink;
import mx.edu.clickandtap.api.domain.exception.BadRequestException;
import mx.edu.clickandtap.api.domain.exception.ForbiddenException;
import mx.edu.clickandtap.api.domain.exception.NotFoundException;
import mx.edu.clickandtap.api.domain.repository.DigitalExperienceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DigitalExperienceService {
    private final DigitalExperienceRepository experienceRepository;
    private final SlugService slugService;

    @Transactional
    public DigitalExperience create(Long userId, DigitalExperience data) {
        if (data.getTitle() == null || data.getTitle().isBlank()) throw new BadRequestException("Title is required");
        if (data.getType() == null) throw new BadRequestException("Type is required");

        String slugSource = (data.getSlug() != null && !data.getSlug().isBlank()) ? data.getSlug() : data.getTitle();
        data.setId(null);
        data.setUserId(userId);
        data.setSlug(slugService.generateUnique(slugSource));
        data.setActive(true);
        data.setLinks(cleanLinks(data.getLinks()));
        return experienceRepository.save(data);
    }

    /** El slug NO cambia al editar: la URL ya puede estar impresa en un QR o NFC. */
    @Transactional
    public DigitalExperience update(Long id, Long userId, boolean isAdmin, DigitalExperience changes) {
        DigitalExperience existing = getOwned(id, userId, isAdmin);
        if (changes.getTitle() == null || changes.getTitle().isBlank()) throw new BadRequestException("Title is required");
        existing.setTitle(changes.getTitle());
        existing.setDescription(changes.getDescription());
        existing.setActive(changes.isActive());
        existing.setLinks(cleanLinks(changes.getLinks()));
        return experienceRepository.save(existing);
    }

    @Transactional
    public void delete(Long id, Long userId, boolean isAdmin) {
        getOwned(id, userId, isAdmin);
        experienceRepository.delete(id);
    }

    public List<DigitalExperience> getByUser(Long userId) { return experienceRepository.getByUserId(userId); }

    public DigitalExperience getOwned(Long id, Long userId, boolean isAdmin) {
        DigitalExperience exp = experienceRepository.getById(id)
                .orElseThrow(() -> new NotFoundException("Experience not found"));
        if (!isAdmin && !exp.getUserId().equals(userId)) {
            throw new ForbiddenException("You cannot access this experience");
        }
        return exp;
    }

    /** Página pública (la que abre el NFC/QR). Solo experiencias activas. */
    public DigitalExperience getPublicBySlug(String slug) {
        DigitalExperience exp = experienceRepository.getBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Experience not found"));
        if (!exp.isActive()) throw new NotFoundException("Experience not found");
        List<ExperienceLink> activeLinks = new ArrayList<>();
        if (exp.getLinks() != null) {
            exp.getLinks().stream().filter(ExperienceLink::isActive).forEach(activeLinks::add);
        }
        exp.setLinks(activeLinks);
        return exp;
    }

    private List<ExperienceLink> cleanLinks(List<ExperienceLink> links) {
        List<ExperienceLink> result = new ArrayList<>();
        if (links == null) return result;
        int position = 0;
        for (ExperienceLink link : links) {
            if (link.getLabel() == null || link.getLabel().isBlank()) throw new BadRequestException("Link label is required");
            validateUrl(link.getUrl());
            link.setId(null);
            link.setPosition(position++);
            result.add(link);
        }
        return result;
    }

    /** Evita esquemas peligrosos como javascript: en las páginas públicas. */
    private void validateUrl(String url) {
        if (url == null || url.isBlank()) throw new BadRequestException("Link url is required");
        String lower = url.trim().toLowerCase(Locale.ROOT);
        boolean allowed = lower.startsWith("https://") || lower.startsWith("http://")
                || lower.startsWith("mailto:") || lower.startsWith("tel:");
        if (!allowed) throw new BadRequestException("Link url must start with http://, https://, mailto: or tel:");
    }
}