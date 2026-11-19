package com.kiri.server.admin.common;

public final class Reasons {

    private Reasons() {
    }

    public static String require(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new AdminException(AdminErrorCode.REASON_REQUIRED);
        }
        return reason.strip();
    }
}
