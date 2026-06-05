package com.awbd.cinema.services.OrderService;

import com.awbd.cinema.DTOs.OrderDTOs.CreateOrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.DiscountPreviewDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderDTO;

import java.util.List;

public interface OrderService {
    OrderDTO createOrder(CreateOrderDTO dto, Long userId);
    List<OrderDTO> getOrders(String status);
    List<OrderDTO> getMyOrders(Long userId);
    List<OrderDTO> getMyPastOrders(Long userId);
    DiscountPreviewDTO getDiscountPreview(Long userId);
    OrderDTO getOrder(Long id);
    OrderDTO payOrder(Long id);
    OrderDTO cancelOrder(Long id);
    void deleteOrder(Long id);
}
