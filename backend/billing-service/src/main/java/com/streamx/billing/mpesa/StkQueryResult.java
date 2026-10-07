package com.streamx.billing.mpesa;

/**
 * Outcome of an STK Push query. {@code pending} means M-Pesa has no final result yet.
 */
public record StkQueryResult(boolean pending, String resultCode, String resultDesc) {

    public static StkQueryResult stillProcessing(String description) {
        return new StkQueryResult(true, null, description);
    }

    public static StkQueryResult completed(String resultCode, String resultDesc) {
        return new StkQueryResult(false, resultCode, resultDesc);
    }
}
