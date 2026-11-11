package com.traveleasy.bus;

import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.model.Seat;
import com.traveleasy.bus.repository.BusRepository;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.repository.SeatRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@EnableScheduling
@SpringBootApplication
public class BusServiceApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(BusServiceApplication.class, args);
    }

    private static void loadDotEnv() {
        String[] possiblePaths = {".env", "../.env", "../../.env"};
        for (String p : possiblePaths) {
            Path path = Paths.get(p);
            if (Files.exists(path)) {
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        int eqIdx = line.indexOf('=');
                        if (eqIdx > 0) {
                            String key = line.substring(0, eqIdx).trim();
                            String value = line.substring(eqIdx + 1).trim();
                            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                                value = value.substring(1, value.length() - 1);
                            } else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                                value = value.substring(1, value.length() - 1);
                            }
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, value);
                            }
                        }
                    }
                    break;
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Bean
    public org.springframework.web.client.RestTemplate restTemplate() {
        return new org.springframework.web.client.RestTemplate();
    }

    @Bean
    public CommandLineRunner initBusData(BusRepository busRepository, BusScheduleRepository scheduleRepository, SeatRepository seatRepository) {
        return args -> {
            if (busRepository.count() == 0) {
                Bus bus1 = busRepository.save(Bus.builder()
                        .operatorId(2L)
                        .operatorName("Orange Tours & Travels")
                        .busNumber("AP39TV1234")
                        .busType("AC Sleeper (2+1)")
                        .totalSeats(30)
                        .amenities("Wi-Fi, Charging Point, Water Bottle, Pillow")
                        .build());

                Bus bus2 = busRepository.save(Bus.builder()
                        .operatorId(2L)
                        .operatorName("VRL Travels")
                        .busNumber("KA01F5678")
                        .busType("Volvo Multi-Axle AC Semi-Sleeper")
                        .totalSeats(36)
                        .amenities("Personal TV, Reading Light, Emergency Button")
                        .build());

                // Seed Schedule
                BusSchedule sched1 = scheduleRepository.save(BusSchedule.builder()
                        .busId(bus1.getId())
                        .operatorName(bus1.getOperatorName())
                        .busNumber(bus1.getBusNumber())
                        .busType(bus1.getBusType())
                        .origin("Vijayawada")
                        .destination("Hyderabad")
                        .travelDate(LocalDate.now().plusDays(1))
                        .departureTime("22:30")
                        .arrivalTime("05:30")
                        .fare(850.0)
                        .availableSeats(30)
                        .boardingPoints("Benz Circle, Varun Motors, Auto Nagar")
                        .droppingPoints("LB Nagar, Ameerpet, Miyapur")
                        .build());

                BusSchedule sched2 = scheduleRepository.save(BusSchedule.builder()
                        .busId(bus2.getId())
                        .operatorName(bus2.getOperatorName())
                        .busNumber(bus2.getBusNumber())
                        .busType(bus2.getBusType())
                        .origin("Vijayawada")
                        .destination("Bangalore")
                        .travelDate(LocalDate.now().plusDays(1))
                        .departureTime("20:00")
                        .arrivalTime("07:00")
                        .fare(1250.0)
                        .availableSeats(36)
                        .boardingPoints("PNBS Bus Stand, Benz Circle")
                        .droppingPoints("Hebbal, Majestic, Electronic City")
                        .build());

                // Seed Seats for sched1
                List<Seat> seats = new ArrayList<>();
                for (int i = 1; i <= 30; i++) {
                    String seatNum = (i <= 15 ? "L" : "U") + (i <= 15 ? i : (i - 15));
                    seats.add(Seat.builder()
                            .scheduleId(sched1.getId())
                            .seatNumber(seatNum)
                            .seatType("Sleeper")
                            .price(850.0)
                            .status("AVAILABLE")
                            .build());
                }
                seatRepository.saveAll(seats);
            }
        };
    }
}
