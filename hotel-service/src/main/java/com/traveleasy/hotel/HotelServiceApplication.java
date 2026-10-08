package com.traveleasy.hotel;

import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.model.RoomType;
import com.traveleasy.hotel.repository.HotelPropertyRepository;
import com.traveleasy.hotel.repository.RoomTypeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class HotelServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HotelServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner initHotelData(HotelPropertyRepository hotelRepository, RoomTypeRepository roomTypeRepository) {
        return args -> {
            if (hotelRepository.count() == 0) {
                HotelProperty hotel1 = hotelRepository.save(HotelProperty.builder()
                        .managerId(3L)
                        .managerName("Grand Residency Hotel")
                        .name("Novotel Vijayawada Varun")
                        .city("Vijayawada")
                        .address("Bharathi Nagar, Vijayawada, Andhra Pradesh")
                        .starRating(5)
                        .description("Luxury 5-star hotel featuring an infinity rooftop pool, spa, and international dining options.")
                        .amenities("Infinity Pool, Free Wi-Fi, Fitness Center, 24/7 Room Service, Bar")
                        .imageUrl("https://images.unsplash.com/photo-1566073771259-6a8506099945")
                        .build());

                HotelProperty hotel2 = hotelRepository.save(HotelProperty.builder()
                        .managerId(3L)
                        .managerName("Grand Residency Hotel")
                        .name("Taj Krishna Hyderabad")
                        .city("Hyderabad")
                        .address("Road No 1, Banjara Hills, Hyderabad, Telangana")
                        .starRating(5)
                        .description("Nestled in scenic Banjara Hills, offering world-class luxury accommodations and heritage gardens.")
                        .amenities("Swimming Pool, Spa, Executive Lounge, Valet Parking, Fine Dining")
                        .imageUrl("https://images.unsplash.com/photo-1542314831-068cd1dbfeeb")
                        .build());

                // Seed Room Types
                roomTypeRepository.save(RoomType.builder()
                        .hotelId(hotel1.getId())
                        .roomTypeName("Deluxe King Room")
                        .maxOccupancy(2)
                        .pricePerNight(4500.0)
                        .totalRooms(20)
                        .availableRooms(20)
                        .amenities("King Size Bed, City View, Free Wi-Fi, Flat-screen TV, Breakfast")
                        .build());

                roomTypeRepository.save(RoomType.builder()
                        .hotelId(hotel1.getId())
                        .roomTypeName("Executive Luxury Suite")
                        .maxOccupancy(4)
                        .pricePerNight(8500.0)
                        .totalRooms(10)
                        .availableRooms(10)
                        .amenities("Separate Living Room, Pool View, Jacuzzi, Complimentary Drinks")
                        .build());

                roomTypeRepository.save(RoomType.builder()
                        .hotelId(hotel2.getId())
                        .roomTypeName("Superior Garden View Room")
                        .maxOccupancy(2)
                        .pricePerNight(6000.0)
                        .totalRooms(15)
                        .availableRooms(15)
                        .amenities("King Bed, Garden View, High-Speed Internet, Coffee Maker")
                        .build());
            }
        };
    }
}
