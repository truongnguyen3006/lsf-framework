package com.myorg.lsf.quota.key;

public final class QuotaKeys {
    private QuotaKeys() {}

    public static String of(String tenant, String type, String id) {
        return tenant + ":" + type + ":" + id;
    }
}