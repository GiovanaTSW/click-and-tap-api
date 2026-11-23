package mx.edu.clickandtap.api.web.controller;

import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.service.DigitalExperienceService;
import mx.edu.clickandtap.api.web.dto.ApiDto.PublicExperienceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/experiences")
@RequiredArgsConstructor
public class PublicExperienceController {
    private final DigitalExperienceService experienceService;

    @GetMapping("/{slug}")
    public PublicExperienceResponse getBySlug(@PathVariable String slug) {
        return PublicExperienceResponse.from(experienceService.getPublicBySlug(slug));
    }
}