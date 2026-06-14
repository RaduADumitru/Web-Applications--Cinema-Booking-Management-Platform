package com.awbd.cinema.services.OrderService;

import com.awbd.cinema.DTOs.OrderDTOs.CreateOrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.DiscountPreviewDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderDTO;
import com.awbd.cinema.utils.RestPage;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    OrderDTO createOrder(CreateOrderDTO dto, Long userId);
    RestPage<OrderDTO> getOrders(String status, Pageable pageable);
    RestPage<OrderDTO> getMyOrders(Long userId, Pageable pageable);
    RestPage<OrderDTO> getMyPastOrders(Long userId, Pageable pageable);
    DiscountPreviewDTO getDiscountPreview(Long userId);
    OrderDTO getOrder(Long id);
    OrderDTO payOrder(Long id);
    OrderDTO cancelOrder(Long id);
    void deleteOrder(Long id);
}
