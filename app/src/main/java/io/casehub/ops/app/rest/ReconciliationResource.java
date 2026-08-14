package io.casehub.ops.app.rest;

import java.util.Map;
import java.util.UUID;

import io.casehub.ops.app.entity.ApplicationEntity;
import io.casehub.ops.app.service.ApplicationEventBroadcaster;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Blocking
@ApplicationScoped
@Path("/api/applications/{id}/reconciliation")
@Produces(MediaType.APPLICATION_JSON)
public class ReconciliationResource {

    @Inject
    io.casehub.desiredstate.runtime.ReconciliationLoop reconciliationLoop;

    @Inject
    ApplicationEventBroadcaster broadcaster;

    @GET
    @Path("/status")
    public Response getStatus(@PathParam("id") UUID id,
                              @Context ContainerRequestContext ctx) {
        String tenancyId = (String) ctx.getProperty(TenancyFilter.TENANCY_PROPERTY);
        var app = ApplicationEntity.<ApplicationEntity>findById(id);
        if (app == null) return Response.status(Response.Status.NOT_FOUND).build();

        var clusters = io.casehub.ops.app.entity.ClusterReferenceEntity.list(
                "tenancyId", tenancyId);
        var statuses = new java.util.ArrayList<Map<String, Object>>();
        for (var cluster : clusters) {
            var clusterRef = (io.casehub.ops.app.entity.ClusterReferenceEntity) cluster;
            String key = tenancyId + ":" + id + ":" + clusterRef.id;
            var desired = reconciliationLoop.getDesired(key);
            statuses.add(Map.of(
                    "clusterId", clusterRef.id.toString(),
                    "clusterName", clusterRef.name,
                    "active", desired != null,
                    "nodeCount", desired != null ? desired.nodes().size() : 0));
        }
        return Response.ok(Map.of("clusters", statuses)).build();
    }

    @POST
    @Path("/trigger")
    public Response trigger(@PathParam("id") UUID id) {
        return Response.accepted(Map.of("status", "triggered")).build();
    }

    @GET
    @Path("/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Response streamEvents(@PathParam("id") UUID id,
                                 @HeaderParam("Last-Event-ID") String lastEventId) {
        return Response.ok(Map.of(
                "type", "sse",
                "applicationId", id,
                "filter", "RECONCILIATION",
                "bufferSize", broadcaster.bufferSize(id))).build();
    }
}
