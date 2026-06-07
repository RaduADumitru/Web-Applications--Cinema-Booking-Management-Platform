package com.awbd.cinema.services.OfferService;

import com.awbd.cinema.DTOs.OfferDTOs.OfferDTO;
import com.awbd.cinema.DTOs.OfferDTOs.SaveOfferDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OfferService {
    OfferDTO createOffer(SaveOfferDTO dto);
    Page<OfferDTO> getOffers(Pageable pageable);
    OfferDTO getOffer(Long id);
    OfferDTO updateOffer(Long id, SaveOfferDTO dto);
    void deleteOffer(Long id);
}
