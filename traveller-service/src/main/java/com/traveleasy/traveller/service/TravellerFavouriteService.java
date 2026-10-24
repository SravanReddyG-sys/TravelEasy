package com.traveleasy.traveller.service;

import com.traveleasy.traveller.dto.AddFavouriteRequest;
import com.traveleasy.traveller.dto.TravellerFavouriteDto;
import com.traveleasy.traveller.model.TravellerFavourite;
import com.traveleasy.traveller.repository.TravellerFavouriteRepository;
import com.traveleasy.traveller.security.TravellerPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TravellerFavouriteService {

    private final TravellerFavouriteRepository favouriteRepository;

    public List<TravellerFavouriteDto> getFavourites(TravellerPrincipal principal, String itemType) {
        List<TravellerFavourite> list;
        if (itemType != null && !itemType.trim().isEmpty()) {
            list = favouriteRepository.findByUserIdAndItemTypeOrderByCreatedAtDesc(
                    principal.getUserId(), itemType.trim().toUpperCase());
        } else {
            list = favouriteRepository.findByUserIdOrderByCreatedAtDesc(principal.getUserId());
        }
        return list.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional
    public TravellerFavouriteDto addFavourite(TravellerPrincipal principal, AddFavouriteRequest request) {
        String itemType = request.getItemType() != null ? request.getItemType().trim().toUpperCase() : "ROUTE";

        // Check if already favourited
        if (request.getItemId() != null && favouriteRepository.existsByUserIdAndItemTypeAndItemId(principal.getUserId(), itemType, request.getItemId())) {
            // Already saved, return existing
            TravellerFavourite existing = favouriteRepository.findByUserIdAndItemTypeOrderByCreatedAtDesc(principal.getUserId(), itemType)
                    .stream()
                    .filter(f -> request.getItemId().equals(f.getItemId()))
                    .findFirst()
                    .orElse(null);
            if (existing != null) return mapToDto(existing);
        }

        TravellerFavourite fav = TravellerFavourite.builder()
                .userId(principal.getUserId())
                .itemType(itemType)
                .itemId(request.getItemId())
                .title(request.getTitle() != null ? request.getTitle().trim() : "Saved Item")
                .subtitle(request.getSubtitle() != null ? request.getSubtitle().trim() : "")
                .metadataJson(request.getMetadataJson() != null ? request.getMetadataJson().trim() : "")
                .build();

        return mapToDto(favouriteRepository.save(fav));
    }

    @Transactional
    public boolean deleteFavourite(TravellerPrincipal principal, Long id) {
        return favouriteRepository.findByIdAndUserId(id, principal.getUserId()).map(fav -> {
            favouriteRepository.delete(fav);
            return true;
        }).orElse(false);
    }

    private TravellerFavouriteDto mapToDto(TravellerFavourite fav) {
        return TravellerFavouriteDto.builder()
                .id(fav.getId())
                .userId(fav.getUserId())
                .itemType(fav.getItemType())
                .itemId(fav.getItemId())
                .title(fav.getTitle())
                .subtitle(fav.getSubtitle())
                .metadataJson(fav.getMetadataJson())
                .createdAt(fav.getCreatedAt())
                .build();
    }
}
