package com.streamx.playback.client;

import java.util.UUID;

public interface DeviceClient {

    enum DeviceStatus { ACTIVE, REVOKED, NOT_FOUND }

    /**
     * Returns the registration status of the device for the account; NOT_FOUND when the device does not exist or
     * belongs to another account. Throws ServiceUnavailableException when device-service cannot be reached.
     */
    DeviceStatus deviceStatus(UUID accountId, UUID deviceId);
}
