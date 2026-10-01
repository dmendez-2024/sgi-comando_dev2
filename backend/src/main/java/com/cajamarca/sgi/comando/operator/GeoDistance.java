package com.cajamarca.sgi.comando.operator;

/** Distancia sobre la superficie terrestre (haversine), en metros. */
public final class GeoDistance {
    private GeoDistance() {}

    public static double meters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000, dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
