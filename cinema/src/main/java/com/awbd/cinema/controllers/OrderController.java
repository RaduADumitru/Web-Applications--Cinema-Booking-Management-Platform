package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.OrderDTOs.CreateOrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.DiscountPreviewDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderDTO;
import com.awbd.cinema.security.CustomUserDetails;
import com.awbd.cinema.services.OrderService.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderDTO> createOrder(
            @Valid @RequestBody CreateOrderDTO dto,
            Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getId();
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(dto, userId));
    }

    @GetMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<List<OrderDTO>> getOrders(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(orderService.getOrders(status));
    }

    @GetMapping("/my")
    public ResponseEntity<List<OrderDTO>> getMyOrders(Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getId();
        return ResponseEntity.ok(orderService.getMyOrders(userId));
    }

    @GetMapping("/my/discount-preview")
    public ResponseEntity<DiscountPreviewDTO> getDiscountPreview(Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getId();
        return ResponseEntity.ok(orderService.getDiscountPreview(userId));
    }

    @GetMapping("/my/past")
    public ResponseEntity<List<OrderDTO>> getMyPastOrders(Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getId();
        return ResponseEntity.ok(orderService.getMyPastOrders(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @PatchMapping("/{id}/pay")
    public ResponseEntity<OrderDTO> payOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.payOrder(id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderDTO> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
