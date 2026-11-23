package mx.edu.clickandtap.api.domain.service;

import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.repository.DigitalExperienceRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SlugService {
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DigitalExperienceRepository experienceRepository;

    /** Convierte un texto en slug: "Café Luna!" -> "cafe-luna" */
    public String slugify(String text) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (normalized.length() > 80) normalized = normalized.substring(0, 80).replaceAll("-+$", "");
        return normalized.isEmpty() ? "experience" : normalized;
    }

    /** Devuelve un slug que no existe en la BD. Si el base está ocupado, agrega un sufijo aleatorio. */
    public String generateUnique(String text) {
        String base = slugify(text);
        if (!experienceRepository.existsBySlug(base)) return base;
        String candidate;
        do {
            candidate = base + "-" + randomSuffix(4);
        } while (experienceRepository.existsBySlug(candidate));
        return candidate;
    }

    private String randomSuffix(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}