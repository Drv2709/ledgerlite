package com.ledgerlite.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * Serves the React build that Dockerfile.render copies into classpath:/static, from the same origin as the API.
 *
 * - /assets/**  : hashed build files, cached for a year
 * - existing files are served as they are
 * - any other path without a file extension (/login, /transactions, ...) returns index.html and React Router
 *   decides what to show, so a hard refresh on a client-side route works
 * - /api/** is never answered with HTML, and a missing file with an extension is a real 404
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    private static final Resource INDEX = new ClassPathResource("static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                // No resource cache: a caching resolver would remember every random URL a bot requests.
                .resourceChain(false)
                .addResolver(new SpaFallbackResolver());
    }

    private static final class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            if (resourcePath.equals("api") || resourcePath.startsWith("api/")) {
                return null;
            }
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            if (resourcePath.contains(".")) {
                return null;
            }
            return INDEX.exists() ? INDEX : null;
        }
    }
}
