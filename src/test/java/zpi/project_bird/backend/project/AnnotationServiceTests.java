package zpi.project_bird.backend.project;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AnnotationServiceTests {
    @Mock AnnotationRepository annotations;
    @Mock ProjectRepository projects;
    final UUID organizationId = UUID.randomUUID();
    final UUID projectId = UUID.randomUUID();
    final UUID detectionId = UUID.randomUUID();

    static List<List<List<List<Double>>>> square() {
        return List.of(List.of(List.of(List.of(0d, 0d), List.of(10d, 0d), List.of(10d, 10d), List.of(0d, 10d), List.of(0d, 0d))));
    }

    @Test void acceptsClosedPolygon() { assertDoesNotThrow(() -> AnnotationService.validateBounds(square())); }

    @Test void rejectsOpenRing() {
        assertThrows(ResponseStatusException.class, () -> AnnotationService.validateBounds(
            List.of(List.of(List.of(List.of(0d, 0d), List.of(10d, 0d), List.of(10d, 10d), List.of(0d, 10d))))));
    }

    @Test void rejectsSelfIntersectingPolygon() {
        assertThrows(ResponseStatusException.class, () -> AnnotationService.validateBounds(
            List.of(List.of(List.of(List.of(0d, 0d), List.of(10d, 10d), List.of(0d, 10d), List.of(10d, 0d), List.of(0d, 0d))))));
    }

    @Test void rejectsNonFiniteCoordinates() {
        assertThrows(ResponseStatusException.class, () -> AnnotationService.validateBounds(
            List.of(List.of(List.of(List.of(0d, 0d), List.of(Double.NaN, 0d), List.of(10d, 10d), List.of(0d, 0d))))));
    }

    @Test void staleVersionCannotWriteOrAddHistory() {
        when(projects.lock(organizationId, projectId)).thenReturn(Optional.of(project()));
        when(annotations.find(projectId, detectionId)).thenReturn(Optional.of(state(2)));
        var service = new AnnotationService(annotations, projects, JsonMapper.builder().build());
        var error = assertThrows(ResponseStatusException.class, () -> service.update(organizationId, projectId, detectionId,
            new AnnotationRequest.Update(1, JsonMapper.builder().build().valueToTree(square())), "annotator"));
        assertEquals(409, error.getStatusCode().value());
        verify(annotations, never()).save(any());
        verify(annotations, never()).recordHistory(any(), any(), any(), any(), any(), any());
    }

    @Test void correctionResetsVerificationAndAdvancesVersion() {
        when(projects.lock(organizationId, projectId)).thenReturn(Optional.of(project()));
        when(annotations.find(projectId, detectionId)).thenReturn(Optional.of(state(2)));
        var service = new AnnotationService(annotations, projects, JsonMapper.builder().build());
        var changed = service.update(organizationId, projectId, detectionId, new AnnotationRequest.Update(2, JsonMapper.builder().build().valueToTree(square())), "annotator");
        assertEquals(3, changed.version());
        assertNull(changed.verifiedAt());
        assertNull(changed.verifiedBy());
        verify(annotations).save(changed);
        verify(annotations).recordHistory(eq(projectId), eq(detectionId), eq("UPDATED"), eq("annotator"), anyString(), anyString());
    }

    @Test void acceptsAllTypedPolygonClassesAndRoadsWithUnknownWidths() {
        var json = JsonMapper.builder().build();
        for (String type : List.of("building", "forest", "water")) {
            var bounds = json.readTree("{\"type\":\"" + type + "\",\"coordinates\":" + json.writeValueAsString(square()) + "}");
            assertDoesNotThrow(() -> AnnotationService.validateBounds(bounds));
        }
        assertDoesNotThrow(() -> AnnotationService.validateBounds(json.readTree(roads())));
    }

    @Test void rejectsInvalidTypedGeometryAndMisalignedWidths() {
        var json = JsonMapper.builder().build();
        for (String bounds : List.of(
                "{\"type\":\"unknown\",\"coordinates\":[[[[0,0],[10,0],[10,10],[0,0]]]]}",
                "{\"type\":\"water\",\"coordinates\":[[[[0,0],[10,0],[10,10],[0,10]]]]}",
                "{\"type\":\"road\",\"coordinates\":[[[0,0]]],\"width_m\":[null]}",
                "{\"type\":\"road\",\"coordinates\":[[[0,0],[0,0]]],\"width_m\":[null]}",
                "{\"type\":\"road\",\"coordinates\":[[[0,0],[10,10]]],\"width_m\":[]}",
                "{\"type\":\"road\",\"coordinates\":[[[0,0],[10,10]]],\"width_m\":[-1]}",
                "{\"type\":\"road\",\"coordinates\":[[[0,0],[10,10]]],\"width_m\":[0]}",
                "{\"type\":\"road\",\"coordinates\":[[[0,0],[10,10]]],\"width_m\":[\"6.5\"]}",
                "{\"type\":\"road\",\"coordinates\":[[[\"0\",0],[10,10]]],\"width_m\":[null]}")) {
            var error = assertThrows(ResponseStatusException.class, () -> AnnotationService.validateBounds(json.readTree(bounds)), bounds);
            assertEquals(400, error.getStatusCode().value());
        }
    }

    @Test void typedCorrectionPreservesTypeCoordinatesAndRoadWidthsInStateAndHistory() {
        when(projects.lock(organizationId, projectId)).thenReturn(Optional.of(project()));
        when(annotations.find(projectId, detectionId)).thenReturn(Optional.of(state(2)));
        var json = JsonMapper.builder().build();
        var bounds = json.readTree(roads());
        var changed = new AnnotationService(annotations, projects, json).update(organizationId, projectId, detectionId,
            new AnnotationRequest.Update(2, bounds), "annotator");
        assertEquals(bounds, json.readTree(changed.bounds()));
        assertEquals(3, changed.version());
        assertNull(changed.verifiedBy());
        verify(annotations).save(changed);
        verify(annotations).recordHistory(eq(projectId), eq(detectionId), eq("UPDATED"), eq("annotator"), anyString(),
            argThat(after -> json.readTree(after).get("bounds").equals(bounds)));
    }

    @Test void typedRoadsCanBeVerifiedWithoutChangingGeometry() {
        var before = new DetectionState(detectionId, projectId, "sheet", UUID.randomUUID(), detectionId, roads(),
            false, 0, null, null, null, null);
        when(projects.lock(organizationId, projectId)).thenReturn(Optional.of(project()));
        when(annotations.find(projectId, detectionId)).thenReturn(Optional.of(before));
        var changed = new AnnotationService(annotations, projects, JsonMapper.builder().build()).verify(organizationId, projectId,
            detectionId, new AnnotationRequest.Verification(0, true), "annotator");
        assertEquals(roads(), changed.bounds());
        assertEquals("annotator", changed.verifiedBy());
        assertEquals(1, changed.version());
        verify(annotations).save(changed);
    }

    private static String roads() {
        return "{\"type\":\"road\",\"coordinates\":[[[0,0],[5,10],[10,20]],[[20,0],[20,10]]],\"width_m\":[6.5,null]}";
    }

    private Project project() { return new Project(projectId, "Area", organizationId, new Project.Coordinates(16, 51), new Project.Coordinates(17, 50)); }
    private DetectionState state(int version) {
        return new DetectionState(detectionId, projectId, "sheet", UUID.randomUUID(), detectionId, "[]", false, version,
            "owner", java.time.OffsetDateTime.now(), "owner", java.time.OffsetDateTime.now());
    }
}
