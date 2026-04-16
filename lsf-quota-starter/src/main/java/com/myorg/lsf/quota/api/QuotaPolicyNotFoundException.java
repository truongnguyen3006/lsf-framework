package com.myorg.lsf.quota.api;

/**
 * Thrown when a quota key is used without any configured policy.
 * This is more expressive than a plain IllegalArgumentException and
 * helps application code map the failure to 4xx responses if desired.
 */
public class QuotaPolicyNotFoundException extends IllegalArgumentException {
    public QuotaPolicyNotFoundException(String quotaKey) {
        super("No quota policy configured for key='" + quotaKey + "'");
    }
}
