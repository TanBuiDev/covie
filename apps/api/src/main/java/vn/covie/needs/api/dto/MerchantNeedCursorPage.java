package vn.covie.needs.api.dto;
import java.util.List;
public record MerchantNeedCursorPage(List<MerchantNeedMatchResponse> items, boolean hasMore, String nextCursor) {}
