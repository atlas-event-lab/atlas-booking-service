package com.atlas.booking.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Traveler data submitted with a Create Booking request (booking.yaml Traveler,
 * domain/traveler.md validation rules).
 */
public record TravelerRequest(

        @NotBlank
        String firstName,

        @NotBlank
        String lastName,

        @NotNull
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate dateOfBirth,

        /** ISO-3166 country code. */
        @NotBlank
        @Size(min = 2, max = 3)
        String nationality,

        String documentType,

        @NotBlank
        String documentNumber,

        @Email
        String email,

        String phoneNumber
) {}
