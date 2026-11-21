package by.regsales.analytics;

import by.regsales.common.ApiException;
import by.regsales.domain.AppUser;
import by.regsales.domain.ImportStatus;
import by.regsales.domain.Region;
import by.regsales.domain.RegionalStatistic;
import by.regsales.domain.RoleCode;
import by.regsales.domain.StatisticalIndicator;
import by.regsales.domain.StatisticsImport;
import by.regsales.repository.AppUserRepository;
import by.regsales.repository.RegionRepository;
import by.regsales.repository.RegionalStatisticRepository;
import by.regsales.repository.RoleRepository;
import by.regsales.repository.StatisticalIndicatorRepository;
import by.regsales.repository.StatisticsImportRepository;
import by.regsales.security.AppPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static by.regsales.analytics.AnalyticsDtos.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BelstatStatistics {
    private static final ZoneId MINSK = ZoneId.of("Europe/Minsk");
    private static final String SOURCE_NAME = "Белстат";
    private static final String SALARY = "ANNUAL_AVERAGE_SALARY";
    private static final String TURNOVER = "ANNUAL_RETAIL_TURNOVER";
    private static final String POPULATION = "POPULATION";
    private static final String MONTHLY_SALARY = "AVERAGE_SALARY";
    private static final String TURNOVER_YTD = "RETAIL_TURNOVER_YTD";
    private static final String NON_FOOD_CPI = "NON_FOOD_CPI_MOM";
    private static final BigDecimal CPI_BASE = BigDecimal.valueOf(100);
    private static final Set<String> CODES = Set.of(SALARY, TURNOVER, POPULATION);

    private final RegionalStatisticRepository statistics;
    private final StatisticalIndicatorRepository indicators;
    private final StatisticsImportRepository imports;
    private final RegionRepository regions;
    private final AppUserRepository users;
    private final RoleRepository roles;

    public Optional<Statistics> latest() {
        return latestThrough(LocalDate.now(MINSK));
    }

    public Optional<Statistics> latestThrough(LocalDate cutoff) {
        var byYear = statistics.findByIndicatorCodeInAndPeriodEndLessThanEqualAndStatisticsImportSourceNameOrderByPeriodEndDesc(
                        CODES, cutoff, SOURCE_NAME).stream()
                .filter(row -> row.getPeriodStart().equals(start(row.getPeriodEnd().getYear()))
                        && row.getPeriodEnd().equals(end(row.getPeriodEnd().getYear())))
                .collect(Collectors.groupingBy(row -> row.getPeriodEnd().getYear()));
        return byYear.entrySet().stream()
                .sorted(Map.Entry.<Integer, List<RegionalStatistic>>comparingByKey().reversed())
                .map(entry -> toStatistics(entry.getKey(), entry.getValue()))
                .flatMap(Optional::stream)
                .findFirst();
    }

    public List<SalaryMonth> monthlySalaries() {
        return monthlyReleases(MONTHLY_SALARY).map(entry -> salaryMonth(entry.getKey(), entry.getValue())).toList();
    }

    private Stream<Map.Entry<YearMonth, List<RegionalStatistic>>> monthlyReleases(String code) {
        return statistics.findByIndicatorCodeInAndPeriodEndLessThanEqualAndStatisticsImportSourceNameOrderByPeriodEndDesc(
                        Set.of(code), LocalDate.now(MINSK), SOURCE_NAME).stream()
                .collect(Collectors.groupingBy(row -> YearMonth.from(row.getPeriodStart())))
                .entrySet().stream()
                .sorted(Map.Entry.<YearMonth, List<RegionalStatistic>>comparingByKey().reversed());
    }

    private static SalaryMonth salaryMonth(YearMonth period, List<RegionalStatistic> values) {
        var rows = values.stream()
                .map(row -> new RegionalSalary(row.getRegion().getCode(), row.getRegion().getName(), row.getValue()))
                .sorted(Comparator.comparing(RegionalSalary::regionName)).toList();
        return new SalaryMonth(period.toString(), values.getFirst().getSourceUrl(), rows);
    }

    @Transactional
    public MonthlyDownloadResult saveMonthlySalaries(BelstatSalaryClient.Download download, AppPrincipal actor) {
        if (download.releases().isEmpty()) {
            var first = download.failures().getFirst();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "BELSTAT_SALARY_DOWNLOAD",
                    "Не удалось загрузить зарплаты за " + download.year() + " год. " + first.period() + " · " + first.message());
        }
        var analyst = analystForUpdate(actor);
        var indicator = indicators.findByCode(MONTHLY_SALARY)
                .orElseThrow(() -> new IllegalStateException("Показатель средней зарплаты отсутствует в справочнике"));
        var regionByCode = belstatRegions();
        for (var release : download.releases()) {
            saveRegionalRelease(analyst, indicator, regionByCode, release.source(), release.period().atDay(1),
                    release.period().atEndOfMonth(), release.salaries());
        }
        var issues = download.failures().stream()
                .map(failure -> new MonthlyDownloadIssue(failure.period().toString(), failure.message())).toList();
        return new MonthlyDownloadResult(download.year(), download.releases().size(), issues);
    }

    public List<TurnoverMonth> turnoverMonths() {
        var byMonth = statistics.findByIndicatorCodeInAndPeriodEndLessThanEqualAndStatisticsImportSourceNameOrderByPeriodEndDesc(
                        Set.of(TURNOVER_YTD), LocalDate.now(MINSK), SOURCE_NAME).stream()
                .filter(row -> row.getPeriodStart().equals(start(row.getPeriodEnd().getYear()))
                        && row.getPeriodEnd().equals(YearMonth.from(row.getPeriodEnd()).atEndOfMonth()))
                .collect(Collectors.groupingBy(row -> YearMonth.from(row.getPeriodEnd()),
                        Collectors.toMap(row -> row.getRegion().getCode(), row -> row)));
        return byMonth.keySet().stream().sorted(Comparator.reverseOrder())
                .filter(period -> byMonth.get(period).keySet().equals(BelstatWorkbook.regionCodes()))
                .map(period -> turnoverMonth(period, byMonth.get(period), byMonth.getOrDefault(period.minusMonths(1), Map.of())))
                .toList();
    }

    private static TurnoverMonth turnoverMonth(YearMonth period, Map<String, RegionalStatistic> values,
                                               Map<String, RegionalStatistic> previous) {
        var rows = values.entrySet().stream().map(entry -> {
            var row = entry.getValue();
            var earlier = previous.get(entry.getKey());
            var total = row.getValue();
            BigDecimal monthly = null;
            if (period.getMonthValue() == 1) monthly = total;
            else if (earlier != null && total.compareTo(earlier.getValue()) >= 0) monthly = total.subtract(earlier.getValue());
            return new RegionalTurnover(entry.getKey(), row.getRegion().getName(), total, monthly);
        }).sorted(Comparator.comparing(RegionalTurnover::regionName)).toList();
        return new TurnoverMonth(period.toString(), values.values().iterator().next().getSourceUrl(), rows);
    }

    @Transactional
    public MonthlyDownloadResult saveTurnover(BelstatTurnoverClient.Download download, AppPrincipal actor) {
        if (download.releases().isEmpty()) {
            var first = download.failures().getFirst();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "BELSTAT_TURNOVER_DOWNLOAD",
                    "Не удалось загрузить товарооборот за " + download.year() + " год. " + first.period() + " · " + first.message());
        }
        var analyst = analystForUpdate(actor);
        var indicator = indicators.findByCode(TURNOVER_YTD)
                .orElseThrow(() -> new IllegalStateException("Показатель розничного товарооборота отсутствует в справочнике"));
        var regionByCode = belstatRegions();
        for (var release : download.releases()) {
            saveRegionalRelease(analyst, indicator, regionByCode, release.source(), start(release.period().getYear()),
                    release.period().atEndOfMonth(), release.totals());
        }
        var issues = download.failures().stream()
                .map(failure -> new MonthlyDownloadIssue(failure.period().toString(), failure.message())).toList();
        return new MonthlyDownloadResult(download.year(), download.releases().size(), issues);
    }

    public List<CpiMonth> nonFoodCpiMonths() {
        return monthlyReleases(NON_FOOD_CPI).map(entry -> {
            var values = entry.getValue();
            var regionalIndices = values.stream()
                    .map(row -> new RegionalCpi(row.getRegion().getCode(), row.getRegion().getName(), row.getValue(),
                            row.getValue().subtract(CPI_BASE)))
                    .sorted(Comparator.comparing(RegionalCpi::regionName)).toList();
            return new CpiMonth(entry.getKey().toString(), values.getFirst().getSourceUrl(), regionalIndices);
        }).toList();
    }

    @Transactional
    public MonthlyDownloadResult saveNonFoodCpi(BelstatCpiClient.Download download, AppPrincipal actor) {
        if (download.releases().isEmpty()) {
            var first = download.issues().getFirst();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "BELSTAT_CPI_DOWNLOAD",
                    "Не удалось загрузить ИПЦ за " + download.year() + " год. " + first.period() + " · " + first.message());
        }
        var analyst = analystForUpdate(actor);
        var indicator = indicators.findByCode(NON_FOOD_CPI)
                .orElseThrow(() -> new IllegalStateException("Показатель ИПЦ на непродовольственные товары отсутствует в справочнике"));
        var regionByCode = belstatRegions();
        for (var release : download.releases()) {
            saveRegionalRelease(analyst, indicator, regionByCode, release.source(), release.period().atDay(1),
                    release.period().atEndOfMonth(), release.indices());
        }
        return new MonthlyDownloadResult(download.year(), download.releases().size(), download.issues());
    }

    private Map<String, Region> belstatRegions() {
        var regionByCode = regions.findAll().stream().collect(Collectors.toMap(Region::getCode, region -> region));
        if (!regionByCode.keySet().containsAll(BelstatWorkbook.regionCodes())) {
            throw new ApiException(HttpStatus.CONFLICT, "BELSTAT_REFERENCE_DATA", "В справочнике отсутствуют регионы Белстата");
        }
        return regionByCode;
    }

    private void saveRegionalRelease(AppUser analyst, StatisticalIndicator indicator, Map<String, Region> regionByCode,
                                     URI source, LocalDate start, LocalDate end, Map<String, BigDecimal> values) {
        var previous = statistics.findByIndicatorCodeInAndPeriodStartAndPeriodEnd(Set.of(indicator.getCode()), start, end).stream()
                .collect(Collectors.toMap(row -> row.getRegion().getCode(), row -> row));
        String path = source.getPath();
        var record = importRecord(analyst, path.substring(path.lastIndexOf('/') + 1), values.size());
        imports.save(record);
        values.forEach((code, value) -> {
            var row = previous.getOrDefault(code, new RegionalStatistic());
            row.setRegion(regionByCode.get(code));
            row.setIndicator(indicator);
            row.setPeriodStart(start);
            row.setPeriodEnd(end);
            row.setValue(databaseValue(value));
            row.setStatisticsImport(record);
            row.setSourceUrl(source.toString());
            statistics.save(row);
        });
    }

    @Transactional
    public StatisticsImportResult importLatest(MultipartFile salaryFile, MultipartFile turnoverFile, MultipartFile populationFile,
                                               TurnoverUnit turnoverUnit, PopulationUnit populationUnit, AppPrincipal actor) {
        var salary = BelstatWorkbook.read(salaryFile, "Средняя зарплата");
        var turnover = BelstatWorkbook.read(turnoverFile, "Розничный товарооборот");
        var population = BelstatWorkbook.read(populationFile, "Численность населения");
        int currentYear = LocalDate.now(MINSK).getYear();
        int year = salary.keySet().stream()
                .filter(candidate -> candidate < currentYear && complete(salary.get(candidate))
                        && complete(turnover.get(candidate)) && complete(population.get(candidate)))
                .max(Integer::compareTo)
                .orElseThrow(() -> invalid("В трёх файлах нет общего завершённого года с данными по всем областям и г. Минску"));

        var values = BelstatWorkbook.regionCodes().stream().collect(Collectors.toMap(code -> code, code -> Map.of(
                SALARY, databaseValue(salary.get(year).get(code)),
                TURNOVER, databaseValue(turnover.get(year).get(code).movePointRight(turnoverUnit == TurnoverUnit.MILLION_BYN ? 6 : 0)),
                POPULATION, populationValue(population.get(year).get(code), populationUnit))));

        var analyst = analystForUpdate(actor);
        var regionByCode = regions.findAll().stream().collect(Collectors.toMap(region -> region.getCode(), region -> region));
        var indicatorByCode = indicators.findAll().stream().collect(Collectors.toMap(indicator -> indicator.getCode(), indicator -> indicator));
        if (!regionByCode.keySet().containsAll(values.keySet()) || !indicatorByCode.keySet().containsAll(CODES)) {
            throw new ApiException(HttpStatus.CONFLICT, "BELSTAT_REFERENCE_DATA", "В справочниках отсутствуют регионы или годовые показатели Белстата");
        }
        var previous = statistics.findByIndicatorCodeInAndPeriodStartAndPeriodEnd(CODES, start(year), end(year)).stream()
                .collect(Collectors.toMap(row -> key(row.getRegion().getCode(), row.getIndicator().getCode()), row -> row));
        var filenames = String.join(" · ", salaryFile.getOriginalFilename(), turnoverFile.getOriginalFilename(), populationFile.getOriginalFilename());
        var record = importRecord(analyst, filenames, values.size() * CODES.size());
        imports.save(record);
        values.forEach((region, measures) -> measures.forEach((indicator, value) -> {
            var row = previous.getOrDefault(key(region, indicator), new RegionalStatistic());
            row.setRegion(regionByCode.get(region));
            row.setIndicator(indicatorByCode.get(indicator));
            row.setPeriodStart(start(year));
            row.setPeriodEnd(end(year));
            row.setValue(value);
            row.setStatisticsImport(record);
            row.setSourceUrl(null);
            statistics.save(row);
        }));
        return new StatisticsImportResult(year, values.size());
    }

    private AppUser analystForUpdate(AppPrincipal actor) {
        // Serialize upserts so concurrent uploads update the same period without duplicates.
        roles.lockByCode(RoleCode.ANALYST).orElseThrow(() -> new AccessDeniedException("Импорт доступен региональному аналитику"));
        return users.findById(actor.getId())
                .filter(user -> user.isActive() && user.getRole().getCode() == RoleCode.ANALYST
                        && user.getSessionVersion() == actor.getSessionVersion())
                .orElseThrow(() -> new AccessDeniedException("Импорт доступен региональному аналитику"));
    }

    private static StatisticsImport importRecord(AppUser analyst, String filenames, int count) {
        var record = new StatisticsImport();
        record.setFileName(filenames.substring(0, Math.min(filenames.length(), 255)));
        record.setSourceName(SOURCE_NAME);
        record.setImportedByUser(analyst);
        record.setRowsTotal(count);
        record.setRowsAccepted(count);
        record.setStatus(ImportStatus.COMPLETED);
        return record;
    }

    private static Optional<Statistics> toStatistics(int year, List<RegionalStatistic> records) {
        var byRegion = records.stream().filter(row -> BelstatWorkbook.regionCodes().contains(row.getRegion().getCode()))
                .collect(Collectors.groupingBy(row -> row.getRegion().getCode()));
        if (!byRegion.keySet().equals(BelstatWorkbook.regionCodes())) return Optional.empty();
        if (byRegion.values().stream().anyMatch(list -> list.size() != CODES.size())) return Optional.empty();
        var rows = byRegion.values().stream().map(regional -> {
            var region = regional.getFirst().getRegion();
            var values = regional.stream().collect(Collectors.toMap(row -> row.getIndicator().getCode(), RegionalStatistic::getValue));
            long population = values.get(POPULATION).longValueExact();
            var retailPerPerson = values.get(TURNOVER).divide(BigDecimal.valueOf(population), 2, RoundingMode.HALF_UP);
            return new Indicators(region.getId(), region.getCode(), region.getName(), values.get(SALARY), null, retailPerPerson, population);
        }).sorted(Comparator.comparing(Indicators::regionName)).toList();
        return Optional.of(new Statistics(Source.BELSTAT, start(year), end(year), null, rows));
    }

    private static boolean complete(Map<String, BigDecimal> values) {
        return values != null && values.keySet().equals(BelstatWorkbook.regionCodes());
    }

    private static BigDecimal populationValue(BigDecimal value, PopulationUnit unit) {
        try {
            return BigDecimal.valueOf(value.movePointRight(unit == PopulationUnit.THOUSAND_PEOPLE ? 3 : 0).longValueExact());
        } catch (ArithmeticException error) {
            throw invalid("Проверьте единицы измерения численности населения. После пересчёта число жителей должно быть целым");
        }
    }

    private static BigDecimal databaseValue(BigDecimal value) {
        var rounded = value.setScale(4, RoundingMode.HALF_UP);
        if (rounded.signum() <= 0 || rounded.precision() > 20) throw invalid("В таблице есть значение вне допустимого диапазона");
        return rounded;
    }

    private static LocalDate start(int year) { return LocalDate.of(year, 1, 1); }

    private static LocalDate end(int year) { return LocalDate.of(year, 12, 31); }

    private static String key(String region, String code) { return region + "/" + code; }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BELSTAT_INVALID_DATA", message);
    }
}
