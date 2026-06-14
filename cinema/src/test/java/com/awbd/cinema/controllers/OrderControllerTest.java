package com.awbd.cinema.controllers;


import com.awbd.cinema.DTOs.OrderDTOs.CreateOrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.DiscountPreviewDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderItemDTO;
import com.awbd.cinema.enums.OrderStatus;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.enums.TicketType;
import com.awbd.cinema.services.OrderService.OrderService;
import com.awbd.cinema.utils.RestPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private OrderService orderService;

    private OrderDTO createSampleOrderDTO() {
        return new OrderDTO(
                1L, LocalDateTime.now(), OrderStatus.PENDING, null, null,
                BigDecimal.valueOf(50.00), 10, 0, BigDecimal.ZERO,
                1L, List.of(101L), null, null
        );
    }

    // ==========================================
    // POST /orders
    // ==========================================
    @Nested
    @DisplayName("POST /orders")
    class CreateOrder {

        @Test
        @DisplayName("Should return 201 Created when order payload is valid")
        void createOrder_ValidPayload_ReturnsCreated() throws Exception {
            loginAsDefaultUser();
            CreateOrderDTO dto = new CreateOrderDTO(List.of(new OrderItemDTO(101L, TicketType.ADULT)), false);
            OrderDTO responseDto = createSampleOrderDTO();

            when(orderService.createOrder(any(CreateOrderDTO.class), eq(1L))).thenReturn(responseDto);

            mockMvc.perform(post("/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1L))
                    .andExpect(jsonPath("$.price").value(50.00))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("Should return 422 Unprocessable Content when validation fails (empty items)")
        void createOrder_InvalidPayload_ReturnsUnprocessableContent() throws Exception {
            loginAsDefaultUser();
            CreateOrderDTO dto = new CreateOrderDTO(List.of(), false); // Empty items list triggers @NotEmpty

            mockMvc.perform(post("/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isUnprocessableContent());
        }
    }

    // ==========================================
    // GET /orders (Staff Access Required)
    // ==========================================
    @Nested
    @DisplayName("GET /orders")
    class GetOrders {

        @Test
        @DisplayName("Should return 200 OK when user is STAFF")
        void getOrders_StaffUser_ReturnsOk() throws Exception {
            loginAs(2L, "staff_member", Role.STAFF);
            OrderDTO orderDto = createSampleOrderDTO();
            when(orderService.getOrders(eq("PENDING"), any(Pageable.class)))
                    .thenReturn(new RestPage<>(new PageImpl<>(List.of(orderDto))));

            mockMvc.perform(get("/orders")
                            .param("status", OrderStatus.PENDING.toString())
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(1L));
        }

        @Test
        @DisplayName("Should return 403 Forbidden when user is standard USER")
        void getOrders_RegularUser_ReturnsForbidden() throws Exception {
            loginAsDefaultUser(); // Authenticated as Role.USER

            mockMvc.perform(get("/orders")
                            .param("status", "pending"))
                    .andExpect(status().isForbidden());
        }
    }

    // ==========================================
    // GET /orders/my & variants
    // ==========================================
    @Nested
    @DisplayName("GET /orders/my/** endpoints")
    class UserOrderEndpoints {

        @Test
        @DisplayName("GET /orders/my should return current user's orders page")
        void getMyOrders_ReturnsOk() throws Exception {
            loginAsDefaultUser();
            OrderDTO orderDto = createSampleOrderDTO();
            when(orderService.getMyOrders(eq(1L), any(Pageable.class)))
                    .thenReturn(new RestPage<>(new PageImpl<>(List.of(orderDto))));

            mockMvc.perform(get("/orders/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].userId").value(1L));
        }

        @Test
        @DisplayName("GET /orders/my/discount-preview should return discount values")
        void getDiscountPreview_ReturnsOk() throws Exception {
            loginAsDefaultUser();
            DiscountPreviewDTO preview = new DiscountPreviewDTO(100, BigDecimal.valueOf(10.00));
            when(orderService.getDiscountPreview(1L)).thenReturn(preview);

            mockMvc.perform(get("/orders/my/discount-preview"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loyaltyPoints").value(100))
                    .andExpect(jsonPath("$.potentialDiscount").value(10.00));
        }

        @Test
        @DisplayName("GET /orders/my/past should return past historic records")
        void getMyPastOrders_ReturnsOk() throws Exception {
            loginAsDefaultUser();
            OrderDTO historicOrder = createSampleOrderDTO();
            when(orderService.getMyPastOrders(eq(1L), any(Pageable.class)))
                    .thenReturn(new RestPage<>(new PageImpl<>(List.of(historicOrder))));

            mockMvc.perform(get("/orders/my/past"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }

    // ==========================================
    // GET & PATCH Single Order Operations
    // ==========================================
    @Nested
    @DisplayName("GET & PATCH /{id} operations")
    class SingleOrderOperations {

        @Test
        @DisplayName("GET /orders/{id} should return selected entity details")
        void getOrder_ReturnsOk() throws Exception {
            loginAsDefaultUser();
            OrderDTO orderDto = createSampleOrderDTO();
            when(orderService.getOrder(1L)).thenReturn(orderDto);

            mockMvc.perform(get("/orders/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1L));
        }

        @Test
        @DisplayName("PATCH /orders/{id}/pay should advance order to PAID status")
        void payOrder_ReturnsOk() throws Exception {
            loginAsDefaultUser();
            OrderDTO paidOrder = new OrderDTO(
                    1L, LocalDateTime.now(), OrderStatus.PAID, LocalDateTime.now(), null,
                    BigDecimal.valueOf(50.00), 10, 0, BigDecimal.ZERO,
                    1L, List.of(101L), null, null
            );
            when(orderService.payOrder(1L)).thenReturn(paidOrder);

            mockMvc.perform(patch("/orders/1/pay").with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PAID"));
        }

        @Test
        @DisplayName("PATCH /orders/{id}/cancel should change status state to CANCELLED")
        void cancelOrder_ReturnsOk() throws Exception {
            loginAsDefaultUser();
            OrderDTO cancelledOrder = createSampleOrderDTO();
            cancelledOrder = new OrderDTO(
                    1L, cancelledOrder.createdAt(), OrderStatus.CANCELLED, null, null,
                    cancelledOrder.price(), cancelledOrder.loyaltyPoints(), 0, BigDecimal.ZERO,
                    1L, List.of(101L), null, null
            );
            when(orderService.cancelOrder(1L)).thenReturn(cancelledOrder);

            mockMvc.perform(patch("/orders/1/cancel").with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }
    }

    // ==========================================
    // DELETE /orders/{id} (Staff Access Required)
    // ==========================================
    @Nested
    @DisplayName("DELETE /orders/{id}")
    class DeleteOrder {

        @Test
        @DisplayName("Should return 24 No Content when executing request as STAFF")
        void deleteOrder_StaffUser_ReturnsNoContent() throws Exception {
            loginAs(2L, "staff_admin", Role.STAFF);
            doNothing().when(orderService).deleteOrder(1L);

            mockMvc.perform(delete("/orders/1").with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Should return 403 Forbidden when executing request as standard USER")
        void deleteOrder_RegularUser_ReturnsForbidden() throws Exception {
            loginAsDefaultUser();

            mockMvc.perform(delete("/orders/1").with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }
}