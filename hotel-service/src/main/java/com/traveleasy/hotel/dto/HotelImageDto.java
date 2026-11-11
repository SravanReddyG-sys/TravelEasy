package com.traveleasy.hotel.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.traveleasy.hotel.model.HotelImage;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelImageDto {
    private Long id;

    @NotBlank(message = "Image URL is required")
    private String imageUrl;

    private String originalFileName;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @JsonProperty("isCover")
    @JsonAlias({"cover", "is_cover"})
    private boolean isCover;

    private int displayOrder;

    @JsonProperty("isCover")
    public boolean isCover() {
        return this.isCover;
    }

    @JsonProperty("isCover")
    @JsonAlias({"cover", "is_cover"})
    public void setCover(boolean isCover) {
        this.isCover = isCover;
    }

    public static HotelImageDto fromEntity(HotelImage entity) {
        if (entity == null) return null;
        return HotelImageDto.builder()
                .id(entity.getId())
                .imageUrl(entity.getImageUrl())
                .originalFileName(entity.getOriginalFileName())
                .isCover(entity.isCover())
                .displayOrder(entity.getDisplayOrder())
                .build();
    }
}
