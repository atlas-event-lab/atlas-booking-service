package com.atlas.booking.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A person travelling under a Booking.
 * Required fields follow domain/traveler.md validation rules.
 */
@Entity
@Table(name = "travelers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Traveler {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID travelerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    /** ISO-3166 country code. */
    @Column(nullable = false, length = 3)
    private String nationality;

    @Column(name = "document_type", length = 50)
    private String documentType;

    @Column(name = "document_number", nullable = false, length = 100)
    private String documentNumber;

    @Column(length = 255)
    private String email;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    public Traveler(
            UUID travelerId,
            String firstName,
            String lastName,
            LocalDate dateOfBirth,
            String nationality,
            String documentType,
            String documentNumber,
            String email,
            String phoneNumber) {
        this.travelerId = travelerId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.nationality = nationality;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }

    /** Called only by {@link Booking#addTraveler(Traveler)}. */
    void setBooking(Booking booking) {
        this.booking = booking;
    }
}
