package com.streamx.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.streamx.billing.mpesa.MpesaException;
import com.streamx.billing.mpesa.MpesaGateway;
import com.streamx.billing.mpesa.StkPushResult;
import com.streamx.billing.mpesa.StkQueryResult;
import com.streamx.billing.repository.PaymentTransactionRepository;
import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.common.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    public static final String NOT_CONFIGURED_MESSAGE = "M-Pesa payments are not configured yet";
    public static final String TIMEOUT_MESSAGE = "M-Pesa did not confirm the payment in time";
    public static final String CANCELLED_MESSAGE = "You cancelled the M-Pesa prompt";
    public static final String PAYMENT_IN_PROGRESS_MESSAGE = "A payment request is already in progress. Check your phone.";
    public static final String INVALID_PHONE_MESSAGE = "Enter a valid Safaricom M-Pesa number, e.g. 0712345678";
    public static final String PAYMENT_METHOD_MPESA = "MPESA";

    static final String ACCOUNT_REFERENCE = "StreamX";
    static final String MPESA_CANCELLED_RESULT_CODE = "1032";
    static final Duration RECONCILE_AFTER = Duration.ofSeconds(20);
    static final Duration CONFIRMATION_TIMEOUT = Duration.ofMinutes(3);
    static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(2);
    static final Duration ACTIVATION_RETRY_WINDOW = Duration.ofDays(7);

    private static final Set<PaymentStatus> IN_FLIGHT = EnumSet.of(PaymentStatus.INITIATED, PaymentStatus.PENDING);
    private static final int MAX_ERROR_LENGTH = 250;
    private static final DateTimeFormatter HUMAN_DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final PaymentTransactionRepository transactionRepository;
    private final MpesaProperties mpesaProperties;
    private final MpesaGateway mpesaGateway;
    private final SubscriptionClient subscriptionClient;
    private final NotificationClient notificationClient;
    private final TransactionTemplate transactionTemplate;

    public BillingService(PaymentTransactionRepository transactionRepository,
                          MpesaProperties mpesaProperties,
                          MpesaGateway mpesaGateway,
                          SubscriptionClient subscriptionClient,
                          NotificationClient notificationClient,
                          PlatformTransactionManager transactionManager) {
        this.transactionRepository = transactionRepository;
        this.mpesaProperties = mpesaProperties;
        this.mpesaGateway = mpesaGateway;
        this.subscriptionClient = subscriptionClient;
        this.notificationClient = notificationClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // --- Checkout (STK Push) ---

    public PaymentTransactionResponse checkout(String accountIdStr, CheckoutRequest request) {
        UUID accountId = parseAccountId(accountIdStr);
        if (!mpesaProperties.isConfigured()) {
            throw new ServiceUnavailableException(NOT_CONFIGURED_MESSAGE);
        }
        String phone = PhoneNumbers.normalize(request.getPhoneNumber())
                .orElseThrow(() -> new BadRequestException(INVALID_PHONE_MESSAGE));

        PlanDetails plan = subscriptionClient.getPlan(parseId(request.getPlanId(), "Plan not found"));
        if (!plan.active()) {
            throw new BadRequestException("This plan is no longer available. Choose another plan.");
        }
        if (plan.currency() == null || !"KES".equalsIgnoreCase(plan.currency().trim())) {
            throw new BadRequestException("M-Pesa payments are only available for plans priced in KES");
        }
        if (plan.price() == null || plan.price().signum() <= 0) {
            throw new BadRequestException("This plan is free. Subscribe to it directly without paying.");
        }
        BigDecimal amount = plan.price().setScale(0, RoundingMode.CEILING);

        LocalDateTime now = LocalDateTime.now();
        if (transactionRepository.existsByAccountIdAndStatusInAndCreatedAtAfter(accountId, IN_FLIGHT, now.minus(DUPLICATE_WINDOW))) {
            throw new BadRequestException(PAYMENT_IN_PROGRESS_MESSAGE);
        }

        PaymentTransaction txn = new PaymentTransaction();
        txn.setAccountId(accountId);
        txn.setPlanId(plan.id());
        txn.setPlanName(plan.name());
        txn.setAmount(amount);
        txn.setCurrency("KES");
        txn.setStatus(PaymentStatus.INITIATED);
        txn.setPaymentMethod(PAYMENT_METHOD_MPESA);
        txn.setPhoneNumber(phone);
        txn = transactionRepository.save(txn);

        StkPushResult result;
        try {
            result = mpesaGateway.initiateStkPush(phone, amount.longValueExact(), ACCOUNT_REFERENCE, plan.name() + " plan");
        } catch (MpesaException e) {
            markFailed(txn, e.getMessage());
            throw new BadGatewayException(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Unexpected error starting STK Push for transaction {}", txn.getId(), e);
            String message = "Could not start the M-Pesa payment. Please try again.";
            markFailed(txn, message);
            throw new BadGatewayException(message);
        }

        txn.setMerchantRequestId(result.merchantRequestId());
        txn.setCheckoutRequestId(result.checkoutRequestId());
        if (!result.accepted()) {
            String message = firstNonBlank(result.responseDescription(), result.customerMessage(),
                    "M-Pesa rejected the payment request");
            markFailed(txn, message);
            throw new BadGatewayException(message);
        }
        txn.setStatus(PaymentStatus.PENDING);
        txn = transactionRepository.save(txn);
        log.info("STK Push sent for transaction {} (account {}, plan {}, KES {})",
                txn.getId(), accountId, plan.id(), amount.toPlainString());
        return mapToResponse(txn);
    }

    // --- Daraja callback ---

    /**
     * Validates the callback token (404 when wrong) and applies the STK result. Processing errors are logged and
     * swallowed so Daraja always gets an "Accepted" acknowledgement for a valid token.
     */
    public void handleCallback(String token, JsonNode payload) {
        if (!isValidCallbackToken(token)) {
            throw new ResourceNotFoundException("Not found");
        }
        try {
            processCallback(payload);
        } catch (Exception e) {
            log.error("Failed to process M-Pesa callback", e);
        }
    }

    boolean isValidCallbackToken(String token) {
        String expected = mpesaProperties.getCallbackToken();
        if (expected == null || expected.isBlank() || token == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.trim().getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    private void processCallback(JsonNode payload) {
        JsonNode callback = payload == null ? null : payload.path("Body").path("stkCallback");
        if (callback == null || !callback.isObject()) {
            log.warn("Ignoring M-Pesa callback without Body.stkCallback");
            return;
        }
        String checkoutRequestId = text(callback, "CheckoutRequestID");
        String resultCode = text(callback, "ResultCode");
        String resultDesc = text(callback, "ResultDesc");
        if (checkoutRequestId == null || resultCode == null) {
            log.warn("Ignoring M-Pesa callback without CheckoutRequestID/ResultCode");
            return;
        }

        PaymentTransaction txn = transactionRepository.findFirstByCheckoutRequestId(checkoutRequestId).orElse(null);
        if (txn == null) {
            log.warn("Ignoring M-Pesa callback for unknown CheckoutRequestID {}", checkoutRequestId);
            return;
        }

        String receipt = null;
        BigDecimal paidAmount = null;
        for (JsonNode item : callback.path("CallbackMetadata").path("Item")) {
            String name = text(item, "Name");
            JsonNode value = item.get("Value");
            if (value == null || value.isNull()) {
                continue;
            }
            if ("MpesaReceiptNumber".equals(name)) {
                receipt = value.asText();
            } else if ("Amount".equals(name) && value.isNumber()) {
                paidAmount = value.decimalValue();
            }
        }
        if (paidAmount != null && txn.getAmount() != null && paidAmount.compareTo(txn.getAmount()) != 0) {
            log.warn("M-Pesa callback amount {} differs from transaction {} amount {}", paidAmount, txn.getId(), txn.getAmount());
        }

        log.info("M-Pesa callback for transaction {}: ResultCode={} ResultDesc={}", txn.getId(), resultCode, resultDesc);
        applyOutcome(txn.getId(), outcomeFor(resultCode, resultDesc, receipt));
    }

    // --- Transaction status & reconciliation ---

    public PaymentTransactionResponse getTransaction(String accountIdStr, String transactionIdStr) {
        UUID accountId = parseAccountId(accountIdStr);
        UUID transactionId = parseId(transactionIdStr, "Transaction not found");
        PaymentTransaction txn = transactionRepository.findByIdAndAccountId(transactionId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (IN_FLIGHT.contains(txn.getStatus()) && age(txn).compareTo(RECONCILE_AFTER) > 0) {
            reconcile(txn);
            txn = transactionRepository.findById(transactionId).orElse(txn);
        }
        return mapToResponse(txn);
    }

    /**
     * Resolves in-flight transactions older than 20 seconds via STK Query; times them out after 3 minutes.
     */
    public void reconcileStaleTransactions() {
        LocalDateTime cutoff = LocalDateTime.now().minus(RECONCILE_AFTER);
        for (PaymentTransaction txn : transactionRepository.findByStatusInAndCreatedAtBefore(IN_FLIGHT, cutoff)) {
            try {
                reconcile(txn);
            } catch (Exception e) {
                log.warn("Reconciliation of transaction {} failed: {}", txn.getId(), e.getMessage());
            }
        }
    }

    /**
     * Retries subscription activation for completed payments whose activation call failed earlier.
     */
    public void retryPendingActivations() {
        LocalDateTime since = LocalDateTime.now().minus(ACTIVATION_RETRY_WINDOW);
        List<PaymentTransaction> pending = transactionRepository
                .findByStatusAndSubscriptionIdIsNullAndPlanIdIsNotNullAndUpdatedAtAfter(PaymentStatus.COMPLETED, since);
        for (PaymentTransaction txn : pending) {
            activateSubscription(txn);
        }
    }

    void reconcile(PaymentTransaction txn) {
        PaymentOutcome outcome = null;
        if (txn.getCheckoutRequestId() != null && mpesaProperties.isConfigured()) {
            try {
                StkQueryResult result = mpesaGateway.queryStkPush(txn.getCheckoutRequestId());
                if (!result.pending()) {
                    outcome = outcomeFor(result.resultCode(), result.resultDesc(), null);
                }
            } catch (Exception e) {
                log.warn("STK query for transaction {} failed: {}", txn.getId(), e.getMessage());
            }
        }
        if (outcome == null && age(txn).compareTo(CONFIRMATION_TIMEOUT) >= 0) {
            outcome = new PaymentOutcome(PaymentStatus.FAILED, TIMEOUT_MESSAGE, null);
        }
        if (outcome != null) {
            applyOutcome(txn.getId(), outcome);
        }
    }

    // --- State transitions ---

    record PaymentOutcome(PaymentStatus status, String errorMessage, String receipt) {
    }

    static PaymentOutcome outcomeFor(String resultCode, String resultDesc, String receipt) {
        String code = resultCode == null ? "" : resultCode.trim();
        if ("0".equals(code)) {
            return new PaymentOutcome(PaymentStatus.COMPLETED, null, receipt);
        }
        if (MPESA_CANCELLED_RESULT_CODE.equals(code)) {
            return new PaymentOutcome(PaymentStatus.CANCELLED, CANCELLED_MESSAGE, null);
        }
        String message = firstNonBlank(resultDesc, null, "M-Pesa payment failed (code " + code + ")");
        return new PaymentOutcome(PaymentStatus.FAILED, message, null);
    }

    /**
     * Applies a final M-Pesa result exactly once (row lock + status check), then runs side effects
     * (subscription activation, notification) outside the database transaction.
     */
    void applyOutcome(UUID transactionId, PaymentOutcome outcome) {
        PaymentTransaction transitioned = transactionTemplate.execute(status -> {
            PaymentTransaction txn = transactionRepository.findByIdForUpdate(transactionId).orElse(null);
            if (txn == null) {
                return null;
            }
            boolean lateSuccessAfterTimeout = txn.getStatus() == PaymentStatus.FAILED
                    && TIMEOUT_MESSAGE.equals(txn.getErrorMessage())
                    && outcome.status() == PaymentStatus.COMPLETED;
            if (IN_FLIGHT.contains(txn.getStatus()) || lateSuccessAfterTimeout) {
                txn.setStatus(outcome.status());
                txn.setErrorMessage(truncate(outcome.errorMessage()));
                if (outcome.receipt() != null) {
                    txn.setExternalTransactionId(outcome.receipt());
                }
                return transactionRepository.save(txn);
            }
            if (txn.getStatus() == PaymentStatus.COMPLETED && outcome.status() == PaymentStatus.COMPLETED
                    && txn.getExternalTransactionId() == null && outcome.receipt() != null) {
                txn.setExternalTransactionId(outcome.receipt());
                transactionRepository.save(txn);
            }
            return null;
        });
        if (transitioned == null) {
            log.debug("Transaction {} already settled; ignoring duplicate result", transactionId);
            return;
        }

        log.info("Transaction {} is now {}", transitioned.getId(), transitioned.getStatus());
        if (transitioned.getStatus() == PaymentStatus.COMPLETED) {
            ActivatedSubscription subscription = activateSubscription(transitioned);
            notifySuccess(transitioned, subscription);
        } else {
            notifyFailure(transitioned);
        }
    }

    private ActivatedSubscription activateSubscription(PaymentTransaction txn) {
        if (txn.getPlanId() == null) {
            return null;
        }
        try {
            ActivatedSubscription subscription = subscriptionClient.activate(txn.getAccountId(), txn.getPlanId(), txn.getId());
            transactionTemplate.executeWithoutResult(status ->
                    transactionRepository.findById(txn.getId()).ifPresent(t -> {
                        t.setSubscriptionId(subscription.id());
                        transactionRepository.save(t);
                    }));
            log.info("Activated subscription {} for transaction {}", subscription.id(), txn.getId());
            return subscription;
        } catch (Exception e) {
            log.error("Subscription activation for paid transaction {} failed; it will be retried: {}",
                    txn.getId(), e.getMessage());
            return null;
        }
    }

    private void notifySuccess(PaymentTransaction txn, ActivatedSubscription subscription) {
        String planName = txn.getPlanName() != null ? txn.getPlanName() : "your";
        StringBuilder body = new StringBuilder("We received KES ")
                .append(formatAmount(txn.getAmount()))
                .append(" via M-Pesa");
        if (txn.getExternalTransactionId() != null) {
            body.append(" (receipt ").append(txn.getExternalTransactionId()).append(")");
        }
        body.append(". ");
        String until = subscription != null ? humanDate(subscription.currentPeriodEnd()) : null;
        if (subscription != null && until != null) {
            body.append("Your ").append(planName).append(" plan is active until ").append(until).append(".");
        } else if (subscription != null) {
            body.append("Your ").append(planName).append(" plan is now active.");
        } else {
            body.append("We're activating your ").append(planName).append(" plan now.");
        }
        sendNotification(txn, "PAYMENT_SUCCESS", "Payment received", body.toString());
    }

    private void notifyFailure(PaymentTransaction txn) {
        String subject = txn.getStatus() == PaymentStatus.CANCELLED ? "Payment cancelled" : "Payment failed";
        String planName = txn.getPlanName() != null ? txn.getPlanName() : "selected";
        String reason = txn.getErrorMessage() != null ? txn.getErrorMessage() : "M-Pesa did not complete the payment";
        String body = "Your M-Pesa payment of KES " + formatAmount(txn.getAmount()) + " for the " + planName
                + " plan was not completed. Reason: " + reason;
        sendNotification(txn, "PAYMENT_FAILED", subject, body);
    }

    private void sendNotification(PaymentTransaction txn, String template, String subject, String body) {
        try {
            notificationClient.sendInApp(txn.getAccountId(), template, subject, body);
        } catch (Exception e) {
            log.warn("Could not send {} notification for transaction {}: {}", template, txn.getId(), e.getMessage());
        }
    }

    // --- History & refunds ---

    @Transactional(readOnly = true)
    public List<PaymentTransactionResponse> getAccountBillingHistory(String accountIdStr) {
        UUID accountId = parseAccountId(accountIdStr);
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Record-keeping only: the actual M-Pesa reversal is performed in the M-Pesa portal.
     */
    @Transactional
    public AdminPaymentTransactionResponse refundTransaction(String transactionIdStr) {
        UUID transactionId = parseId(transactionIdStr, "Transaction not found");
        PaymentTransaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (txn.getStatus() != PaymentStatus.COMPLETED) {
            throw new BadRequestException("Only completed transactions can be refunded (current status: " + txn.getStatus() + ")");
        }
        txn.setStatus(PaymentStatus.REFUNDED);
        PaymentTransaction updated = transactionRepository.save(txn);
        log.info("Marked transaction {} as refunded", transactionIdStr);
        return mapToAdminResponse(updated);
    }

    // --- Mapping ---

    public PaymentTransactionResponse mapToResponse(PaymentTransaction txn) {
        PaymentTransactionResponse response = new PaymentTransactionResponse();
        fill(response, txn);
        response.setPhoneNumber(PhoneNumbers.mask(txn.getPhoneNumber()));
        return response;
    }

    public AdminPaymentTransactionResponse mapToAdminResponse(PaymentTransaction txn) {
        AdminPaymentTransactionResponse response = new AdminPaymentTransactionResponse();
        fill(response, txn);
        response.setPhoneNumber(txn.getPhoneNumber());
        response.setMerchantRequestId(txn.getMerchantRequestId());
        response.setCheckoutRequestId(txn.getCheckoutRequestId());
        return response;
    }

    private static void fill(PaymentTransactionResponse response, PaymentTransaction txn) {
        response.setId(txn.getId().toString());
        response.setAccountId(txn.getAccountId().toString());
        response.setSubscriptionId(txn.getSubscriptionId() != null ? txn.getSubscriptionId().toString() : null);
        response.setPlanId(txn.getPlanId() != null ? txn.getPlanId().toString() : null);
        response.setPlanName(txn.getPlanName());
        response.setAmount(txn.getAmount());
        response.setCurrency(txn.getCurrency());
        response.setStatus(txn.getStatus());
        response.setPaymentMethod(txn.getPaymentMethod());
        response.setExternalTransactionId(txn.getExternalTransactionId());
        response.setErrorMessage(txn.getErrorMessage());
        response.setCreatedAt(txn.getCreatedAt());
        response.setUpdatedAt(txn.getUpdatedAt());
    }

    // --- Helpers ---

    private void markFailed(PaymentTransaction txn, String message) {
        txn.setStatus(PaymentStatus.FAILED);
        txn.setErrorMessage(truncate(message));
        transactionRepository.save(txn);
    }

    private static Duration age(PaymentTransaction txn) {
        if (txn.getCreatedAt() == null) {
            return Duration.ofDays(365);
        }
        return Duration.between(txn.getCreatedAt(), LocalDateTime.now());
    }

    private static UUID parseAccountId(String accountIdStr) {
        try {
            return UUID.fromString(accountIdStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UnauthorizedException("Authentication required");
        }
    }

    private static UUID parseId(String value, String notFoundMessage) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResourceNotFoundException(notFoundMessage);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }

    private static String formatAmount(BigDecimal amount) {
        return amount == null ? "0" : amount.stripTrailingZeros().toPlainString();
    }

    private static String humanDate(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(isoDateTime).format(HUMAN_DATE);
        } catch (Exception e) {
            return null;
        }
    }
}
