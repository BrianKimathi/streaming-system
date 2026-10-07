package com.streamx.billing.controller;

import com.streamx.billing.config.SecurityConfig;
import com.streamx.billing.domain.PaymentStatus;
import com.streamx.billing.dto.PaymentTransactionResponse;
import com.streamx.billing.exception.BadGatewayException;
import com.streamx.billing.exception.ServiceUnavailableException;
import com.streamx.billing.service.BillingService;
import com.streamx.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BillingController.class)
@Import(SecurityConfig.class)
class BillingControllerTest {

    private static final String CHECKOUT_BODY = "{\"planId\":\"" + UUID.randomUUID() + "\",\"phoneNumber\":\"0712345678\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BillingService billingService;

    @Test
    void checkoutReturnsPendingTransaction() throws Exception {
        String accountId = UUID.randomUUID().toString();
        PaymentTransactionResponse response = new PaymentTransactionResponse();
        response.setId(UUID.randomUUID().toString());
        response.setStatus(PaymentStatus.PENDING);
        response.setPhoneNumber("2547****5678");
        when(billingService.checkout(eq(accountId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("X-Account-Id", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHECKOUT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.phoneNumber").value("2547****5678"));
    }

    @Test
    void checkoutNotConfiguredIs503() throws Exception {
        when(billingService.checkout(any(), any()))
                .thenThrow(new ServiceUnavailableException("M-Pesa payments are not configured yet"));

        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHECKOUT_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("M-Pesa payments are not configured yet"));
    }

    @Test
    void darajaRejectionIs502() throws Exception {
        when(billingService.checkout(any(), any())).thenThrow(new BadGatewayException("Bad Request - Invalid PhoneNumber"));

        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHECKOUT_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Bad Request - Invalid PhoneNumber"));
    }

    @Test
    void checkoutValidatesBody() throws Exception {
        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(billingService, never()).checkout(any(), any());
    }

    @Test
    void checkoutWithoutIdentityIs401() throws Exception {
        mockMvc.perform(post("/api/v1/billing/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHECKOUT_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void callbackAcknowledgesWithDarajaShape() throws Exception {
        mockMvc.perform(post("/api/v1/billing/mpesa/callback/the-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"Body\":{\"stkCallback\":{\"CheckoutRequestID\":\"ws_CO_1\",\"ResultCode\":0}}}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"ResultCode\":0,\"ResultDesc\":\"Accepted\"}", true));
        verify(billingService).handleCallback(eq("the-token"), any());
    }

    @Test
    void callbackWithWrongTokenIs404() throws Exception {
        doThrow(new ResourceNotFoundException("Not found")).when(billingService).handleCallback(eq("bad"), any());

        mockMvc.perform(post("/api/v1/billing/mpesa/callback/bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void transactionNotOwnedIs404() throws Exception {
        String accountId = UUID.randomUUID().toString();
        String txnId = UUID.randomUUID().toString();
        when(billingService.getTransaction(accountId, txnId)).thenThrow(new ResourceNotFoundException("Transaction not found"));

        mockMvc.perform(get("/api/v1/billing/transactions/" + txnId).header("X-Account-Id", accountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction not found"));
    }

    @Test
    void legacyPayEndpointIsGone() throws Exception {
        mockMvc.perform(post("/api/v1/billing/pay")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":999}"))
                .andExpect(status().is4xxClientError());
    }
}
