package by.regsales.common;

import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Map;

public record CalendarPeriod(LocalDate start, LocalDate end) {
    public CalendarPeriod {
        if (start == null || start.getYear() < 1000 || start.getYear() > 9999 || start.getDayOfMonth() != 1) {
            throw invalid("periodStart");
        }
        boolean month = start.plusMonths(1).minusDays(1).equals(end);
        boolean quarter = (start.getMonthValue() - 1) % 3 == 0 && start.plusMonths(3).minusDays(1).equals(end);
        if (!month && !quarter) throw invalid("periodEnd");
    }

    private static ApiException invalid(String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Проверьте период",
                Map.of(field, "Выберите календарный месяц или квартал"));
    }
}
