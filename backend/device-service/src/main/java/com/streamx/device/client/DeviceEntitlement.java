package com.streamx.device.client;

/** The subset of subscription-service's EntitlementsResponse that device registration needs. */
public record DeviceEntitlement(String status, int maxRegisteredDevices) {
}
