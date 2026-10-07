package com.streamx.subscription.service;

import com.streamx.subscription.domain.BillingInterval;
import com.streamx.subscription.domain.SubscriptionStatus;
import com.streamx.subscription.domain.VideoResolution;
import com.streamx.subscription.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubscriptionServiceTest {

    @Autowired
    private SubscriptionService subscriptionService;

    @Test
    void testCreatePlanAndVersioning() {
        CreatePlanRequest req1 = new CreatePlanRequest();
        req1.setName("Premium");
        req1.setPrice(new BigDecimal("999.00"));
        req1.setMaxProfiles(4);
        req1.setMaxRegisteredDevices(5);
        req1.setMaxConcurrentStreams(4);
        req1.setMaxResolution(VideoResolution.UHD_4K);

        PlanResponse v1 = subscriptionService.createPlan(req1);

        assertEquals("Premium", v1.getName());
        assertEquals(1, v1.getVersion());
        assertTrue(v1.isActive());

        // Create Premium v2
        CreatePlanRequest req2 = new CreatePlanRequest();
        req2.setName("Premium");
        req2.setPrice(new BigDecimal("1199.00"));
        req2.setMaxProfiles(5);
        req2.setMaxRegisteredDevices(6);
        req2.setMaxConcurrentStreams(4);
        req2.setMaxResolution(VideoResolution.UHD_4K);

        PlanResponse v2 = subscriptionService.createPlan(req2);

        assertEquals("Premium", v2.getName());
        assertEquals(2, v2.getVersion());
        assertTrue(v2.isActive());
    }

    @Test
    void testSubscribeAndEntitlements() {
        CreatePlanRequest req = new CreatePlanRequest();
        req.setName("Standard");
        req.setPrice(new BigDecimal("599.00"));
        req.setMaxProfiles(3);
        req.setMaxRegisteredDevices(4);
        req.setMaxConcurrentStreams(2);
        req.setMaxResolution(VideoResolution.FHD_1080P);

        PlanResponse plan = subscriptionService.createPlan(req);

        String accountId = UUID.randomUUID().toString();
        SubscribeRequest subReq = new SubscribeRequest(plan.getId());

        SubscriptionResponse subRes = subscriptionService.subscribe(accountId, subReq);

        assertNotNull(subRes.getId());
        assertEquals(SubscriptionStatus.ACTIVE, subRes.getStatus());
        assertEquals(plan.getId(), subRes.getPlan().getId());

        EntitlementsResponse entitlements = subscriptionService.getEntitlements(accountId);
        assertEquals(3, entitlements.getMaxProfiles());
        assertEquals(4, entitlements.getMaxRegisteredDevices());
        assertEquals(2, entitlements.getMaxConcurrentStreams());
        assertEquals(VideoResolution.FHD_1080P, entitlements.getMaxResolution());
    }
}
