package vn.covie.needs.application;

import java.util.UUID;

public record NeedSourceContext(
        UUID needRequestId,
        UUID needResponseId,
        UUID needResponseItemId,
        long responseVersion) {}
