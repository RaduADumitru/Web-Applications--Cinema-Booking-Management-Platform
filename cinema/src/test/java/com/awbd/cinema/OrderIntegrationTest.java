package com.awbd.cinema;

import com.awbd.cinema.DTOs.OrderDTOs.CreateOrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderItemDTO;
import com.awbd.cinema.entities.*;
import com.awbd.cinema.enums.*;
import com.awbd.cinema.repositories.*;
import com.awbd.cinema.security.CustomUserDetails;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class OrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private SessionInfoRepository sessionInfoRepository;

    @Autowired
    private ScreenSessionRepository screenSessionRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketInfoRepository ticketInfoRepository;

    private User testUser;
    private Room room;
    private Ticket availableTicket;
    private TicketInfo adultTicketInfo;

    @BeforeEach
    void setUp() {
        // 1. Create a User
        testUser = User.builder()
                .username("order_user")
                .email("order_user@example.com")
                .password("hashed_password") // Password not used for direct auth in this test
                .firstName("Order")
                .lastName("User")
                .phoneNumber("+40712345678")
                .role(Role.USER)
                .loyaltyPoints(0)
                .build();
        testUser = userRepository.save(testUser);

        // 2. Create a Movie
        Movie movie = Movie.builder()
                .id(999999L)
                .title("Integration Test Movie")
                .description("A movie for integration tests.")
                .releaseDate(LocalDateTime.now())
                .rating(8.0)
                .duration(120)
                .ageRating("PG-13")
                .genres(new ArrayList<>())
                .build();
        movie = movieRepository.save(movie);

        // 3. Create a Room
        room = Room.builder()
                .name("Test Room")
                .type(RoomType.NORMAL)
                .floor(1)
                .seats(new ArrayList<>())
                .screenSessions(new ArrayList<>())
                .build();
        room = roomRepository.save(room);

        // 4. Create a Seat
        Seat seat = Seat.builder()
                .row(1)
                .number(1)
                .zone(SeatZone.A)
                .build();
        seat = seatRepository.save(seat);

        // Update room to include the seat
        room.setSeats(new ArrayList<>(List.of(seat)));
        roomRepository.save(room);

        // 5. Create a SessionInfo
        SessionInfo sessionInfo = SessionInfo.builder()
                .format(Format.TWO_D)
                .points(10)
                .build();
        sessionInfo = sessionInfoRepository.save(sessionInfo);

        // 6. Create a ScreenSession
        ScreenSession screenSession = ScreenSession.builder()
                .movie(movie)
                .sessionInfo(sessionInfo)
                .date(LocalDate.now().plusDays(7))
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(20, 0))
                .build();
        screenSession = screenSessionRepository.save(screenSession);

        // Update room to include the screen session
        room.setScreenSessions(new ArrayList<>(List.of(screenSession)));
        roomRepository.save(room);

        // 7. Create a Ticket
        availableTicket = Ticket.builder()
                .isAvailable(true)
                .seat(seat)
                .room(room)
                .screenSession(screenSession)
                .build();
        availableTicket = ticketRepository.save(availableTicket);

        // 8. Create TicketInfo
        adultTicketInfo = TicketInfo.builder()
                .type(TicketType.ADULT)
                .price(BigDecimal.valueOf(15.00))
                .build();
        adultTicketInfo = ticketInfoRepository.save(adultTicketInfo);
    }

    @Test
    @DisplayName("End-to-End: Create, Retrieve, Pay, and Cancel an Order")
    void testOrderFlow() throws Exception {
        // 1. Create an Order
        OrderItemDTO orderItem = new OrderItemDTO(availableTicket.getId(), TicketType.ADULT);
        CreateOrderDTO createOrderDTO = new CreateOrderDTO(List.of(orderItem), false); // No loyalty discount

        String createOrderResponse = mockMvc.perform(post("/orders")
                        .with(user(new CustomUserDetails(testUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.price").value(15.00))
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(createOrderResponse).get("id").asLong();

        // Verify ticket is no longer available
        mockMvc.perform(get("/tickets/{id}", availableTicket.getId())
                        .with(user(new CustomUserDetails(testUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(false));

        // 2. Retrieve the created Order
        mockMvc.perform(get("/orders/{id}", orderId)
                        .with(user(new CustomUserDetails(testUser)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // 3. Pay the Order
        mockMvc.perform(patch("/orders/{id}/pay", orderId)
                        .with(user(new CustomUserDetails(testUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        // 4. Retrieve User's Orders
        mockMvc.perform(get("/orders/my")
                        .with(user(new CustomUserDetails(testUser)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(orderId))
                .andExpect(jsonPath("$.content[0].status").value("PAID"));

        // 5. Cancel the Order (should fail as it's already PAID)
        mockMvc.perform(patch("/orders/{id}/cancel", orderId)
                        .with(user(new CustomUserDetails(testUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Paid orders cannot be cancelled."));

        // Create another pending order to test cancellation
        Ticket anotherTicket = Ticket.builder()
                .isAvailable(true)
                .seat(seatRepository.save(Seat.builder().row(2).number(2).zone(SeatZone.A).build()))
                .room(roomRepository.findById(room.getId()).get())
                .screenSession(screenSessionRepository.findById(availableTicket.getScreenSession().getId()).get())
                .build();
        anotherTicket = ticketRepository.save(anotherTicket);

        OrderItemDTO anotherOrderItem = new OrderItemDTO(anotherTicket.getId(), TicketType.ADULT);
        CreateOrderDTO anotherCreateOrderDTO = new CreateOrderDTO(List.of(anotherOrderItem), false);

        String anotherCreateOrderResponse = mockMvc.perform(post("/orders")
                        .with(user(new CustomUserDetails(testUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(anotherCreateOrderDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        Long pendingOrderId = objectMapper.readTree(anotherCreateOrderResponse).get("id").asLong();

        // 6. Cancel a PENDING Order
        mockMvc.perform(patch("/orders/{id}/cancel", pendingOrderId)
                        .with(user(new CustomUserDetails(testUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Verify ticket is available again
        mockMvc.perform(get("/tickets/{id}", anotherTicket.getId())
                        .with(user(new CustomUserDetails(testUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(true));

        // 7. Delete an Order (soft delete)
        mockMvc.perform(delete("/orders/{id}", orderId)
                        .with(user("staff").roles("STAFF"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // Verify deletion (should return 404 Not Found for active orders)
        mockMvc.perform(get("/orders/{id}", orderId)
                        .with(user(new CustomUserDetails(testUser)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
