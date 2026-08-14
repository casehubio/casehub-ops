package io.casehub.ops.app.rest;

import io.casehub.ops.app.entity.ApplicationEntity;
import io.casehub.ops.app.rest.dto.ScaleServiceRequest;
import io.casehub.ops.app.service.ScalingService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Blocking
@ApplicationScoped
@Path("/api/applications/{appId}/services/{serviceId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ScalingResource {

    private final ScalingService scalingService;

    @Inject
    public ScalingResource(ScalingService scalingService) {
        this.scalingService = scalingService;
    }

    ScalingResource() {
        this.scalingService = null;
    }

    @POST
    @Path("/scale")
    public Response scale(@PathParam("appId") UUID appId,
                          @PathParam("serviceId") String serviceId,
                          ScaleServiceRequest request) {
        var app = ApplicationEntity.<ApplicationEntity>findById(appId);
        if (app == null) return Response.status(Response.Status.NOT_FOUND).build();

        return scalingService.scale(app.id.toString(), app.engineCaseId, app.status,
                                    app.servicesJson, serviceId, request);
    }
}
