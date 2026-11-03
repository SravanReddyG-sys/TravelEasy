package com.traveleasy.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipientDto {
    private RecipientRole role;
    private Long userId;
    private String email;
    private String name;

    @Builder.Default
    private List<String> channels = new ArrayList<>();
}
