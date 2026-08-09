package vn.covie.users.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.users.api.dto.CreateUserAddressRequest;
import vn.covie.users.api.dto.UpdateUserAddressRequest;
import vn.covie.users.api.dto.UserAddressResponse;
import vn.covie.users.application.UserAddressService;

@RestController
@RequestMapping("/api/v1/me/addresses")
public class UserAddressController {
    private final UserAddressService addresses;

    public UserAddressController(UserAddressService addresses) { this.addresses = addresses; }

    @GetMapping public List<UserAddressResponse> active() { return addresses.active().stream().map(UserAddressResponse::from).toList(); }
    @GetMapping("/{addressId}") public UserAddressResponse get(@PathVariable UUID addressId) { return UserAddressResponse.from(addresses.get(addressId)); }
    @PostMapping public ResponseEntity<UserAddressResponse> create(@Valid @RequestBody CreateUserAddressRequest request) { return ResponseEntity.ok(UserAddressResponse.from(addresses.create(request))); }
    @PatchMapping("/{addressId}") public UserAddressResponse update(@PathVariable UUID addressId, @Valid @RequestBody UpdateUserAddressRequest request) { return UserAddressResponse.from(addresses.update(addressId, request)); }
    @DeleteMapping("/{addressId}") public ResponseEntity<Void> delete(@PathVariable UUID addressId) { addresses.delete(addressId); return ResponseEntity.noContent().build(); }
}
