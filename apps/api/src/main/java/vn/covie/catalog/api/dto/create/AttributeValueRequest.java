package vn.covie.catalog.api.dto.create;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.util.UUID;

public record AttributeValueRequest(@JsonAlias("categoryAttributeKey") String attributeKey, String valueText,
        BigDecimal valueNumber, Boolean valueBoolean, @JsonAlias("attributeOptionId") UUID optionId) {}
