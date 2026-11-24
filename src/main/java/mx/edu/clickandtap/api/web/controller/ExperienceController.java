package mx.edu.clickandtap.api.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.service.DigitalExperienceService;
import mx.edu.clickandtap.api.web.dto.ApiDto.ExperienceRequest;
import mx.edu.clickandtap.api.web.dto.ApiDto.ExperienceResponse;
import mx.edu.clickandtap.api.web.dto.ApiDto.ExperienceUpdateRequest;
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
    public ExperienceResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody ExperienceRequest r) {
        return ExperienceResponse.from(experienceService.create(user.id(), r.toDomain()));
    }

    /** My experiences. Not paginated: a user is expected to have a handful of them. */
    @GetMapping
    public List<ExperienceResponse> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return experienceService.getByUser(user.id()).stream().map(ExperienceResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ExperienceResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return ExperienceResponse.from(experienceService.getOwned(id, user.id(), user.isAdmin()));
    }

    @PutMapping("/{id}")
    public ExperienceResponse update(@PathVariable Long id,
                                     @AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody ExperienceUpdateRequest r) {
        return ExperienceResponse.from(experienceService.update(id, user.id(), user.isAdmin(), r.toDomain()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        experienceService.delete(id, user.id(), user.isAdmin());
    }
}
