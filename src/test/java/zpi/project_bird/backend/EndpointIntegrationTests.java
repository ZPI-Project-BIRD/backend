package zpi.project_bird.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import zpi.project_bird.backend.downloader.DownloaderClient;

@EnabledIfEnvironmentVariable(named = "BIRD_INTEGRATION_TESTS", matches = "true")
@SpringBootTest(properties = {
    "spring.datasource.url=${BIRD_TEST_DATABASE_URL:jdbc:postgresql://127.0.0.1:5432/bird_endpoint_test_20261113}",
    "spring.datasource.username=app", "spring.datasource.password=app",
    "spring.data.redis.url=redis://127.0.0.1:6379/2",
    "spring.session.data.redis.namespace=bird:endpoint-tests", "spring.session.timeout=2m",
    "server.servlet.session.cookie.secure=false"
})
@AutoConfigureMockMvc
class EndpointIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired DSLContext dsl;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @MockitoBean DownloaderClient downloader;
    @MockitoBean LambdaClient lambda;
    @MockitoBean SqsClient sqs;

    static final UUID ORG = UUID.fromString("11111111-1111-4111-8111-111111111111");
    static final UUID OTHER_ORG = UUID.fromString("11111111-1111-4111-8111-111111111112");
    static final UUID PROJECT = UUID.fromString("22222222-2222-4222-8222-222222222222");
    static final UUID OTHER_PROJECT = UUID.fromString("22222222-2222-4222-8222-222222222223");
    static final UUID JOB = UUID.fromString("33333333-3333-4333-8333-333333333333");
    static final UUID DETECTION = UUID.fromString("44444444-4444-4444-8444-444444444444");
    static final String ORGS = "/api/organizations";
    static final String BASE = ORGS + "/" + ORG + "/projects/" + PROJECT;
    static final String BOUNDS = "[[[[0,0],[10,0],[10,10],[0,10],[0,0]]]]";
    static String passwordHash;

    @BeforeEach void seed() {
        String database = dsl.fetchOne("select current_database() as name").get("name", String.class);
        assertTrue(database.contains("endpoint_test"), "Refusing to reset a non-test database");
        dsl.execute("truncate public.organizations, public.users, public.memberships, geoportal.features cascade");
        if (passwordHash == null) passwordHash = passwords.encode("Password123!");
        for (String username : new String[]{"admin", "owner", "editor", "annotator", "viewer", "free"}) {
            dsl.execute("insert into users (username, friendly_name, password_hash, is_admin) values (?, ?, ?, ?)",
                username, username, passwordHash, username.equals("admin"));
        }
        dsl.execute("insert into organizations (id, name) values (?, 'Main organization'), (?, 'Other organization')", ORG, OTHER_ORG);
        for (String[] member : new String[][]{{"owner", "4_OWNER"}, {"editor", "3_EDITOR"}, {"annotator", "2_ANNOTATOR"}, {"viewer", "1_VIEWER"}}) {
            dsl.execute("insert into memberships (user_id, organization_id, role) select id, ?, ?::membership_role from users where username = ?",
                ORG, member[1], member[0]);
        }
        for (UUID project : new UUID[]{PROJECT, OTHER_PROJECT}) {
            dsl.execute("""
                insert into projects (id, name, organization_id, top_left, bottom_right)
                values (?, 'Area', ?, ST_SetSRID(ST_Point(16,51),4326), ST_SetSRID(ST_Point(16.01,50.99),4326))
                """, project, project.equals(PROJECT) ? ORG : OTHER_ORG);
        }
        dsl.execute("""
            insert into geoportal.features (id, source_url, color_space, pixel_size, dataset_date, downloaded_at)
            values ('sheet', 'https://example.test/sheet', 'RGB', 0.25, now(), now()),
                   ('other-sheet', 'https://example.test/other', 'RGB', 0.25, now(), now())
            """);
        dsl.execute("insert into geoportal.project_features (project_id, feature_id) values (?, 'sheet'), (?, 'other-sheet')", PROJECT, OTHER_PROJECT);
        dsl.execute("insert into geoportal.recognition_jobs (id, project_id, feature_id, started_at, status) values (?, ?, 'sheet', now(), '2_FINISHED')", JOB, PROJECT);
        dsl.execute("insert into geoportal.detections (id, recognition_job_id, bounds) values (?, ?, ?::jsonb)", DETECTION, JOB, BOUNDS);
    }

    @Test void organizationAdministrationIsAdminOnlyAndSupportsCrud() throws Exception {
        mvc.perform(get(ORGS).with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(ORGS).with(user("owner"))).andExpect(status().isForbidden());
        var created = mvc.perform(as(post(ORGS), "admin").content("{\"name\":\" New organization \"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("New organization")).andReturn();
        String id = json.readTree(created.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(as(patch(ORGS + "/" + id), "owner").content("{\"name\":\"No\"}")).andExpect(status().isForbidden());
        mvc.perform(as(patch(ORGS + "/" + id), "admin").content("{\"name\":\"Renamed\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed"));
        mvc.perform(as(patch(ORGS + "/" + id), "admin").content("{\"name\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(as(delete(ORGS + "/" + id), "admin")).andExpect(status().isNoContent());
        mvc.perform(as(delete(ORGS + "/" + id), "admin")).andExpect(status().isNotFound());
    }

    @Test void existingUsersCanBeAssignedAndPromotedWithOwnerRestrictions() throws Exception {
        String membership = ORGS + "/" + ORG + "/memberships";
        String payload = "{\"userId\":\"" + userId("free") + "\",\"membershipRole\":\"EDITOR\"}";
        mvc.perform(as(post(membership), "owner").content(payload)).andExpect(status().isCreated());
        mvc.perform(as(post(membership), "owner").content(payload)).andExpect(status().isConflict());
        String role = ORGS + "/" + ORG + "/users/free/role";
        mvc.perform(as(patch(role), "owner").content("{\"membershipRole\":\"OWNER\"}")).andExpect(status().isForbidden());
        mvc.perform(as(patch(role), "admin").content("{\"membershipRole\":\"4_OWNER\"}")).andExpect(status().isOk());
        mvc.perform(as(patch(role), "owner").content("{\"membershipRole\":\"VIEWER\"}")).andExpect(status().isForbidden());
        mvc.perform(as(patch(role), "admin").content("{\"membershipRole\":\"VIEWER\"}")).andExpect(status().isOk());
    }

    @Test void lastOwnerCannotBeDemotedOrRemoved() throws Exception {
        String owner = ORGS + "/" + ORG + "/users/owner";
        mvc.perform(as(patch(owner + "/role"), "admin").content("{\"membershipRole\":\"EDITOR\"}")).andExpect(status().isConflict());
        mvc.perform(as(delete(owner), "admin")).andExpect(status().isConflict());
    }

    @Test void ownerCanCreateAnAnnotatorButCannotCreateAnOwner() throws Exception {
        String route = ORGS + "/" + ORG + "/users";
        mvc.perform(as(post(route), "owner").content("{\"friendlyName\":\"New member\",\"username\":\"new_member\",\"membershipRole\":\"ANNOTATOR\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.user.membershipRole").value("2_ANNOTATOR"));
        mvc.perform(as(post(route), "owner").content("{\"friendlyName\":\"New owner\",\"username\":\"new_owner\",\"membershipRole\":\"OWNER\"}"))
            .andExpect(status().isForbidden());
    }

    @Test void globalUserSearchIsRestrictedAndDoesNotExposePasswordHashes() throws Exception {
        mvc.perform(get("/api/users?search=view&limit=1").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].username").value("viewer")).andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/users").with(user("owner"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/users?limit=0").with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
    }

    @Test void bansBlockLoginAndRevokeSessionsEvenAfterUnban() throws Exception {
        Cookie oldSession = login("viewer");
        Cookie secondOldSession = login("viewer");
        String route = "/api/users/" + userId("viewer") + "/ban";
        mvc.perform(as(post(route), "owner").content("{\"reason\":\"Test\"}")).andExpect(status().isForbidden());
        mvc.perform(as(post(route), "admin").content("{\"reason\":\"Test\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").cookie(oldSession)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"viewer\",\"password\":\"Password123!\"}")).andExpect(status().isUnauthorized());
        mvc.perform(as(delete(route), "admin")).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").cookie(secondOldSession)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(login("viewer"))).andExpect(status().isOk());
        mvc.perform(as(post("/api/users/" + userId("admin") + "/ban"), "admin")
            .content("{\"reason\":\"Test\"}")).andExpect(status().isForbidden());
    }

    @Test void annotationsAreVersionedVerifiedAndAudited() throws Exception {
        String route = BASE + "/detections/" + DETECTION;
        mvc.perform(as(patch(route), "viewer").content(update(0))).andExpect(status().isForbidden());
        mvc.perform(as(patch(route), "annotator").content(update(0)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(as(patch(route + "/verification"), "annotator").content("{\"expectedVersion\":1,\"verified\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.verifiedBy").value("annotator")).andExpect(jsonPath("$.version").value(2));
        mvc.perform(as(patch(route), "editor").content(update(2)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3)).andExpect(jsonPath("$.verifiedBy").isEmpty());
        mvc.perform(as(patch(route), "annotator").content(update(0))).andExpect(status().isConflict());
        mvc.perform(get(BASE + "/detections").with(user("viewer")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].detections[0].version").value(3));
        mvc.perform(as(delete(route + "?expectedVersion=3"), "annotator")).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/detections").with(user("viewer"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(BASE + "/annotation-history").with(user("owner")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4)).andExpect(jsonPath("$[0].action").value("DELETED"));
        mvc.perform(get(BASE + "/annotation-history").with(user("annotator"))).andExpect(status().isForbidden());
    }

    @Test void manualGeometryRequiresAProjectFeatureAndValidClosedPolygons() throws Exception {
        mvc.perform(as(post(BASE + "/detections"), "annotator").content("{\"featureId\":\"sheet\",\"bounds\":" + BOUNDS + "}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(as(post(BASE + "/detections"), "annotator").content("{\"featureId\":\"other-sheet\",\"bounds\":" + BOUNDS + "}"))
            .andExpect(status().isNotFound());
        mvc.perform(as(patch(BASE + "/detections/" + DETECTION), "annotator").content("{\"expectedVersion\":0,\"bounds\":[[[[0,0],[1,1],[1,0],[0,1]]]]}"))
            .andExpect(status().isBadRequest());
    }

    @Test void typedDetectionsCanBeEditedVerifiedReadAndAudited() throws Exception {
        String route = BASE + "/detections/" + DETECTION;
        for (String type : java.util.List.of("building", "forest", "water", "road")) {
            seed();
            String bounds = type.equals("road")
                ? "{\"type\":\"road\",\"coordinates\":[[[0,0],[5,10]],[[20,0],[20,10]]],\"width_m\":[6.5,null]}"
                : "{\"type\":\"" + type + "\",\"coordinates\":" + BOUNDS + "}";
            dsl.execute("update geoportal.detections set bounds = ?::jsonb where id = ?", bounds, DETECTION);
            mvc.perform(as(patch(route + "/verification"), "annotator").content("{\"expectedVersion\":0,\"verified\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bounds.type").value(type));
            mvc.perform(as(patch(route), "annotator").content("{\"expectedVersion\":1,\"bounds\":" + bounds + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.verifiedBy").isEmpty()).andExpect(jsonPath("$.bounds.type").value(type));
            var result = mvc.perform(get(BASE + "/detections").with(user("viewer")))
                .andExpect(status().isOk()).andReturn();
            assertEquals(json.readTree(bounds), json.readTree(result.getResponse().getContentAsString()).get(0).get("detections").get(0).get("bounds"));
            var history = mvc.perform(get(BASE + "/annotation-history").with(user("owner")))
                .andExpect(status().isOk()).andReturn();
            assertEquals(json.readTree(bounds), json.readTree(history.getResponse().getContentAsString()).get(0).get("after").get("bounds"));
        }
    }

    @Test void concurrentCorrectionsCannotOverwriteEachOther() throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> correction = () -> {
                ready.countDown();
                assertTrue(start.await(5, java.util.concurrent.TimeUnit.SECONDS));
                return mvc.perform(as(patch(BASE + "/detections/" + DETECTION), "annotator").content(update(0)))
                    .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(correction);
            var second = executor.submit(correction);
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            var statuses = java.util.stream.Stream.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                second.get(10, java.util.concurrent.TimeUnit.SECONDS)).sorted().toList();
            assertEquals(java.util.List.of(200, 409), statuses);
        }
        assertEquals(1, count("geoportal.annotation_history"));
    }

    @Test void emptyModelGeometryCannotBeVerifiedButCanBeRemoved() throws Exception {
        dsl.execute("update geoportal.detections set bounds = null where id = ?", DETECTION);
        String route = BASE + "/detections/" + DETECTION;
        mvc.perform(as(patch(route + "/verification"), "annotator").content("{\"expectedVersion\":0,\"verified\":true}"))
            .andExpect(status().isBadRequest());
        mvc.perform(as(delete(route + "?expectedVersion=0"), "annotator")).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/detections").with(user("viewer"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test void featureAnnotationAndRetryRoutesCannotCrossOrganizationBoundaries() throws Exception {
        String foreign = ORGS + "/" + ORG + "/projects/" + OTHER_PROJECT;
        mvc.perform(get(foreign + "/features").with(user("viewer"))).andExpect(status().isNotFound());
        mvc.perform(as(patch(foreign + "/detections/" + DETECTION), "annotator").content(update(0))).andExpect(status().isNotFound());
        mvc.perform(as(post(foreign + "/detections/retry"), "editor")).andExpect(status().isNotFound());
        mvc.perform(as(post(foreign + "/detections/rerun"), "editor")).andExpect(status().isNotFound());
        mvc.perform(as(post(foreign + "/discovery/retry"), "editor")).andExpect(status().isNotFound());
        verifyNoInteractions(downloader);
    }

    @Test void detectionRetryRequeuesOnlyFailedJobsAndPreservesManualCorrections() throws Exception {
        mvc.perform(as(patch(BASE + "/detections/" + DETECTION), "annotator").content(update(0))).andExpect(status().isOk());
        dsl.execute("update geoportal.recognition_jobs set status = '3_FAILED', error = 'Temporary failure' where id = ?", JOB);
        mvc.perform(as(post(BASE + "/detections/retry"), "viewer")).andExpect(status().isForbidden());
        mvc.perform(as(post(BASE + "/detections/retry"), "editor"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.requeuedJobs").value(1));
        mvc.perform(as(post(BASE + "/detections/retry"), "editor"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.requeuedJobs").value(0));
        dsl.execute("delete from geoportal.detections where recognition_job_id = ?", JOB);
        mvc.perform(get(BASE + "/detections").with(user("viewer")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].detections[0].id").value(DETECTION.toString()))
            .andExpect(jsonPath("$[0].detections[0].version").value(1));
        mvc.perform(get(BASE + "/progress").with(user("viewer")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.jobStates.pending").value(1))
            .andExpect(jsonPath("$.queue.nextPosition").value(1)).andExpect(jsonPath("$.queue.queued").value(1));
    }

    @Test void detectionRerunRequiresEditorOwnerOrAdministrator() throws Exception {
        mvc.perform(post(BASE + "/detections/rerun").with(csrf())).andExpect(status().isUnauthorized());
        for (String username : new String[]{"viewer", "annotator", "free"}) {
            mvc.perform(as(post(BASE + "/detections/rerun"), username)).andExpect(status().isForbidden());
        }
        for (String username : new String[]{"editor", "owner", "admin"}) {
            dsl.execute("update geoportal.recognition_jobs set status = '2_FINISHED' where id = ?", JOB);
            mvc.perform(as(post(BASE + "/detections/rerun"), username))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requeuedJobs").value(1));
        }
    }

    @Test void detectionRerunRequeuesCompletedAndFailedJobsAndPreservesReviewedAnnotations() throws Exception {
        mvc.perform(as(patch(BASE + "/detections/" + DETECTION), "annotator").content(update(0))).andExpect(status().isOk());
        mvc.perform(as(patch(BASE + "/detections/" + DETECTION + "/verification"), "annotator")
            .content("{\"expectedVersion\":1,\"verified\":true}")).andExpect(status().isOk());
        dsl.execute("insert into geoportal.project_features (project_id, feature_id) values (?, 'other-sheet')", PROJECT);
        UUID failed = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        dsl.execute("""
            insert into geoportal.recognition_jobs (id, project_id, feature_id, status, finished_at, error)
            values (?, ?, 'other-sheet', '3_FAILED', now(), 'Temporary failure'),
                   (?, ?, 'other-sheet', '3_FAILED', now(), 'Foreign failure')
            """, failed, PROJECT, foreign, OTHER_PROJECT);
        dsl.execute("update geoportal.recognition_jobs set finished_at = now() where id = ?", JOB);
        mvc.perform(as(post(BASE + "/detections/rerun"), "editor"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.requeuedJobs").value(2));
        assertEquals(2, dsl.fetchOne("""
            select count(*) as count from geoportal.recognition_jobs
            where project_id = ? and status = '0_PENDING' and finished_at is null and error is null
            """, PROJECT).get("count", Integer.class));
        assertEquals("3_FAILED", dsl.fetchOne("select status::text as status from geoportal.recognition_jobs where id = ?", foreign).get("status", String.class));
        assertEquals(1, count("geoportal.detections"));
        assertEquals(1, count("geoportal.detection_annotations"));
        assertEquals(2, count("geoportal.annotation_history"));
        mvc.perform(as(post(BASE + "/detections/rerun"), "editor")).andExpect(status().isConflict());
        // The worker replaces raw output when it starts; reviewed annotations survive that replacement.
        dsl.execute("delete from geoportal.detections where recognition_job_id = ?", JOB);
        mvc.perform(get(BASE + "/detections").with(user("viewer"))).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].detections[0].id").value(DETECTION.toString()))
            .andExpect(jsonPath("$[0].detections[0].verifiedBy").value("annotator"));
        verifyNoInteractions(downloader);
    }

    @Test void detectionRerunRejectsPendingRunningOrDownloadingWork() throws Exception {
        for (String status : new String[]{"0_PENDING", "1_STARTED"}) {
            dsl.execute("update geoportal.recognition_jobs set status = ?::geoportal.recognition_job_status where id = ?", status, JOB);
            mvc.perform(as(post(BASE + "/detections/rerun"), "editor")).andExpect(status().isConflict());
            assertEquals(status, dsl.fetchOne("select status::text as status from geoportal.recognition_jobs where id = ?", JOB).get("status", String.class));
        }
        dsl.execute("update geoportal.recognition_jobs set status = '2_FINISHED' where id = ?", JOB);
        dsl.execute("insert into geoportal.project_features (project_id, feature_id) values (?, 'other-sheet')", PROJECT);
        dsl.execute("update geoportal.features set downloaded_at = null where id = 'other-sheet'");
        mvc.perform(as(post(BASE + "/detections/rerun"), "editor")).andExpect(status().isConflict());
    }

    @Test void detectionRerunRejectsActiveDiscoveryAndProjectsWithoutJobs() throws Exception {
        dsl.execute("insert into project_processing (project_id, discovery_status, locked_until) values (?, 'RUNNING', now() + interval '15 minutes')", PROJECT);
        mvc.perform(as(post(BASE + "/detections/rerun"), "editor")).andExpect(status().isConflict());
        dsl.execute("update project_processing set discovery_status = 'SUCCEEDED', locked_until = null where project_id = ?", PROJECT);
        dsl.execute("delete from geoportal.recognition_jobs where project_id = ?", PROJECT);
        mvc.perform(as(post(BASE + "/detections/rerun"), "editor")).andExpect(status().isConflict());
    }

    @Test void discoveryFailureCanBeRetriedAndSuccessfulDiscoveryCannotBeRepeated() throws Exception {
        doThrow(new IllegalStateException("temporary failure")).doNothing().when(downloader).discoverFeatures(PROJECT);
        mvc.perform(as(post(BASE + "/discovery/retry"), "editor")).andExpect(status().isBadGateway());
        mvc.perform(get(BASE + "/progress").with(user("viewer")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.discovery.status").value("FAILED"));
        mvc.perform(as(post(BASE + "/discovery/retry"), "editor"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED")).andExpect(jsonPath("$.attempts").value(2));
        mvc.perform(as(post(BASE + "/discovery/retry"), "editor")).andExpect(status().isConflict());
        verify(downloader, times(2)).discoverFeatures(PROJECT);
    }

    @Test void activeDiscoveryCannotBeStartedTwice() throws Exception {
        dsl.execute("insert into project_processing (project_id, discovery_status, locked_until) values (?, 'RUNNING', now() + interval '15 minutes')", PROJECT);
        mvc.perform(as(post(BASE + "/discovery/retry"), "editor")).andExpect(status().isConflict());
        verifyNoInteractions(downloader);
    }

    @Test void projectCreationStartsDiscoveryAndKeepsFailedProjectsRecoverable() throws Exception {
        String payload = "{\"name\":\"New area\",\"topLeft\":{\"longitude\":16,\"latitude\":51},\"bottomRight\":{\"longitude\":16.01,\"latitude\":50.99}}";
        var result = mvc.perform(as(post(ORGS + "/" + ORG + "/projects"), "editor").content(payload)).andExpect(status().isCreated()).andReturn();
        UUID created = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
        verify(downloader).discoverFeatures(created);
        doThrow(new IllegalStateException("offline")).when(downloader).discoverFeatures(any());
        mvc.perform(as(post(ORGS + "/" + ORG + "/projects"), "editor").content(payload)).andExpect(status().isBadGateway());
        assertEquals(2, dsl.fetchOne("select count(*) as count from projects where name = 'New area'").get("count", Integer.class));
    }

    @Test void organizationDeletionCascadesProjectDataButRetainsAccountsAndSharedImagery() throws Exception {
        mvc.perform(as(patch(BASE + "/detections/" + DETECTION), "annotator").content(update(0))).andExpect(status().isOk());
        mvc.perform(as(post(BASE + "/discovery/retry"), "editor")).andExpect(status().isOk());
        mvc.perform(as(delete(ORGS + "/" + ORG), "admin")).andExpect(status().isNoContent());
        assertEquals(0, count("geoportal.recognition_jobs"));
        assertEquals(0, count("geoportal.detections"));
        assertEquals(0, count("geoportal.detection_annotations"));
        assertEquals(0, count("geoportal.annotation_history"));
        assertEquals(0, count("project_processing"));
        assertEquals(0, count("memberships"));
        assertEquals(6, count("users"));
        assertEquals(2, count("geoportal.features"));
        assertEquals(1, count("projects"));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String username) {
        return request.with(user(username).roles(username.equals("admin") ? "ADMIN" : "USER"))
            .with(csrf()).contentType(MediaType.APPLICATION_JSON);
    }
    private UUID userId(String username) {
        return dsl.fetchOne("select id from users where username = ?", username).get("id", UUID.class);
    }
    private String update(int version) { return "{\"expectedVersion\":" + version + ",\"bounds\":" + BOUNDS + "}"; }
    private int count(String table) { return dsl.fetchOne("select count(*) as count from " + table).get("count", Integer.class); }
    private Cookie login(String username) throws Exception {
        var response = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"Password123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse();
        Cookie cookie = response.getCookie("SESSION");
        assertNotNull(cookie);
        return cookie;
    }
}
