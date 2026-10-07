package com.streamx.auth.client;

import java.util.Optional;
import java.util.UUID;

public interface ProfileDirectoryClient {

    /**
     * Returns the account that owns the profile, or empty if the profile does not exist.
     * Throws {@link com.streamx.auth.exception.ServiceUnavailableException} if user-service cannot be reached.
     */
    Optional<UUID> findOwnerAccountId(UUID profileId);
}
