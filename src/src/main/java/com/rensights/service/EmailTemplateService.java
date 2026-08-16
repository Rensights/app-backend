package com.rensights.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the HTML email templates from {@code resources/templates/email} and fills in their
 * {@code {{VARIABLE}}} placeholders.
 *
 * <p>Templates are read once and cached: they ship inside the jar, so re-reading them per send
 * buys nothing. A missing template fails loudly at send time rather than mailing a broken body.
 */
@Service
public class EmailTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(EmailTemplateService.class);

    private static final String TEMPLATE_PATH = "templates/email/%s.html";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * The named template with its placeholders replaced.
     *
     * @param templateName file name without the extension, e.g. {@code "verification-code"}
     * @param variables    placeholder name (without braces) to value, e.g. {@code CODE -> 478041}
     */
    public String render(String templateName, Map<String, String> variables) {
        String template = cache.computeIfAbsent(templateName, this::load);

        String rendered = template;
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            rendered = rendered.replace("{{" + variable.getKey() + "}}",
                variable.getValue() == null ? "" : variable.getValue());
        }
        return rendered;
    }

    private String load(String templateName) {
        String path = String.format(TEMPLATE_PATH, templateName);
        try (InputStream stream = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(stream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Email template not found on the classpath: {}", path, e);
            throw new IllegalStateException("Missing email template: " + path, e);
        }
    }
}
