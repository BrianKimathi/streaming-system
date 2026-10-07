package com.streamx.subscription.controller;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.subscription.config.SecurityConfig;
import com.streamx.subscription.dto.ActivateSubscriptionRequest;
import com.streamx.subscription.dto.SubscriptionResponse;
import com.streamx.subscription.service.SubscriptionService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {SubscriptionController.class, SubscriptionInternalController.class})
@Import(SecurityConfig.class)
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubscriptionService subscriptionService;

    @Test
    void paidPlanSubscribeReturns400WithMessage() throws Exception {
        String accountId = UUID.randomUUID().toString();
        when(subscriptionService.subscribe(eq(accountId), any()))
                .thenThrow(new BadRequestException("This plan requires payment. Pay with M-Pesa to subscribe."));

        mockMvc.perform(post("/api/v1/subscriptions/subscribe")
                        .header("X-Account-Id", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("This plan requires payment. Pay with M-Pesa to subscribe."));
    }

    @Test
    void missingAccountHeaderIs401() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        verify(subscriptionService, never()).getAccountSubscription(any());
    }

    @Test
    void noSubscriptionIs404() throws Exception {
        String accountId = UUID.randomUUID().toString();
        when(subscriptionService.getAccountSubscription(accountId))
                .thenThrow(new ResourceNotFoundException("No subscription found"));

        mockMvc.perform(get("/api/v1/subscriptions/me").header("X-Account-Id", accountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No subscription found"));
    }

    @Test
    void cancelAndResumeEndpoints() throws Exception {
        String accountId = UUID.randomUUID().toString();
        SubscriptionResponse response = new SubscriptionResponse();
        response.setCancelAtPeriodEnd(true);
        when(subscriptionService.cancel(accountId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/subscriptions/me/cancel").header("X-Account-Id", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelAtPeriodEnd").value(true));

        when(subscriptionService.resume(accountId)).thenReturn(new SubscriptionResponse());
        mockMvc.perform(post("/api/v1/subscriptions/me/resume").header("X-Account-Id", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelAtPeriodEnd").value(false));
    }

    @Test
    void internalActivateValidatesBody() throws Exception {
        mockMvc.perform(post("/api/v1/subscriptions/internal/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
        verify(subscriptionService, never()).activate(any(ActivateSubscriptionRequest.class));
    }

    @Test
    void internalActivateReturnsSubscription() throws Exception {
        SubscriptionResponse response = new SubscriptionResponse();
        response.setId(UUID.randomUUID().toString());
        when(subscriptionService.activate(any(ActivateSubscriptionRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/subscriptions/internal/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + UUID.randomUUID() + "\",\"planId\":\"" + UUID.randomUUID()
                                + "\",\"paymentTransactionId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(response.getId()));
    }

    @Test
    void internalPlanNotFoundIs404() throws Exception {
        String planId = UUID.randomUUID().toString();
        when(subscriptionService.getPlan(planId)).thenThrow(new ResourceNotFoundException("Plan not found"));

        mockMvc.perform(get("/api/v1/subscriptions/internal/plans/" + planId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Plan not found"));
    }
}
