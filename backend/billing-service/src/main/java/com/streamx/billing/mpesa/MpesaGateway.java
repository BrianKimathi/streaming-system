package com.streamx.billing.mpesa;

/**
 * Safaricom Daraja STK Push operations. Implemented by {@link MpesaClient}.
 */
public interface MpesaGateway {

    /**
     * Sends an STK Push prompt to the customer's phone.
     *
     * @throws MpesaException when Daraja rejects the request or cannot be reached
     */
    StkPushResult initiateStkPush(String phoneNumber, long amount, String accountReference, String description);

    /**
     * Queries the final state of an STK Push request.
     *
     * @throws MpesaException when Daraja returns an error other than "still processing" or cannot be reached
     */
    StkQueryResult queryStkPush(String checkoutRequestId);

    /**
     * Requests a fresh OAuth token with the effective credentials. Never initiates a payment.
     *
     * @throws MpesaException when the credentials are missing or rejected, or Daraja cannot be reached
     */
    void verifyCredentials();

    /**
     * Drops the cached OAuth token so the next call authenticates with the current credentials.
     */
    void resetAuthentication();
}
