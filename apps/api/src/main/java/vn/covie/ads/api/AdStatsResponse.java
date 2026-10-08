package vn.covie.ads.api;
import java.time.LocalDate;
import java.util.List;
public record AdStatsResponse(long impressions, long clicks, double ctr, List<Day> days) {
    public record Day(LocalDate date, long impressions, long clicks, double ctr) {}
}
