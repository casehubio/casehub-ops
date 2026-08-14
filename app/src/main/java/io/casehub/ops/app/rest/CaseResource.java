package io.casehub.ops.app.rest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.casehub.ops.app.entity.ApplicationEntity;
import io.casehub.ops.app.service.ApplicationEventBroadcaster;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Blocking
@ApplicationScoped
@Path("/api/applications/{id}/cases")
@Produces(MediaType.APPLICATION_JSON)
public class CaseResource {

    @Inject
    io.casehub.api.engine.CaseHubRuntime caseHubRuntime;

    @Inject
    ApplicationEventBroadcaster broadcaster;

    @GET
    public Response listCases(@PathParam("id") UUID id) {
        var app = ApplicationEntity.<ApplicationEntity>findById(id);
        if (app == null) return Response.status(Response.Status.NOT_FOUND).build();
        if (app.engineCaseId == null) return Response.ok(List.of()).build();

        var eventLog = caseHubRuntime.eventLog(app.engineCaseId);
        return Response.ok(Map.of(
                "engineCaseId", app.engineCaseId,
                "eventCount", eventLog.size(),
                "events", eventLog)).build();
    }

    @GET
    @Path("/{caseId}")
    public Response getCase(@PathParam("id") UUID id,
                            @PathParam("caseId") UUID caseId) {
        try {
            var eventLog = caseHubRuntime.eventLog(caseId);
            return Response.ok(Map.of(
                    "caseId", caseId,
                    "eventCount", eventLog.size(),
                    "events", eventLog)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Response streamEvents(@PathParam("id") UUID id,
                                 @HeaderParam("Last-Event-ID") String lastEventId) {
        return Response.ok(Map.of(
                "type", "sse",
                "applicationId", id,
                "filter", "CASE",
                "bufferSize", broadcaster.bufferSize(id))).build();
    }
}
