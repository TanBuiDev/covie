package vn.covie.catalog.api.dto.publicview;

import java.util.List;
import java.util.UUID;

public record PublicOptionGroupResponse(UUID id, String name, List<PublicOptionValueResponse> values) {}
