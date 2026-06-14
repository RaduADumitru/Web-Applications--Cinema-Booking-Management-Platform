package com.awbd.cinema.services.OfferService;

import com.awbd.cinema.DTOs.OfferDTOs.OfferDTO;
import com.awbd.cinema.DTOs.OfferDTOs.SaveOfferDTO;
import com.awbd.cinema.utils.RestPage;
import org.springframework.data.domain.Pageable;

public interface OfferService {
    OfferDTO createOffer(SaveOfferDTO dto);
    RestPage<OfferDTO> getOffers(Pageable pageable);
    OfferDTO getOffer(Long id);
    OfferDTO updateOffer(Long id, SaveOfferDTO dto);
    void deleteOffer(Long id);
}
