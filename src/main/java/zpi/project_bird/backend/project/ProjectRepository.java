package zpi.project_bird.backend.project;

import static zpi.project_bird.backend.jooq.tables.Projects.PROJECTS;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Repository;

import zpi.project_bird.backend.jooq.tables.records.ProjectsRecord;

@Repository
public class ProjectRepository {
    private static final GeometryFactory GEOMETRY_FACTORY =
        new GeometryFactory(new PrecisionModel(), 4326);

    private final DSLContext dsl;

    public ProjectRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<Project> findAll(UUID organizationId) {
        return dsl.selectFrom(PROJECTS)
            .where(PROJECTS.ORGANIZATION_ID.eq(organizationId))
            .orderBy(PROJECTS.ID)
            .fetch(this::toProject);
    }

    public Optional<Project> findById(UUID organizationId, UUID projectId) {
        return dsl.selectFrom(PROJECTS)
            .where(
                PROJECTS.ORGANIZATION_ID.eq(organizationId),
                PROJECTS.ID.eq(projectId))
            .fetchOptional(this::toProject);
    }

    public Project create(
            UUID organizationId,
            String name,
            Project.Coordinates topLeft,
            Project.Coordinates bottomRight) {
        ProjectsRecord record = dsl.insertInto(PROJECTS)
            .set(PROJECTS.ORGANIZATION_ID, organizationId)
            .set(PROJECTS.NAME, name)
            .set(PROJECTS.TOP_LEFT, toGeometry(topLeft))
            .set(PROJECTS.BOTTOM_RIGHT, toGeometry(bottomRight))
            .returning()
            .fetchSingle();

        return toProject(record);
    }

    public boolean delete(UUID organizationId, UUID projectId) {
        return dsl.deleteFrom(PROJECTS)
            .where(
                PROJECTS.ORGANIZATION_ID.eq(organizationId),
                PROJECTS.ID.eq(projectId))
            .execute() == 1;
    }

    private Project toProject(ProjectsRecord record) {
        return new Project(
            record.getId(),
            record.getName(),
            record.getOrganizationId(),
            toCoordinates(record.getTopLeft()),
            toCoordinates(record.getBottomRight()));
    }

    private static Point toGeometry(Project.Coordinates coordinates) {
        return GEOMETRY_FACTORY.createPoint(
            new CoordinateXY(coordinates.longitude(), coordinates.latitude()));
    }

    private static Project.Coordinates toCoordinates(Geometry geometry) {
        if (!(geometry instanceof Point point)) {
            throw new IllegalStateException("Expected project boundary geometry to be a point");
        }

        return new Project.Coordinates(point.getX(), point.getY());
    }
}
