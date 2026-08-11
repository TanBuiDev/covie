package vn.covie.location.api;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.location.api.dto.LocationAreaResponse;
import vn.covie.location.application.LocationAreaService;

@RestController
@RequestMapping("/api/v1/location-areas")
public class LocationAreaController {
    private final LocationAreaService areas;

    public LocationAreaController(LocationAreaService areas) {
        this.areas = areas;
    }

    @GetMapping
    public List<LocationAreaResponse> activeAreas(@RequestParam(required = false) UUID parentId) {
        return areas.activeAreas(parentId);
    }
}
