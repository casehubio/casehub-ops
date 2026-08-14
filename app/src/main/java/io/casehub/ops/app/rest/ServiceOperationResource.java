package io.casehub.ops.app.rest;

import java.util.Map;
import java.util.UUID;

import io.casehub.ops.app.entity.ApplicationEntity;
import io.casehub.ops.app.lifecycle.ServiceCaseRegistry;
import io.casehub.ops.app.rest.dto.ScaleServiceRequest;
import io.casehub.ops.app.service.ScalingService;
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
@Path("/api/applications/{id}/services/{serviceId}/ops")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ServiceOperationResource {

    @Inject
    ServiceCaseRegistry serviceCaseRegistry;

    @Inject
    ScalingService scalingService;

    @Inject
    io.casehub.api.engine.CaseHubRuntime caseHubRuntime;

    @GET
    @Path("/status")
    public Response getStatus(@PathParam("id") UUID id,
                              @PathParam("serviceId") String serviceId) {
        var ctx = serviceCaseRegistry.getByServiceId(serviceId);
        if (ctx == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(Map.of(
                "serviceId", serviceId,
                "serviceName", ctx.serviceName(),
                "category", ctx.category().name(),
                "dimensions", ctx.dimensions())).build();
    }

    @POST
    @Path("/scale")
    public Response scale(@PathParam("id") UUID id,
                          @PathParam("serviceId") String serviceId,
                          ScaleServiceRequest request) {
        var app = ApplicationEntity.<ApplicationEntity>findById(id);
        if (app == null) return Response.status(Response.Status.NOT_FOUND).build();

        return scalingService.scale(app.id.toString(), app.engineCaseId, app.status,
                                    app.servicesJson, serviceId, request);
    }

    @POST
    @Path("/upgrade")
    public Response upgrade(@PathParam("id") UUID id,
                            @PathParam("serviceId") String serviceId,
                            Map<String, String> request) {
        var app = ApplicationEntity.<ApplicationEntity>findById(id);
        if (app == null) return Response.status(Response.Status.NOT_FOUND).build();
        if (app.engineCaseId == null) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "No active case for application")).build();
        }

        String newImage = request != null ? request.get("newImage") : null;
        if (newImage == null || newImage.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "newImage is required")).build();
        }

        caseHubRuntime.signal(app.engineCaseId, "upgradeRequested", Map.of(
                "serviceId", serviceId,
                "newImage", newImage,
                "applicationId", id.toString(),
                "tenancyId", app.tenancyId));

        return Response.accepted(Map.of("status", "upgrade-signalled")).build();
    }
}
