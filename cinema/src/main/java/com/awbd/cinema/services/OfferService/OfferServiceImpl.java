package com.awbd.cinema.services.OfferService;

import com.awbd.cinema.DTOs.OfferDTOs.OfferDTO;
import com.awbd.cinema.DTOs.OfferDTOs.SaveOfferDTO;
import com.awbd.cinema.entities.Offer;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.OfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OfferServiceImpl implements OfferService {

    private final OfferRepository offerRepository;

    @Override
    @Transactional
    public OfferDTO createOffer(SaveOfferDTO dto) {
        if (offerRepository.existsByDay(dto.day())) {
            throw new AlreadyExistsException("An offer for " + dto.day() + " already exists.");
        }
        Offer offer = Offer.builder()
                .day(dto.day())
                .percent(dto.percent())
                .build();
        return OfferDTO.from(offerRepository.save(offer));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfferDTO> getOffers() {
        return offerRepository.findAll().stream().map(OfferDTO::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OfferDTO getOffer(Long id) {
        return offerRepository.findById(id)
                .map(OfferDTO::from)
                .orElseThrow(() -> new NotFoundException("Offer not found with id: " + id));
    }

    @Override
    @Transactional
    public OfferDTO updateOffer(Long id, SaveOfferDTO dto) {
        Offer offer = offerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Offer not found with id: " + id));

        if (!offer.getDay().equals(dto.day()) && offerRepository.existsByDay(dto.day())) {
            throw new AlreadyExistsException("An offer for " + dto.day() + " already exists.");
        }

        offer.setDay(dto.day());
        offer.setPercent(dto.percent());
        return OfferDTO.from(offerRepository.save(offer));
    }

    @Override
    @Transactional
    public void deleteOffer(Long id) {
        if (!offerRepository.existsById(id)) {
            throw new NotFoundException("Offer not found with id: " + id);
        }
        offerRepository.deleteById(id);
    }
}
