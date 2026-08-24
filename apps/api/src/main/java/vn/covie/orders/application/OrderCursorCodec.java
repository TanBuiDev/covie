package vn.covie.orders.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

final class OrderCursorCodec {
    record Cursor(Instant createdAt, UUID id) {}
    String encode(Cursor cursor) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((cursor.createdAt().toEpochMilli()+":"+cursor.id()).getBytes(StandardCharsets.UTF_8));
    }
    Cursor decode(String value) {
        if(value==null||value.isBlank()) return null;
        try { String[] parts=new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8).split(":",2); return new Cursor(Instant.ofEpochMilli(Long.parseLong(parts[0])),UUID.fromString(parts[1])); }
        catch(RuntimeException exception){throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.BAD_REQUEST,"Order cursor is invalid.");}
    }
}
