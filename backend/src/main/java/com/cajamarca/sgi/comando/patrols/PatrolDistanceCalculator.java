package com.cajamarca.sgi.comando.patrols;

import java.time.Instant;
import java.util.*;

/** Estimación geodésica entre muestras, sin depender de PostGIS. No calcula Impulsos. */
public final class PatrolDistanceCalculator {
    public static final double MAX_SPEED_KMH = 5;
    public record Sample(Instant at, double latitude, double longitude, Double accuracyM, Double speedMps) {}
    public record Policy(double maxAccuracyM, long maxGapSeconds) {
        public Policy {
            if (!Double.isFinite(maxAccuracyM) || maxAccuracyM<=0 || maxGapSeconds<=0)
                throw new IllegalArgumentException("Límites GPS inválidos");
        }
    }
    public record Result(double meters, int acceptedSegments, int rejectedSegments, int samples) {}

    public static Result calculate(List<Sample> input, Instant start, Instant end, Policy policy) {
        if (start==null || end==null || end.isBefore(start)) throw new IllegalArgumentException("Ventana inválida");
        var ordered = new ArrayList<>(input);
        ordered.sort(Comparator.comparing(Sample::at,Comparator.nullsFirst(Comparator.naturalOrder())));
        Sample previous=null;double total=0;int accepted=0,rejected=0;
        for (Sample current:ordered) {
            // Una muestra mala rompe la continuidad: nunca se puentea el tramo desconocido.
            if (!valid(current,policy) || current.at().isBefore(start) || current.at().isAfter(end)) {
                previous=null;rejected++;continue;
            }
            if (previous!=null) {
                double seconds=(current.at().toEpochMilli()-previous.at().toEpochMilli())/1000d;
                double meters=meters(previous,current);
                if (seconds>0 && seconds<=policy.maxGapSeconds() && meters/seconds*3.6<=MAX_SPEED_KMH
                    && slow(previous) && slow(current)) {total+=meters;accepted++;}
                else rejected++;
            }
            previous=current;
        }
        return new Result(total,accepted,rejected,input.size());
    }

    private static boolean valid(Sample s,Policy p) {
        return s.at()!=null && Double.isFinite(s.latitude()) && Math.abs(s.latitude())<=90
            && Double.isFinite(s.longitude()) && Math.abs(s.longitude())<=180
            && s.accuracyM()!=null && Double.isFinite(s.accuracyM()) && s.accuracyM()>=0 && s.accuracyM()<=p.maxAccuracyM()
            && (s.speedMps()==null || Double.isFinite(s.speedMps()) && s.speedMps()>=0);
    }
    private static boolean slow(Sample s) {return s.speedMps()==null || s.speedMps()*3.6<=MAX_SPEED_KMH;}
    static double meters(Sample a,Sample b) {
        double lat=Math.toRadians(b.latitude()-a.latitude()),lon=Math.toRadians(b.longitude()-a.longitude());
        double h=Math.pow(Math.sin(lat/2),2)+Math.cos(Math.toRadians(a.latitude()))*Math.cos(Math.toRadians(b.latitude()))*Math.pow(Math.sin(lon/2),2);
        return 6371000d*2*Math.asin(Math.sqrt(Math.max(0,Math.min(1,h))));
    }
}
