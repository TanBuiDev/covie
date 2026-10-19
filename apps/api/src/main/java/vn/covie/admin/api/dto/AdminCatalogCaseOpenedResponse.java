package vn.covie.admin.api.dto;

import java.util.UUID;

public record AdminCatalogCaseOpenedResponse(UUID caseId, boolean created) {}
