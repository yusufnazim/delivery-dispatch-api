package com.yusufnazim.deliverydispatch.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HaversineDistanceCalculatorTest {

    private final HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();

    @Test
    void returnsZeroForSamePoint() {
        double distance = calculator.distanceInKilometers(
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"));

        assertThat(distance).isZero();
    }

    @Test
    void calculatesKnownCrossBosphorusDistance() {
        double distance = calculator.distanceInKilometers(
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                new BigDecimal("40.991000"),
                new BigDecimal("29.024400"));

        assertThat(distance).isCloseTo(6.08, within(0.05));
    }

    @Test
    void calculatesKnownPickupToDropoffDistance() {
        double distance = calculator.distanceInKilometers(
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));

        assertThat(distance).isCloseTo(9.58, within(0.05));
    }

    @Test
    void calculatesSameDistanceInBothDirections() {
        BigDecimal taksimLatitude = new BigDecimal("41.036900");
        BigDecimal taksimLongitude = new BigDecimal("28.985000");
        BigDecimal kadikoyLatitude = new BigDecimal("40.991000");
        BigDecimal kadikoyLongitude = new BigDecimal("29.024400");

        double forwardDistance = calculator.distanceInKilometers(
                taksimLatitude,
                taksimLongitude,
                kadikoyLatitude,
                kadikoyLongitude);
        double reverseDistance = calculator.distanceInKilometers(
                kadikoyLatitude,
                kadikoyLongitude,
                taksimLatitude,
                taksimLongitude);

        assertThat(reverseDistance).isCloseTo(forwardDistance, within(0.000001));
    }
}
