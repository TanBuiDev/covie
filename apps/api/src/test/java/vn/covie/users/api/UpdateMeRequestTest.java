package vn.covie.users.api;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.covie.users.api.dto.UpdateMeRequest;
class UpdateMeRequestTest {
 final ObjectMapper json=new ObjectMapper();
 @Test void distinguishesExplicitNullFromOmittedOptionalFields()throws Exception{
  var omitted=json.readValue("{\"displayName\":\"Minh\"}",UpdateMeRequest.class);assertThat(omitted.hasPhone()).isFalse();assertThat(omitted.hasAvatarMediaId()).isFalse();
  var cleared=json.readValue("{\"displayName\":\"Minh\",\"phone\":null,\"avatarMediaId\":null}",UpdateMeRequest.class);assertThat(cleared.hasPhone()).isTrue();assertThat(cleared.hasAvatarMediaId()).isTrue();assertThat(cleared.phone()).isNull();assertThat(cleared.avatarMediaId()).isNull();
 }
}
