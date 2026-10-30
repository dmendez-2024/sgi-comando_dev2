package com.cajamarca.sgi.comando.dashboard;

import jakarta.ws.rs.BadRequestException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DashboardConsignmentPeriodTest {
    private final LocalDate week = LocalDate.of(2026, 10, 26);
    private final LocalDate today = LocalDate.of(2026, 10, 28);

    @Test void thirtyDaysIncludeTodayAndThePreviousTwentyNineDays() {
        var window = DashboardResource.consignmentWindow(week, "30d", today);
        assertEquals(Instant.parse("2026-09-29T05:00:00Z"), window.from());
        assertEquals(Instant.parse("2026-10-29T05:00:00Z"), window.to());
    }

    @Test void pastWeekEndsOnItsSunday() {
        var window = DashboardResource.consignmentWindow(week.minusWeeks(1), "7d", today);
        assertEquals(Instant.parse("2026-10-19T05:00:00Z"), window.from());
        assertEquals(Instant.parse("2026-10-26T05:00:00Z"), window.to());
    }

    @Test void sixMonthsUseCalendarMonths() {
        var window = DashboardResource.consignmentWindow(week, "6m", today);
        assertEquals(Instant.parse("2026-04-29T05:00:00Z"), window.from());
        assertEquals(Instant.parse("2026-10-29T05:00:00Z"), window.to());
    }

    @Test void omittedPeriodKeepsExistingWeeklyConsumers() {
        var window = DashboardResource.consignmentWindow(week, null, today);
        assertEquals(Instant.parse("2026-10-26T05:00:00Z"), window.from());
        assertEquals(Instant.parse("2026-11-02T05:00:00Z"), window.to());
        assertThrows(BadRequestException.class,
            () -> DashboardResource.consignmentWindow(week, "invalid", today));
    }
}
