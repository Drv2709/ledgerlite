package com.ledgerlite.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React index page for "/" directly (status 200, HTML), instead of relying on Spring Boot's
 * welcome-page forward. Other client-side routes (/login, /transactions, ...) are handled by SpaWebConfig.
 * Without a built frontend (plain "mvn spring-boot:run" in development) this answers 404.
 */
@Controller
public class SpaIndexController {

    private static final Resource INDEX = new ClassPathResource("static/index.html");

    @GetMapping("/")
    public ResponseEntity<Resource> index() {
        if (!INDEX.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noCache())
                .body(INDEX);
    }
}