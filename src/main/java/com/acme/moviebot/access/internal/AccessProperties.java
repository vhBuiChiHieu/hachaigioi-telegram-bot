package com.acme.moviebot.access.internal;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.access")
public record AccessProperties(Set<Long> adminIds) {

    public AccessProperties {
        adminIds = adminIds == null ? Set.of() : Set.copyOf(adminIds);
    }
}
