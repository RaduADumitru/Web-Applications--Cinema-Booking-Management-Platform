package com.awbd.cinema;

import com.awbd.cinema.DTOs.OrderDTOs.CreateOrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderItemDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.clients.UserServiceClient;
import com.awbd.cinema.entities.Ticket;
import com.awbd.cinema.entities.TicketInfo;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.enums.TicketType;
import com.awbd.cinema.repositories.TicketInfoRepository;
import com.awbd.cinema.repositories.TicketRepository;
import com.awbd.cinema.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class OrderIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketInfoRepository ticketInfoRepository;

    @MockitoBean private UserServiceClient userServiceClient;

    private final Long userId = 1L;
    private CustomUserDetails principal;
    private Ticket availableTicket;

    private Ticket seedTicket(Long seatId) {
        return ticketRepository.save(Ticket.builder()
                .isAvailable(true)
                .seatId(seatId).roomId(10L).screenSessionId(20L)
                .seatRow(1).seatNumber(seatId.intValue()).seatZone("A")
                .extraFee(BigDecimal.ZERO).extraPoints(0).sessionPoints(0)
                .movieTitle("Integration Test Movie")
                .sessionDate(LocalDate.now().plusDays(7))
                .sessionStartTime(LocalTime.of(18, 0))
                .build());
    }

    @BeforeEach
    void setUp() {
        principal = new CustomUserDetails(userId, "order_user", "password", Role.USER, null);
        availableTicket = seedTicket(1L);
        ticketInfoRepository.save(TicketInfo.builder().type(TicketType.ADULT).price(BigDecimal.valueOf(15.00)).build());

        when(userServiceClient.getLoyaltyPoints(anyLong())).thenReturn(new LoyaltyPointsDTO(userId, 0));
        when(userServiceClient.updateLoyaltyPoints(anyLong(), any())).thenReturn(new LoyaltyPointsDTO(userId, 0));
    }

    @Test
    @DisplayName("End-to-End: Create, Retrieve, Pay, and Cancel an Order (booking-local)")
    void testOrderFlow() throws Exception {
        OrderItemDTO orderItem = new OrderItemDTO(availableTicket.getId(), TicketType.ADULT);
        CreateOrderDTO createOrderDTO = new CreateOrderDTO(List.of(orderItem), false);

        String createResp = mockMvc.perform(post("/orders")
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.price").value(15.00))
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(createResp).get("id").asLong();

        mockMvc.perform(get("/tickets/{id}", availableTicket.getId()).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(false));

        mockMvc.perform(get("/orders/{id}", orderId).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(patch("/orders/{id}/pay", orderId).with(user(principal)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(get("/orders/my").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(orderId))
                .andExpect(jsonPath("$.content[0].status").value("PAID"));

        mockMvc.perform(patch("/orders/{id}/cancel", orderId).with(user(principal)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Paid orders cannot be cancelled."));

        Ticket anotherTicket = seedTicket(2L);
        OrderItemDTO anotherItem = new OrderItemDTO(anotherTicket.getId(), TicketType.ADULT);
        CreateOrderDTO anotherOrder = new CreateOrderDTO(List.of(anotherItem), false);

        String anotherResp = mockMvc.perform(post("/orders")
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(anotherOrder)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        Long pendingOrderId = objectMapper.readTree(anotherResp).get("id").asLong();

        mockMvc.perform(patch("/orders/{id}/cancel", pendingOrderId).with(user(principal)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/tickets/{id}", anotherTicket.getId()).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(true));

        mockMvc.perform(delete("/orders/{id}", orderId).with(user("staff").roles("STAFF")).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/orders/{id}", orderId).with(user(principal)))
                .andExpect(status().isNotFound());
    }
}
