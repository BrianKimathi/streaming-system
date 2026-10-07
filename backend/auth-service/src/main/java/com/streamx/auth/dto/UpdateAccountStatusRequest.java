package com.streamx.auth.dto;

public class UpdateAccountStatusRequest {
    private boolean blocked;

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }
}
