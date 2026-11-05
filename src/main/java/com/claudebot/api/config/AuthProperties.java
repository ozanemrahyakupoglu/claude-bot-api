package com.claudebot.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Optional HTTP Basic credentials for the API and the UI. Auth is off while either is empty.
 */
@Component
@ConfigurationProperties(prefix = "api.auth")
public class AuthProperties {

    private String username;
    private String password;

    public boolean isEnabled() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
