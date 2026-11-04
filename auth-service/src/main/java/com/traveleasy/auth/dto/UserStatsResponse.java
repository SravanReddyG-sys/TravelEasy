package com.traveleasy.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatsResponse {
    private long totalUsers;
    private long travellers;
    private long busOperators;
    private long hotelOperators;
}
