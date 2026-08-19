package vn.covie.location.api;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.covie.location.api.dto.LocationAreaResponse;
import vn.covie.location.application.LocationAreaService;
import vn.covie.location.model.LocationAreaStatus;
import vn.covie.location.model.LocationAreaType;

class LocationAreaControllerTest {
    private LocationAreaService areas;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        areas = mock(LocationAreaService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new LocationAreaController(areas)).build();
    }

    @Test
    void returnsOnlyTheServiceProvidedActiveRootAreasWhenParentIsOmitted() throws Exception {
        UUID rootId = UUID.randomUUID();
        when(areas.activeAreas(null)).thenReturn(List.of(response(rootId, null, "Community A", 1)));

        mockMvc.perform(get("/api/v1/location-areas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(rootId.toString()))
                .andExpect(jsonPath("$[0].parentId").doesNotExist())
                .andExpect(jsonPath("$[0].name").value("Community A"))
                .andExpect(jsonPath("$[0].sortOrder").value(1));

        verify(areas).activeAreas(isNull());
    }

    @Test
    void resolvesTheRequestedParentBeforeReturningItsOrderedChildren() throws Exception {
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        when(areas.activeAreas(parentId)).thenReturn(List.of(response(childId, parentId, "Building A", 2)));

        mockMvc.perform(get("/api/v1/location-areas").param("parentId", parentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(childId.toString()))
                .andExpect(jsonPath("$[0].parentId").value(parentId.toString()))
                .andExpect(jsonPath("$[0].name").value("Building A"));

        verify(areas).activeAreas(parentId);
    }

    private LocationAreaResponse response(UUID id, UUID parentId, String name, int sortOrder) {
        return new LocationAreaResponse(id, parentId, LocationAreaType.COMMUNITY, name, name.toLowerCase().replace(' ', '-'),
                LocationAreaStatus.ACTIVE, sortOrder);
    }
}
