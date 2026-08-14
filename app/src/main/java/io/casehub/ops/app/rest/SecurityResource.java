package io.casehub.ops.app.rest;

import java.util.Map;
import java.util.UUID;

import io.casehub.ops.app.entity.ApplicationEntity;
import io.casehub.ops.app.model.CveEvent;
import io.casehub.ops.app.model.CveRecord;
import io.casehub.ops.app.model.CveStatus;
import io.casehub.ops.app.persistence.CveStore;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Blocking
@ApplicationScoped
@Path("/api/applications/{id}/security")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SecurityResource {

    @Inject
    CveStore cveStore;

    @Inject
    io.casehub.api.engine.CaseHubRuntime caseHubRuntime;

    @GET
    @Path("/cves")
    public Response getCves(@PathParam("id") UUID id) {
        return Response.ok(cveStore.findByApplicationId(id)).build();
    }

    @POST
    @Path("/cves")
    public Response scanCves(@PathParam("id") UUID id, CveEvent cveEvent) {
        var app = ApplicationEntity.<ApplicationEntity>findById(id);
        if (app == null) return Response.status(Response.Status.NOT_FOUND).build();

        var record = new CveRecord(cveEvent.cveId(), cveEvent.severity(),
                cveEvent.affectedImage(), cveEvent.affectedServices(),
                cveEvent.fixedInTag(), CveStatus.DETECTED, id,
                app.tenancyId, java.time.Instant.now());
        cveStore.store(record);

        if (app.engineCaseId != null) {
            caseHubRuntime.signal(app.engineCaseId, "cveDetected", Map.of(
                    "cveId", cveEvent.cveId(),
                    "severity", cveEvent.severity().name(),
                    "affectedImage", cveEvent.affectedImage(),
                    "affectedServices", cveEvent.affectedServices(),
                    "fixedInTag", cveEvent.fixedInTag() != null ? cveEvent.fixedInTag() : "",
                    "applicationId", id.toString(),
                    "tenancyId", app.tenancyId));
        }

        return Response.accepted().build();
    }

    @GET
    @Path("/posture")
    public Response getPosture(@PathParam("id") UUID id) {
        var cves = cveStore.findByApplicationId(id);
        long detected = cves.stream().filter(c -> c.status() == CveStatus.DETECTED).count();
        long remediating = cves.stream().filter(c -> c.status() == CveStatus.REMEDIATING).count();
        long resolved = cves.stream().filter(c -> c.status() == CveStatus.RESOLVED).count();
        long escalated = cves.stream().filter(c -> c.status() == CveStatus.ESCALATED).count();

        return Response.ok(Map.of(
                "totalCves", cves.size(),
                "detected", detected,
                "remediating", remediating,
                "resolved", resolved,
                "escalated", escalated)).build();
    }
}
