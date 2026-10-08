package vn.covie.users.api.dto;

import java.util.UUID;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
public final class UpdateMeRequest {
 @NotBlank @Size(max=120) private String displayName;
 @Size(max=30) @Schema(nullable=true,description="Omit to preserve; null or blank clears the phone.") private String phone;
 @Schema(nullable=true,description="Owned READY USER_AVATAR media; omit to preserve, null clears.") private UUID avatarMediaId;
 private boolean phonePresent; private boolean avatarPresent;
 public UpdateMeRequest(){}
 public UpdateMeRequest(String displayName){this.displayName=displayName;}
 public String getDisplayName(){return displayName;} public void setDisplayName(String value){displayName=value;}
 public String getPhone(){return phone;} @JsonSetter public void setPhone(String value){phone=value;phonePresent=true;}
 public UUID getAvatarMediaId(){return avatarMediaId;} @JsonSetter public void setAvatarMediaId(UUID value){avatarMediaId=value;avatarPresent=true;}
 public String displayName(){return displayName;} public String phone(){return phone;} public UUID avatarMediaId(){return avatarMediaId;}
 @JsonIgnore public boolean hasPhone(){return phonePresent;}
 @JsonIgnore public boolean hasAvatarMediaId(){return avatarPresent;}
}
