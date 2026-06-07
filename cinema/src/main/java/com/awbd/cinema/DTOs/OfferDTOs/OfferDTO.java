package com.awbd.cinema.DTOs.OfferDTOs;

import com.awbd.cinema.entities.Offer;

import java.time.DayOfWeek;

public record OfferDTO(
        Long id,
        DayOfWeek day,
        Integer percent
) {
    public static OfferDTO from(Offer offer) {
        return new OfferDTO(offer.getId(), offer.getDay(), offer.getPercent());
    }
}
