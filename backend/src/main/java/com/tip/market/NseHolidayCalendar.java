package com.tip.market;

import java.time.LocalDate;
import java.util.Set;

/**
 * NSE equity cash-market holidays used by {@link NseMarketClock}.
 * <p>
 * Fixed national holidays plus published specials for nearby years.
 * Expand as needed; missing entries fall back to weekday open hours only.
 */
public final class NseHolidayCalendar {

    /**
     * Known full-day NSE holidays (equity). Partial/special sessions not modeled.
     */
    private static final Set<LocalDate> HOLIDAYS = Set.of(
            // 2025
            LocalDate.of(2025, 2, 26),  // Mahashivratri
            LocalDate.of(2025, 3, 14),  // Holi
            LocalDate.of(2025, 3, 31),  // Id-Ul-Fitr (Ramadan Eid) — verify annually
            LocalDate.of(2025, 4, 10),  // Mahavir Jayanti
            LocalDate.of(2025, 4, 14),  // Dr. Ambedkar Jayanti
            LocalDate.of(2025, 4, 18),  // Good Friday
            LocalDate.of(2025, 5, 1),   // Maharashtra Day
            LocalDate.of(2025, 8, 15),  // Independence Day
            LocalDate.of(2025, 8, 27),  // Ganesh Chaturthi
            LocalDate.of(2025, 10, 2),  // Mahatma Gandhi Jayanti
            LocalDate.of(2025, 10, 21), // Diwali-Laxmi Pujan (Muhurat may apply)
            LocalDate.of(2025, 10, 22), // Diwali-Balipratipada
            LocalDate.of(2025, 11, 5),  // Prakash Gurpurb Sri Guru Nanak Dev
            LocalDate.of(2025, 12, 25), // Christmas
            // 2026
            LocalDate.of(2026, 1, 26),  // Republic Day
            LocalDate.of(2026, 3, 3),   // Holi (approx — confirm vs NSE circular)
            LocalDate.of(2026, 3, 26),  // Ram Navami (approx)
            LocalDate.of(2026, 3, 31),  // Mahavir Jayanti (approx)
            LocalDate.of(2026, 4, 3),   // Good Friday
            LocalDate.of(2026, 4, 14),  // Dr. Ambedkar Jayanti
            LocalDate.of(2026, 5, 1),   // Maharashtra Day
            LocalDate.of(2026, 5, 28),  // Bakri Id (approx)
            LocalDate.of(2026, 6, 26),  // Muharram (approx)
            LocalDate.of(2026, 8, 15),  // Independence Day
            LocalDate.of(2026, 9, 14),  // Ganesh Chaturthi (approx)
            LocalDate.of(2026, 10, 2),  // Mahatma Gandhi Jayanti
            LocalDate.of(2026, 10, 20), // Dussehra (approx)
            LocalDate.of(2026, 11, 8),  // Diwali-Laxmi Pujan (approx)
            LocalDate.of(2026, 11, 9),  // Diwali-Balipratipada (approx)
            LocalDate.of(2026, 11, 24), // Guru Nanak Jayanti (approx)
            LocalDate.of(2026, 12, 25)  // Christmas
    );

    private NseHolidayCalendar() {
    }

    public static boolean isHoliday(LocalDate date) {
        return date != null && HOLIDAYS.contains(date);
    }
}
