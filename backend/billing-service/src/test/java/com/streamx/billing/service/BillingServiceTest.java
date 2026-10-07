package com.streamx.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.billing.client.ActivatedSubscription;
import com.streamx.billing.client.NotificationClient;
import com.streamx.billing.client.PlanDetails;
import com.streamx.billing.client.SubscriptionClient;
import com.streamx.billing.config.MpesaProperties;
import com.streamx.billing.domain.PaymentStatus;
import com.streamx.billing.domain.PaymentTransaction;
import com.streamx.billing.dto.AdminPaymentTransactionResponse;
import com.streamx.billing.dto.CheckoutRequest;
import com.streamx.billing.dto.PaymentTransactionResponse;
import com.streamx.billing.exception.BadGatewayException;
import com.streamx.billing.exception.ServiceUnavailableException;
import com.streamx.billing.mpesa.MpesaConfigProvider;
import com.streamx.billing.mpesa.MpesaException;
import com.streamx.billing.mpesa.MpesaGateway;
import com.streamx.billing.mpesa.StkPushResult;
import com.streamx.billing.mpesa.StkQueryResult;
import com.streamx.billing.repository.MpesaSettingsRepository;
import com.streamx.billing.repository.PaymentTransactionRepository;
import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class BillingServiceTest {

    private static final String TOKEN = "test-callback-token";

    @Autowired
    private BillingService billingService;

    @Autowired
    private BillingAdminService billingAdminService;

    @Autowired
    private PaymentTransactionRepository repository;

    @Autowired
    private MpesaProperties mpesaProperties;

    @Autowired
    private MpesaSettingsRepository settingsRepository;

    @Autowired
    private MpesaConfigProvider mpesaConfigProvider;

    @MockitoBean
    private MpesaGateway mpesaGateway;

    @MockitoBean
    private SubscriptionClient subscriptionClient;

    @MockitoBean
    private NotificationClient notificationClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String originalConsumerKey;

    private UUID accountId;
    private UUID planId;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        settingsRepository.deleteAll();
        mpesaConfigProvider.invalidate();
        originalConsumerKey = mpesaProperties.getConsumerKey();
        accountId = UUID.randomUUID();
        planId = UUID.randomUUID();
    }

    @AfterEach
    void restoreProperties() {
        mpesaProperties.setConsumerKey(originalConsumerKey);
        mpesaConfigProvider.invalidate();
    }

    private void stubPlan(String price, String currency, boolean active) {
        when(subscriptionClient.getPlan(planId))
                .thenReturn(new PlanDetails(planId, "Premium", new BigDecimal(price), currency, active));
    }

    private void stubStkAccepted(String checkoutRequestId) {
        when(mpesaGateway.initiateStkPush(anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new StkPushResult("29115-34620561-1", checkoutRequestId, "0",
                        "Success. Request accepted for processing", "Success. Request accepted for processing"));
    }

    private PaymentTransaction pendingTransaction(String checkoutRequestId, long secondsAgo) {
        PaymentTransaction txn = new PaymentTransaction();
        txn.setAccountId(accountId);
        txn.setPlanId(planId);
        txn.setPlanName("Premium");
        txn.setAmount(new BigDecimal("1000"));
        txn.setCurrency("KES");
        txn.setStatus(PaymentStatus.PENDING);
        txn.setPaymentMethod("MPESA");
        txn.setPhoneNumber("254712345678");
        txn.setMerchantRequestId("29115-34620561-1");
        txn.setCheckoutRequestId(checkoutRequestId);
        txn.setCreatedAt(LocalDateTime.now().minusSeconds(secondsAgo));
        return repository.save(txn);
    }

    private JsonNode callback(String checkoutRequestId, int resultCode, String resultDesc, String receipt) throws Exception {
        String metadata = receipt == null ? "" : """
                ,"CallbackMetadata":{"Item":[
                  {"Name":"Amount","Value":1000},
                  {"Name":"MpesaReceiptNumber","Value":"%s"},
                  {"Name":"TransactionDate","Value":20261007190102},
                  {"Name":"PhoneNumber","Value":254712345678}
                ]}""".formatted(receipt);
        String json = """
                {"Body":{"stkCallback":{
                  "MerchantRequestID":"29115-34620561-1",
                  "CheckoutRequestID":"%s",
                  "ResultCode":%d,
                  "ResultDesc":"%s"%s
                }}}""".formatted(checkoutRequestId, resultCode, resultDesc, metadata);
        return objectMapper.readTree(json);
    }

    private ActivatedSubscription activated() {
        return new ActivatedSubscription(UUID.randomUUID(), "Premium", "2026-11-07T19:00:00");
    }

    // --- checkout ---

    @Test
    void checkoutHappyPathUsesPlanPriceRoundedUp() {
        stubPlan("999.40", "KES", true);
        stubStkAccepted("ws_CO_happy");

        PaymentTransactionResponse response = billingService.checkout(accountId.toString(),
                new CheckoutRequest(planId.toString(), "0712 345 678"));

        verify(mpesaGateway).initiateStkPush("254712345678", 1000L, "StreamX", "Premium plan");
        assertEquals(PaymentStatus.PENDING, response.getStatus());
        assertEquals(0, new BigDecimal("1000").compareTo(response.getAmount()));
        assertEquals("KES", response.getCurrency());
        assertEquals("MPESA", response.getPaymentMethod());
        assertEquals("2547****5678", response.getPhoneNumber());
        assertEquals(planId.toString(), response.getPlanId());
        assertEquals("Premium", response.getPlanName());

        PaymentTransaction stored = repository.findById(UUID.fromString(response.getId())).orElseThrow();
        assertEquals("254712345678", stored.getPhoneNumber());
        assertEquals("ws_CO_happy", stored.getCheckoutRequestId());
        assertEquals("29115-34620561-1", stored.getMerchantRequestId());
    }

    @Test
    void checkoutWithoutMpesaConfigurationIs503() {
        mpesaProperties.setConsumerKey("");
        mpesaConfigProvider.invalidate();

        ServiceUnavailableException ex = assertThrows(ServiceUnavailableException.class, () -> billingService.checkout(
                accountId.toString(), new CheckoutRequest(planId.toString(), "0712345678")));

        assertEquals("M-Pesa payments are not configured yet", ex.getMessage());
        verify(mpesaGateway, never()).initiateStkPush(anyString(), anyLong(), anyString(), anyString());
        assertEquals(0, repository.count());
    }

    @Test
    void checkoutRejectsInvalidPhone() {
        assertThrows(BadRequestException.class, () -> billingService.checkout(
                accountId.toString(), new CheckoutRequest(planId.toString(), "0812345678")));
        verify(subscriptionClient, never()).getPlan(any());
    }

    @Test
    void checkoutRejectsFreeInactiveAndNonKesPlans() {
        CheckoutRequest request = new CheckoutRequest(planId.toString(), "0712345678");

        stubPlan("0", "KES", true);
        assertThrows(BadRequestException.class, () -> billingService.checkout(accountId.toString(), request));

        stubPlan("999", "KES", false);
        assertThrows(BadRequestException.class, () -> billingService.checkout(accountId.toString(), request));

        stubPlan("10", "USD", true);
        assertThrows(BadRequestException.class, () -> billingService.checkout(accountId.toString(), request));

        verify(mpesaGateway, never()).initiateStkPush(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void checkoutRejectsWhilePaymentInProgress() {
        pendingTransaction("ws_CO_existing", 30);
        stubPlan("999", "KES", true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> billingService.checkout(
                accountId.toString(), new CheckoutRequest(planId.toString(), "0712345678")));
        assertEquals("A payment request is already in progress. Check your phone.", ex.getMessage());
    }

    @Test
    void darajaRejectionMarksFailedAndReturns502() {
        stubPlan("999", "KES", true);
        when(mpesaGateway.initiateStkPush(anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new MpesaException("Bad Request - Invalid PhoneNumber"));

        BadGatewayException ex = assertThrows(BadGatewayException.class, () -> billingService.checkout(
                accountId.toString(), new CheckoutRequest(planId.toString(), "0712345678")));

        assertEquals("Bad Request - Invalid PhoneNumber", ex.getMessage());
        PaymentTransaction stored = repository.findAll().getFirst();
        assertEquals(PaymentStatus.FAILED, stored.getStatus());
        assertEquals("Bad Request - Invalid PhoneNumber", stored.getErrorMessage());
    }

    @Test
    void nonZeroResponseCodeMarksFailedAndReturns502() {
        stubPlan("999", "KES", true);
        when(mpesaGateway.initiateStkPush(anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new StkPushResult(null, null, "1", "Unable to lock subscriber", null));

        assertThrows(BadGatewayException.class, () -> billingService.checkout(
                accountId.toString(), new CheckoutRequest(planId.toString(), "0712345678")));
        assertEquals(PaymentStatus.FAILED, repository.findAll().getFirst().getStatus());
    }

    // --- callback ---

    @Test
    void successfulCallbackCompletesActivatesAndNotifies() throws Exception {
        PaymentTransaction txn = pendingTransaction("ws_CO_success", 5);
        ActivatedSubscription subscription = activated();
        when(subscriptionClient.activate(accountId, planId, txn.getId())).thenReturn(subscription);

        billingService.handleCallback(TOKEN, callback("ws_CO_success", 0,
                "The service request is processed successfully.", "QJR7XYZ123"));

        PaymentTransaction stored = repository.findById(txn.getId()).orElseThrow();
        assertEquals(PaymentStatus.COMPLETED, stored.getStatus());
        assertEquals("QJR7XYZ123", stored.getExternalTransactionId());
        assertEquals(subscription.id(), stored.getSubscriptionId());
        verify(subscriptionClient).activate(accountId, planId, txn.getId());

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationClient).sendInApp(eq(accountId), eq("PAYMENT_SUCCESS"), eq("Payment received"), body.capture());
        assertTrue(body.getValue().contains("QJR7XYZ123"));
        assertTrue(body.getValue().contains("7 Nov 2026"));
    }

    @Test
    void cancelledCallbackMarksCancelled() throws Exception {
        PaymentTransaction txn = pendingTransaction("ws_CO_cancel", 5);

        billingService.handleCallback(TOKEN, callback("ws_CO_cancel", 1032, "Request cancelled by user", null));

        PaymentTransaction stored = repository.findById(txn.getId()).orElseThrow();
        assertEquals(PaymentStatus.CANCELLED, stored.getStatus());
        assertEquals("You cancelled the M-Pesa prompt", stored.getErrorMessage());
        verify(subscriptionClient, never()).activate(any(), any(), any());
        verify(notificationClient).sendInApp(eq(accountId), eq("PAYMENT_FAILED"), eq("Payment cancelled"), anyString());
    }

    @Test
    void failedCallbackStoresResultDesc() throws Exception {
        PaymentTransaction txn = pendingTransaction("ws_CO_fail", 5);

        billingService.handleCallback(TOKEN, callback("ws_CO_fail", 1,
                "The balance is insufficient for the transaction.", null));

        PaymentTransaction stored = repository.findById(txn.getId()).orElseThrow();
        assertEquals(PaymentStatus.FAILED, stored.getStatus());
        assertEquals("The balance is insufficient for the transaction.", stored.getErrorMessage());
        verify(notificationClient).sendInApp(eq(accountId), eq("PAYMENT_FAILED"), eq("Payment failed"), anyString());
        verify(subscriptionClient, never()).activate(any(), any(), any());
    }

    @Test
    void duplicateCallbackIsIgnored() throws Exception {
        PaymentTransaction txn = pendingTransaction("ws_CO_dup", 5);
        when(subscriptionClient.activate(accountId, planId, txn.getId())).thenReturn(activated());
        JsonNode payload = callback("ws_CO_dup", 0, "ok", "QJR7DUP001");

        billingService.handleCallback(TOKEN, payload);
        billingService.handleCallback(TOKEN, payload);
        billingService.handleCallback(TOKEN, callback("ws_CO_dup", 1, "late failure", null));

        assertEquals(PaymentStatus.COMPLETED, repository.findById(txn.getId()).orElseThrow().getStatus());
        verify(subscriptionClient, times(1)).activate(any(), any(), any());
        verify(notificationClient, times(1)).sendInApp(any(), anyString(), anyString(), anyString());
    }

    @Test
    void wrongCallbackTokenIs404AndChangesNothing() throws Exception {
        PaymentTransaction txn = pendingTransaction("ws_CO_token", 5);

        assertThrows(ResourceNotFoundException.class,
                () -> billingService.handleCallback("wrong-token", callback("ws_CO_token", 0, "ok", "QJR7TOKEN1")));
        assertThrows(ResourceNotFoundException.class,
                () -> billingService.handleCallback(null, callback("ws_CO_token", 0, "ok", "QJR7TOKEN1")));

        assertEquals(PaymentStatus.PENDING, repository.findById(txn.getId()).orElseThrow().getStatus());
        verify(subscriptionClient, never()).activate(any(), any(), any());
    }

    @Test
    void malformedOrUnknownCallbacksAreAcceptedWithoutChanges() throws Exception {
        PaymentTransaction txn = pendingTransaction("ws_CO_known", 5);

        assertDoesNotThrow(() -> billingService.handleCallback(TOKEN, objectMapper.readTree("{\"foo\":1}")));
        assertDoesNotThrow(() -> billingService.handleCallback(TOKEN, null));
        assertDoesNotThrow(() -> billingService.handleCallback(TOKEN, callback("ws_CO_unknown", 0, "ok", "R1")));

        assertEquals(PaymentStatus.PENDING, repository.findById(txn.getId()).orElseThrow().getStatus());
    }

    @Test
    void notificationFailureDoesNotBreakPaymentProcessing() throws Exception {
        PaymentTransaction txn = pendingTransaction("ws_CO_notify", 5);
        when(subscriptionClient.activate(accountId, planId, txn.getId())).thenReturn(activated());
        doThrow(new ResourceAccessException("notification-service down"))
                .when(notificationClient).sendInApp(any(), anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> billingService.handleCallback(TOKEN, callback("ws_CO_notify", 0, "ok", "QJR7NOTIF1")));

        PaymentTransaction stored = repository.findById(txn.getId()).orElseThrow();
        assertEquals(PaymentStatus.COMPLETED, stored.getStatus());
        assertNotNull(stored.getSubscriptionId());
    }

    @Test
    void failedActivationIsRetriedLater() throws Exception {
        PaymentTransaction txn = pendingTransaction("ws_CO_retry", 5);
        ActivatedSubscription subscription = activated();
        when(subscriptionClient.activate(accountId, planId, txn.getId()))
                .thenThrow(new ResourceAccessException("subscription-service down"))
                .thenReturn(subscription);

        billingService.handleCallback(TOKEN, callback("ws_CO_retry", 0, "ok", "QJR7RETRY1"));
        PaymentTransaction afterCallback = repository.findById(txn.getId()).orElseThrow();
        assertEquals(PaymentStatus.COMPLETED, afterCallback.getStatus());
        assertNull(afterCallback.getSubscriptionId());

        billingService.retryPendingActivations();
        assertEquals(subscription.id(), repository.findById(txn.getId()).orElseThrow().getSubscriptionId());
    }

    // --- status polling & reconciliation ---

    @Test
    void youngPendingTransactionIsNotQueried() {
        PaymentTransaction txn = pendingTransaction("ws_CO_young", 5);

        PaymentTransactionResponse response = billingService.getTransaction(accountId.toString(), txn.getId().toString());

        assertEquals(PaymentStatus.PENDING, response.getStatus());
        verify(mpesaGateway, never()).queryStkPush(anyString());
    }

    @Test
    void reconciliationCompletesViaStkQuery() {
        PaymentTransaction txn = pendingTransaction("ws_CO_query", 30);
        when(mpesaGateway.queryStkPush("ws_CO_query"))
                .thenReturn(StkQueryResult.completed("0", "The service request is processed successfully."));
        when(subscriptionClient.activate(accountId, planId, txn.getId())).thenReturn(activated());

        PaymentTransactionResponse response = billingService.getTransaction(accountId.toString(), txn.getId().toString());

        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertNotNull(response.getSubscriptionId());
        verify(notificationClient).sendInApp(eq(accountId), eq("PAYMENT_SUCCESS"), anyString(), anyString());
    }

    @Test
    void reconciliationStaysPendingWhileProcessing() {
        PaymentTransaction txn = pendingTransaction("ws_CO_processing", 60);
        when(mpesaGateway.queryStkPush("ws_CO_processing"))
                .thenReturn(StkQueryResult.stillProcessing("The transaction is being processed"));

        assertEquals(PaymentStatus.PENDING,
                billingService.getTransaction(accountId.toString(), txn.getId().toString()).getStatus());
    }

    @Test
    void reconciliationTimesOutAfterThreeMinutes() {
        PaymentTransaction txn = pendingTransaction("ws_CO_timeout", 200);
        when(mpesaGateway.queryStkPush("ws_CO_timeout"))
                .thenReturn(StkQueryResult.stillProcessing("The transaction is being processed"));

        PaymentTransactionResponse response = billingService.getTransaction(accountId.toString(), txn.getId().toString());

        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertEquals("M-Pesa did not confirm the payment in time", response.getErrorMessage());
        verify(notificationClient).sendInApp(eq(accountId), eq("PAYMENT_FAILED"), anyString(), anyString());
    }

    @Test
    void reconciliationTimesOutWhenQueryErrors() {
        PaymentTransaction txn = pendingTransaction("ws_CO_qerr", 200);
        when(mpesaGateway.queryStkPush("ws_CO_qerr")).thenThrow(new MpesaException("Could not reach M-Pesa"));

        billingService.reconcileStaleTransactions();

        PaymentTransaction stored = repository.findById(txn.getId()).orElseThrow();
        assertEquals(PaymentStatus.FAILED, stored.getStatus());
        assertEquals(BillingService.TIMEOUT_MESSAGE, stored.getErrorMessage());
    }

    @Test
    void reconciliationAppliesCancelledResult() {
        PaymentTransaction txn = pendingTransaction("ws_CO_qcancel", 30);
        when(mpesaGateway.queryStkPush("ws_CO_qcancel")).thenReturn(StkQueryResult.completed("1032", "Request cancelled by user"));

        PaymentTransactionResponse response = billingService.getTransaction(accountId.toString(), txn.getId().toString());

        assertEquals(PaymentStatus.CANCELLED, response.getStatus());
        assertEquals("You cancelled the M-Pesa prompt", response.getErrorMessage());
    }

    @Test
    void lateSuccessCallbackAfterTimeoutIsHonoured() throws Exception {
        PaymentTransaction txn = pendingTransaction("ws_CO_late", 200);
        when(mpesaGateway.queryStkPush("ws_CO_late")).thenReturn(StkQueryResult.stillProcessing("processing"));
        when(subscriptionClient.activate(accountId, planId, txn.getId())).thenReturn(activated());
        billingService.getTransaction(accountId.toString(), txn.getId().toString());
        assertEquals(PaymentStatus.FAILED, repository.findById(txn.getId()).orElseThrow().getStatus());

        billingService.handleCallback(TOKEN, callback("ws_CO_late", 0, "ok", "QJR7LATE01"));

        PaymentTransaction stored = repository.findById(txn.getId()).orElseThrow();
        assertEquals(PaymentStatus.COMPLETED, stored.getStatus());
        assertNull(stored.getErrorMessage());
        verify(subscriptionClient).activate(accountId, planId, txn.getId());
    }

    @Test
    void transactionOfAnotherAccountIs404() {
        PaymentTransaction txn = pendingTransaction("ws_CO_other", 5);

        assertThrows(ResourceNotFoundException.class,
                () -> billingService.getTransaction(UUID.randomUUID().toString(), txn.getId().toString()));
        assertThrows(ResourceNotFoundException.class,
                () -> billingService.getTransaction(accountId.toString(), "not-a-uuid"));
    }

    // --- history & admin ---

    @Test
    void historyMasksPhoneButAdminSeesFullDetails() {
        PaymentTransaction txn = pendingTransaction("ws_CO_admin", 5);

        List<PaymentTransactionResponse> history = billingService.getAccountBillingHistory(accountId.toString());
        assertEquals(1, history.size());
        assertEquals("2547****5678", history.getFirst().getPhoneNumber());

        List<AdminPaymentTransactionResponse> admin = billingAdminService.listTransactions(accountId.toString());
        assertEquals(1, admin.size());
        assertEquals("254712345678", admin.getFirst().getPhoneNumber());
        assertEquals("ws_CO_admin", admin.getFirst().getCheckoutRequestId());
        assertEquals(txn.getId().toString(), admin.getFirst().getId());
        assertEquals(1L, billingAdminService.getStats().get("pendingTransactions"));
    }

    @Test
    void refundOnlyForCompletedTransactions() throws Exception {
        PaymentTransaction txn = pendingTransaction("ws_CO_refund", 5);
        assertThrows(BadRequestException.class, () -> billingService.refundTransaction(txn.getId().toString()));

        when(subscriptionClient.activate(accountId, planId, txn.getId())).thenReturn(activated());
        billingService.handleCallback(TOKEN, callback("ws_CO_refund", 0, "ok", "QJR7REFND1"));

        assertEquals(PaymentStatus.REFUNDED, billingService.refundTransaction(txn.getId().toString()).getStatus());
    }
}
