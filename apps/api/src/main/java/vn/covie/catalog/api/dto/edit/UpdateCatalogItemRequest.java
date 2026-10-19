package vn.covie.catalog.api.dto.edit;

import jakarta.validation.Valid;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import vn.covie.catalog.api.dto.create.AttributeValueRequest;

/** Fields are optional so PATCH changes only explicitly supplied item members. */
@JsonIgnoreProperties("socialCaptionPresent")
public final class UpdateCatalogItemRequest {
    @Size(max = 220) private String name;
    @Size(max = 10000) private String description;
    private UUID primaryCategoryId;
    private List<@Valid AttributeValueRequest> attributes;
    private List<String> tags;
    private List<UUID> mediaIds;
    @Size(max = 3000) private String socialCaption;
    @JsonIgnore private boolean socialCaptionPresent;

    @JsonProperty @Schema(minLength=0,maxLength=220) public String name() { return name; }
    @JsonProperty @Schema(minLength=0,maxLength=10000) public String description() { return description; }
    @JsonProperty public UUID primaryCategoryId() { return primaryCategoryId; }
    @JsonProperty public List<AttributeValueRequest> attributes() { return attributes; }
    @JsonProperty public List<String> tags() { return tags; }
    @JsonProperty public List<UUID> mediaIds() { return mediaIds; }
    @JsonProperty @Schema(nullable=true, maxLength=3000, description="Maximum 3000 UTF-16 units. Omitted retains; null or blank clears.")
    public String socialCaption() { return socialCaption; }
    @JsonIgnore public boolean socialCaptionWasSupplied() { return socialCaptionPresent; }

    @JsonSetter public void setName(String name) { this.name=name; }
    @JsonSetter public void setDescription(String description) { this.description=description; }
    @JsonSetter public void setPrimaryCategoryId(UUID primaryCategoryId) { this.primaryCategoryId=primaryCategoryId; }
    @JsonSetter public void setAttributes(List<AttributeValueRequest> attributes) { this.attributes=attributes; }
    @JsonSetter public void setTags(List<String> tags) { this.tags=tags; }
    @JsonSetter public void setMediaIds(List<UUID> mediaIds) { this.mediaIds=mediaIds; }
    @JsonSetter
    public void setSocialCaption(String socialCaption) { this.socialCaption=socialCaption; this.socialCaptionPresent=true; }
}
