package io.casehub.ops.app.rest;

import java.util.List;
import java.util.UUID;

import io.casehub.ops.app.rest.dto.DeployRequest;
import io.casehub.ops.app.service.ApplicationLifecycleService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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
@Path("/api/applications/{id}/deployments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeploymentResource {

    @Inject
    ApplicationLifecycleService lifecycleService;

    @POST
    public Response deploy(@PathParam("id") UUID id,
                           DeployRequest request,
                           @Context ContainerRequestContext ctx) {
        String tenancyId = (String) ctx.getProperty(TenancyFilter.TENANCY_PROPERTY);
        lifecycleService.deploy(id, tenancyId);
        return Response.accepted().build();
    }

    @GET
    public Response listDeployments(@PathParam("id") UUID id) {
        return Response.ok(io.casehub.ops.app.entity.DeploymentRecordEntity.findByApplicationId(id)).build();
    }

    @GET
    @Path("/current")
    public Response getCurrentDeployment(@PathParam("id") UUID id) {
        var records = io.casehub.ops.app.entity.DeploymentRecordEntity.findByApplicationId(id);
        if (records.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
        var latest = records.stream()
                .max(java.util.Comparator.comparing(r -> r.createdAt))
                .orElse(null);
        return Response.ok(latest).build();
    }

    @POST
    @Path("/rollback")
    public Response rollback(@PathParam("id") UUID id,
                             @Context ContainerRequestContext ctx) {
        String tenancyId = (String) ctx.getProperty(TenancyFilter.TENANCY_PROPERTY);
        var app = io.casehub.ops.app.entity.ApplicationEntity.<io.casehub.ops.app.entity.ApplicationEntity>findById(id);
        if (app == null) return Response.status(Response.Status.NOT_FOUND).build();

        var records = io.casehub.ops.app.entity.DeploymentRecordEntity.findByApplicationId(id);
        var lastSuccess = records.stream()
                .filter(r -> r.outcome == io.casehub.ops.app.model.DeploymentOutcome.SUCCESS)
                .max(java.util.Comparator.comparing(r -> r.createdAt))
                .orElse(null);
        if (lastSuccess == null) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(java.util.Map.of("error", "No successful deployment to rollback to")).build();
        }

        lifecycleService.rollbackToDeployment(id, lastSuccess.id, tenancyId);
        return Response.accepted().build();
    }
}
