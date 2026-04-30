package com.cardsense.api.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class MerchantRegistry {

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String registryPath;
    private Map<String, String> aliases = Map.of();

    public MerchantRegistry(
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            @Value("${cardsense.merchant-registry.path:classpath:merchant-registry.json}") String registryPath
    ) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.registryPath = registryPath;
    }

    @PostConstruct
    void load() {
        Resource resource = resourceLoader.getResource(registryPath);
        if (!resource.exists()) {
            aliases = Map.of();
            return;
        }

        try (InputStream input = resource.getInputStream()) {
            RegistryEntry[] entries = objectMapper.readValue(input, RegistryEntry[].class);
            Map<String, String> loaded = new HashMap<>();
            for (RegistryEntry entry : entries) {
                addAlias(loaded, entry.code(), entry.code());
                addAlias(loaded, entry.label(), entry.code());
                if (entry.aliases() != null) {
                    for (String alias : entry.aliases()) {
                        addAlias(loaded, alias, entry.code());
                    }
                }
            }
            aliases = Map.copyOf(loaded);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load merchant registry from " + registryPath, exception);
        }
    }

    public Optional<String> canonicalCode(String merchantName) {
        return Optional.ofNullable(aliases.get(normalize(merchantName)));
    }

    private void addAlias(Map<String, String> target, String alias, String code) {
        String normalizedAlias = normalize(alias);
        String normalizedCode = normalize(code);
        if (!normalizedAlias.isBlank() && !normalizedCode.isBlank()) {
            target.put(normalizedAlias, normalizedCode);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RegistryEntry(String code, String label, List<String> aliases) {
    }
}
