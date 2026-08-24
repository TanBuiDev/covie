package vn.covie.needs.application;

import java.util.UUID;

public record NeedDiscoveryCursor(int v, double textRank, long distanceMeters, UUID needId, UUID merchantLocationId) {
    public NeedDiscoveryCursor(double textRank, long distanceMeters, UUID needId, UUID merchantLocationId) {
        this(1, textRank, distanceMeters, needId, merchantLocationId);
    }
}
