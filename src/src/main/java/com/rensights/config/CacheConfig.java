package com.rensights.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TTL-only Caffeine caching for read-heavy, admin-owned content.
 *
 * <p>app-backend and admin-backend share one Postgres database, and admin-backend is the ONLY
 * writer of the content cached here. app-backend therefore cannot perform event-based eviction,
 * so every cache relies on {@code expireAfterWrite} (time-based expiry) plus a bounded
 * {@code maximumSize}. Each cache gets its OWN TTL + size, so instead of a single global spec we
 * register a dedicated {@link Caffeine} instance per named cache.
 *
 * <p>Any cache name requested at runtime that is not pre-registered below still works: it falls
 * back to the conservative default builder ({@link #setCaffeine} on the manager). Prefer explicit
 * registration for anything intentional.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // Safe fallback for any cache name used at runtime but not explicitly registered below.
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(100));

        // Localized UI translations, keyed by language + namespace. Short TTL so
        // edits made in the admin translation editor propagate to the public site
        // within seconds (app-backend can't be evicted by admin-backend, the writer).
        manager.registerCustomCache("translations", Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(30))
                .maximumSize(200)
                .build());

        // Report sections resolved per (languageCode, tier) — NOT per user.
        manager.registerCustomCache("reportSections", Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(60)
                .build());

        // Single list of enabled languages.
        manager.registerCustomCache("languagesEnabled", Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(15))
                .maximumSize(1)
                .build());

        // Language lookup by code.
        manager.registerCustomCache("languageByCode", Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(15))
                .maximumSize(50)
                .build());

        // Landing page section content, keyed by section + language.
        manager.registerCustomCache("landingSection", Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(100)
                .build());

        // Full landing page (all sections) per language.
        manager.registerCustomCache("landingAll", Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(20)
                .build());

        // Public articles list (single entry).
        manager.registerCustomCache("articlesList", Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1)
                .build());

        // Public article by slug.
        manager.registerCustomCache("articleBySlug", Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(300)
                .build());

        // Normalized weekly-deals list from the upstream API (single entry).
        manager.registerCustomCache("dealsAll", Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(1)
                .build());

        // Weekly-deal detail by id.
        manager.registerCustomCache("dealDetail", Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(500)
                .build());

        // Trust-critical feature kill switches — short TTL so a disable propagates fast.
        manager.registerCustomCache("killSwitches", Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(10)
                .build());

        return manager;
    }
}
