package com.awbd.cinema.services.OrderService;

import com.awbd.cinema.DTOs.OrderDTOs.CreateOrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.DiscountPreviewDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    OrderDTO createOrder(CreateOrderDTO dto, Long userId);
    Page<OrderDTO> getOrders(String status, Pageable pageable);
    Page<OrderDTO> getMyOrders(Long userId, Pageable pageable);
    Page<OrderDTO> getMyPastOrders(Long userId, Pageable pageable);
    DiscountPreviewDTO getDiscountPreview(Long userId);
    OrderDTO getOrder(Long id);
    OrderDTO payOrder(Long id);
    OrderDTO cancelOrder(Long id);
    void deleteOrder(Long id);
}
