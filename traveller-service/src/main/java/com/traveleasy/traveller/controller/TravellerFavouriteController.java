package com.traveleasy.traveller.controller;

import com.traveleasy.traveller.dto.AddFavouriteRequest;
import com.traveleasy.traveller.dto.TravellerFavouriteDto;
import com.traveleasy.traveller.security.TravellerPrincipal;
import com.traveleasy.traveller.service.TravellerFavouriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/traveller/favourites")
@RequiredArgsConstructor
public class TravellerFavouriteController {

    private final TravellerFavouriteService favouriteService;

    @GetMapping
    public ResponseEntity<List<TravellerFavouriteDto>> getFavourites(@AuthenticationPrincipal TravellerPrincipal principal,
                                                                    @RequestParam(required = false) String type) {
        return ResponseEntity.ok(favouriteService.getFavourites(principal, type));
    }

    @PostMapping
    public ResponseEntity<TravellerFavouriteDto> addFavourite(@AuthenticationPrincipal TravellerPrincipal principal,
                                                             @RequestBody AddFavouriteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(favouriteService.addFavourite(principal, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFavourite(@AuthenticationPrincipal TravellerPrincipal principal,
                                            @PathVariable Long id) {
        boolean deleted = favouriteService.deleteFavourite(principal, id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Favourite removed successfully"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Favourite not found"));
        }
    }
}
