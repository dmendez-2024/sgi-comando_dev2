package com.cajamarca.sgi.comando.patrols;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PatrolDistanceCalculatorTest {
    final Instant start=Instant.parse("2026-11-11T10:00:00Z");
    final PatrolDistanceCalculator.Policy policy=new PatrolDistanceCalculator.Policy(20,180);
    PatrolDistanceCalculator.Sample at(int seconds,double longitude,Double accuracy,Double speed) {
        return new PatrolDistanceCalculator.Sample(start.plusSeconds(seconds),0,longitude,accuracy,speed);
    }
    @Test void measuresMetersAndOrdersSamplesWithoutPostgis() {
        var result=PatrolDistanceCalculator.calculate(List.of(at(120,0.0009,5d,null),at(0,0,5d,null)),start,start.plusSeconds(120),policy);
        assertEquals(100.075,result.meters(),0.01);assertEquals(1,result.acceptedSegments());
    }
    @Test void excludesSpeedAboveFiveAndReportedVehicleSpeed() {
        assertEquals(0,PatrolDistanceCalculator.calculate(List.of(at(0,0,5d,null),at(120,0.002,5d,null)),start,start.plusSeconds(120),policy).acceptedSegments());
        assertEquals(0,PatrolDistanceCalculator.calculate(List.of(at(0,0,5d,3d),at(120,0.0005,5d,null)),start,start.plusSeconds(120),policy).acceptedSegments());
    }
    @Test void poorOrMissingAccuracyBreaksContinuityInsteadOfBridging() {
        var points=List.of(at(0,0,5d,null),at(60,0.0005,100d,null),at(120,0.0009,5d,null));
        assertEquals(0,PatrolDistanceCalculator.calculate(points,start,start.plusSeconds(120),policy).meters());
        assertEquals(0,PatrolDistanceCalculator.calculate(List.of(at(0,0,null,null),at(120,0.0009,5d,null)),start,start.plusSeconds(120),policy).meters());
    }
    @Test void doesNotCountGapsOutsideWindowOrZeroTime() {
        assertEquals(0,PatrolDistanceCalculator.calculate(List.of(at(0,0,5d,null),at(600,0.0009,5d,null)),start,start.plusSeconds(600),policy).meters());
        assertEquals(0,PatrolDistanceCalculator.calculate(List.of(at(-60,0,5d,null),at(60,0.0009,5d,null)),start,start.plusSeconds(120),policy).meters());
        assertEquals(0,PatrolDistanceCalculator.calculate(List.of(at(0,0,5d,null),at(0,0.0009,5d,null)),start,start.plusSeconds(120),policy).meters());
    }
    @Test void requiresExplicitPositiveQualityLimits() {
        assertThrows(IllegalArgumentException.class,()->new PatrolDistanceCalculator.Policy(0,180));
        assertThrows(IllegalArgumentException.class,()->new PatrolDistanceCalculator.Policy(20,0));
    }
}
