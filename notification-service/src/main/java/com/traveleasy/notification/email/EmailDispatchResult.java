package com.traveleasy.notification.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailDispatchResult {
    private boolean accepted;
    @Builder.Default
    private boolean simulated = false;
    private String providerMessageId;
    private int statusCode;
    private String errorMessage;
    @Builder.Default
    private Boolean transientFailure = null;

    public boolean isTransientFailure() {
        if (transientFailure != null) {
            return transientFailure;
        }
        return statusCode == 429 || (statusCode >= 500 && statusCode <= 599) || statusCode == 0;
    }
}
