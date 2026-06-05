package com.awbd.cinema.services.TicketInfoService;

import com.awbd.cinema.DTOs.TicketInfoDTOs.SaveTicketInfoDTO;
import com.awbd.cinema.DTOs.TicketInfoDTOs.TicketInfoDTO;
import com.awbd.cinema.entities.TicketInfo;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.TicketInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketInfoServiceImpl implements TicketInfoService {

    private final TicketInfoRepository ticketInfoRepository;

    @Transactional
    public TicketInfoDTO createTicketInfo(SaveTicketInfoDTO dto) {
        if (ticketInfoRepository.findByType(dto.type()).isPresent()) {
            throw new AlreadyExistsException("Ticket info for type '" + dto.type() + "' already exists.");
        }
        TicketInfo ticketInfo = TicketInfo.builder()
                .type(dto.type())
                .price(dto.price())
                .build();
        return TicketInfoDTO.from(ticketInfoRepository.save(ticketInfo));
    }

    @Transactional(readOnly = true)
    public List<TicketInfoDTO> getTicketInfos() {
        return ticketInfoRepository.findAll().stream()
                .map(TicketInfoDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketInfoDTO getTicketInfo(Long id) {
        return ticketInfoRepository.findById(id)
                .map(TicketInfoDTO::from)
                .orElseThrow(() -> new NotFoundException("Ticket info not found."));
    }

    @Transactional
    public TicketInfoDTO updateTicketInfo(Long id, SaveTicketInfoDTO dto) {
        TicketInfo ticketInfo = ticketInfoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket info not found."));

        if (ticketInfo.getType() != dto.type()) {
            ticketInfoRepository.findByType(dto.type()).ifPresent(existing -> {
                throw new AlreadyExistsException("Ticket info for type '" + dto.type() + "' already exists.");
            });
        }

        ticketInfo.setType(dto.type());
        ticketInfo.setPrice(dto.price());
        return TicketInfoDTO.from(ticketInfoRepository.save(ticketInfo));
    }

    @Transactional
    public void deleteTicketInfo(Long id) {
        if (!ticketInfoRepository.existsById(id)) {
            throw new NotFoundException("Ticket info not found.");
        }
        ticketInfoRepository.deleteById(id);
    }
}
