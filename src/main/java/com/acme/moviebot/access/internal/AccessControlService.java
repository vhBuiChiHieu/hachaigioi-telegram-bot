package com.acme.moviebot.access.internal;

import com.acme.moviebot.access.AccessControl;
import org.springframework.stereotype.Service;

@Service
public class AccessControlService implements AccessControl {

    private final AccessProperties properties;

    public AccessControlService(AccessProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isAdmin(long telegramUserId) {
        return properties.adminIds().contains(telegramUserId);
    }
}
