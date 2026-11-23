package mx.edu.clickandtap.api.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.DigitalExperience;
import mx.edu.clickandtap.api.domain.service.DigitalExperienceService;
import mx.edu.clickandtap.api.web.dto.ApiDto.ExperienceRequest;
import mx.edu.clickandtap.api.web.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/experiences")
@RequiredArgsConstructor
public class ExperienceController {
    private final DigitalExperienceService experienceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DigitalExperience create(@AuthenticationPrincipal AuthenticatedUser user,
                                    @Valid @RequestBody ExperienceRequest r) {
        return experienceService.create(user.id(), r.toDomain());
    }

    @GetMapping
    public List<DigitalExperience> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return experienceService.getByUser(user.id());
    }

    @GetMapping("/{id}")
    public DigitalExperience get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return experienceService.getOwned(id, user.id(), user.isAdmin());
    }

    @PutMapping("/{id}")
    public DigitalExperience update(@PathVariable Long id,
                                    @AuthenticationPrincipal AuthenticatedUser user,
                                    @Valid @RequestBody ExperienceRequest r) {
        return experienceService.update(id, user.id(), user.isAdmin(), r.toDomain());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        experienceService.delete(id, user.id(), user.isAdmin());
    }
}