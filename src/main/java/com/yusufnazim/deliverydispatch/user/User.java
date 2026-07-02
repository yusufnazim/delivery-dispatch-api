package com.yusufnazim.deliverydispatch.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "courier_display_name", length = 120)
    private String courierDisplayName;

    @Column(name = "courier_phone_number", length = 32)
    private String courierPhoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "courier_vehicle_type", length = 32)
    private CourierVehicleType courierVehicleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "courier_availability_status", length = 32)
    private CourierAvailabilityStatus courierAvailabilityStatus;

    @Column(name = "courier_latitude", precision = 8, scale = 6)
    private BigDecimal courierLatitude;

    @Column(name = "courier_longitude", precision = 9, scale = 6)
    private BigDecimal courierLongitude;

    public User(String email, String passwordHash, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        if (role == Role.COURIER) {
            this.courierAvailabilityStatus = CourierAvailabilityStatus.UNAVAILABLE;
        }
    }

    public void updateCourierProfile(
            String courierDisplayName,
            String courierPhoneNumber,
            CourierVehicleType courierVehicleType) {
        this.courierDisplayName = courierDisplayName;
        this.courierPhoneNumber = courierPhoneNumber;
        this.courierVehicleType = courierVehicleType;
    }

    public void updateCourierAvailabilityStatus(CourierAvailabilityStatus courierAvailabilityStatus) {
        this.courierAvailabilityStatus = courierAvailabilityStatus;
    }

    public void updateCourierLocation(BigDecimal courierLatitude, BigDecimal courierLongitude) {
        this.courierLatitude = courierLatitude;
        this.courierLongitude = courierLongitude;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
