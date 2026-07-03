package com.yusufnazim.deliverydispatch.dispatch;

import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class HaversineDistanceCalculator {

    private static final double EARTH_RADIUS_KILOMETERS = 6371.0088;

    public double distanceInKilometers(
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            BigDecimal destinationLatitude,
            BigDecimal destinationLongitude) {
        Objects.requireNonNull(originLatitude, "originLatitude must not be null");
        Objects.requireNonNull(originLongitude, "originLongitude must not be null");
        Objects.requireNonNull(destinationLatitude, "destinationLatitude must not be null");
        Objects.requireNonNull(destinationLongitude, "destinationLongitude must not be null");

        double originLatitudeRadians = Math.toRadians(originLatitude.doubleValue());
        double destinationLatitudeRadians = Math.toRadians(destinationLatitude.doubleValue());
        double latitudeDifferenceRadians =
                Math.toRadians(destinationLatitude.subtract(originLatitude).doubleValue());
        double longitudeDifferenceRadians =
                Math.toRadians(destinationLongitude.subtract(originLongitude).doubleValue());

        double haversine = Math.pow(Math.sin(latitudeDifferenceRadians / 2), 2)
                + Math.cos(originLatitudeRadians)
                * Math.cos(destinationLatitudeRadians)
                * Math.pow(Math.sin(longitudeDifferenceRadians / 2), 2);
        double centralAngle = 2 * Math.asin(Math.sqrt(haversine));

        return EARTH_RADIUS_KILOMETERS * centralAngle;
    }
}
