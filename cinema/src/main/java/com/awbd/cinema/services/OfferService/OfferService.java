package com.awbd.cinema.services.OfferService;

import com.awbd.cinema.DTOs.OfferDTOs.OfferDTO;
import com.awbd.cinema.DTOs.OfferDTOs.SaveOfferDTO;

import java.util.List;

public interface OfferService {
    OfferDTO createOffer(SaveOfferDTO dto);
    List<OfferDTO> getOffers();
    OfferDTO getOffer(Long id);
    OfferDTO updateOffer(Long id, SaveOfferDTO dto);
    void deleteOffer(Long id);
}
