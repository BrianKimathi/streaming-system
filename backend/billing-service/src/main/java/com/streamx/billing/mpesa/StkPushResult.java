package com.streamx.billing.mpesa;

public record StkPushResult(
        String merchantRequestId,
        String checkoutRequestId,
        String responseCode,
        String responseDescription,
        String customerMessage
) {
    public boolean accepted() {
        return "0".equals(responseCode) && checkoutRequestId != null && !checkoutRequestId.isBlank();
    }
}
