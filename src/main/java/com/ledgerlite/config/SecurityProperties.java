package com.ledgerlite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the app.security.* block of application.yml. */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(JwtProperties jwt, CookieProperties cookie) {

    public record JwtProperties(String secret, long expirationMinutes) {
        @Override
        public String toString() {
            return "JwtProperties[secret=***, expirationMinutes=" + expirationMinutes + "]";
        }
    }

    public record CookieProperties(String name, boolean secure, String sameSite) {}
}
