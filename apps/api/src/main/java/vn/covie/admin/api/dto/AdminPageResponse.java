package vn.covie.admin.api.dto;
import java.util.List;
public record AdminPageResponse<T>(List<T> items, long total, int page, int size) {}
