package com.streamx.billing.dto;

/**
 * Admin view of a transaction: unmasked phone number plus the Daraja request identifiers.
 */
public class AdminPaymentTransactionResponse extends PaymentTransactionResponse {
    private String merchantRequestId;
    private String checkoutRequestId;

    public AdminPaymentTransactionResponse() {
    }

    public String getMerchantRequestId() {
        return merchantRequestId;
    }

    public void setMerchantRequestId(String merchantRequestId) {
        this.merchantRequestId = merchantRequestId;
    }

    public String getCheckoutRequestId() {
        return checkoutRequestId;
    }

    public void setCheckoutRequestId(String checkoutRequestId) {
        this.checkoutRequestId = checkoutRequestId;
    }
}
