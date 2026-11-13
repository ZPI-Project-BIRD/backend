package zpi.project_bird.backend.project;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.core.JacksonException;

@Service
public class AnnotationService {
    private final AnnotationRepository annotations;
    private final ProjectRepository projects;
    private final ObjectMapper json;
    private static final GeometryFactory GEOMETRY = new GeometryFactory();

    public AnnotationService(AnnotationRepository annotations, ProjectRepository projects, ObjectMapper json) {
        this.annotations = annotations;
        this.projects = projects;
        this.json = json;
    }

    @Transactional
    public DetectionState create(UUID organizationId, UUID projectId, AnnotationRequest.Create request, String actor) {
        lock(organizationId, projectId);
        if (!annotations.hasFeature(projectId, request.featureId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Source feature was not found in this project");
        }
        validateBounds(request.bounds());
        var state = new DetectionState(UUID.randomUUID(), projectId, request.featureId(), null, null,
            json.writeValueAsString(request.bounds()), false, 0, null, null, null, null)
            .change(json.writeValueAsString(request.bounds()), false, actor, false);
        persist(state, null, "CREATED", actor);
        return state;
    }

    @Transactional
    public DetectionState update(UUID organizationId, UUID projectId, UUID detectionId, AnnotationRequest.Update request, String actor) {
        lock(organizationId, projectId);
        var before = find(projectId, detectionId, request.expectedVersion());
        validateBounds(request.bounds());
        // A geometry correction invalidates any earlier verification.
        var after = before.change(json.writeValueAsString(request.bounds()), false, actor, false);
        persist(after, before, "UPDATED", actor);
        return after;
    }

    @Transactional
    public void delete(UUID organizationId, UUID projectId, UUID detectionId, int expectedVersion, String actor) {
        lock(organizationId, projectId);
        var before = find(projectId, detectionId, expectedVersion);
        persist(before.change(before.bounds(), true, actor, false), before, "DELETED", actor);
    }

    @Transactional
    public DetectionState verify(UUID organizationId, UUID projectId, UUID detectionId, AnnotationRequest.Verification request, String actor) {
        lock(organizationId, projectId);
        var before = find(projectId, detectionId, request.expectedVersion());
        if (request.verified() && (before.bounds() == null || before.bounds().equals("null"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A detection without geometry cannot be verified");
        }
        if (request.verified()) {
            try {
                validateBounds(json.readValue(before.bounds(), new TypeReference<List<List<List<List<Double>>>>>() {}));
            } catch (JacksonException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Detection geometry must be corrected before verification");
            }
        }
        var after = before.change(before.bounds(), false, actor, request.verified());
        persist(after, before, request.verified() ? "VERIFIED" : "UNVERIFIED", actor);
        return after;
    }

    @Transactional(readOnly = true)
    public List<AnnotationHistoryEntry> history(UUID organizationId, UUID projectId, UUID detectionId, int limit, int offset) {
        projects.findById(organizationId, projectId).orElseThrow(AnnotationService::notFound);
        return annotations.history(projectId, detectionId, limit, offset);
    }

    private void lock(UUID organizationId, UUID projectId) {
        // Serialize manual changes per project; the version check then reliably prevents lost updates.
        projects.lock(organizationId, projectId).orElseThrow(AnnotationService::notFound);
    }

    private DetectionState find(UUID projectId, UUID detectionId, int expectedVersion) {
        var state = annotations.find(projectId, detectionId).filter(value -> !value.deleted()).orElseThrow(AnnotationService::notFound);
        if (state.version() != expectedVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Detection changed; reload it and retry with its current version");
        }
        return state;
    }

    private void persist(DetectionState after, DetectionState before, String action, String actor) {
        annotations.save(after);
        annotations.recordHistory(after.projectId(), after.id(), action, actor,
            before == null ? null : json.writeValueAsString(before), json.writeValueAsString(after));
    }

    static void validateBounds(List<List<List<List<Double>>>> polygons) {
        try {
            if (polygons == null || polygons.isEmpty() || polygons.size() > 10000) throw new IllegalArgumentException();
            int pointCount = 0;
            for (var polygon : polygons) {
                if (polygon == null || polygon.isEmpty()) throw new IllegalArgumentException();
                var rings = new ArrayList<LinearRing>();
                for (var ring : polygon) {
                    if (ring == null || ring.size() < 4) throw new IllegalArgumentException();
                    pointCount += ring.size();
                    if (pointCount > 100000) throw new IllegalArgumentException();
                    var coordinates = new Coordinate[ring.size()];
                    for (int i = 0; i < ring.size(); i++) {
                        var point = ring.get(i);
                        if (point == null || point.size() != 2 || point.get(0) == null || point.get(1) == null
                                || !Double.isFinite(point.get(0)) || !Double.isFinite(point.get(1))) throw new IllegalArgumentException();
                        coordinates[i] = new Coordinate(point.get(0), point.get(1));
                    }
                    rings.add(GEOMETRY.createLinearRing(coordinates));
                }
                var shape = GEOMETRY.createPolygon(rings.getFirst(), rings.subList(1, rings.size()).toArray(LinearRing[]::new));
                if (shape.isEmpty() || shape.getArea() <= 0 || !new IsValidOp(shape).isValid()) throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Bounds must contain valid polygons with closed rings, finite [easting, northing] pairs, and at most 100000 points");
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Project or detection was not found in this organization");
    }
}
