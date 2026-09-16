package io.casehub.ops.app.rest;

import java.util.UUID;

import io.casehub.ops.app.entity.ApplicationEntity;
import io.casehub.ops.app.rest.dto.CreateApplicationRequest;
import io.casehub.ops.app.service.ApplicationLifecycleService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Blocking
@ApplicationScoped
@Path("/api/applications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ApplicationResource {

    @Inject
    ApplicationLifecycleService lifecycleService;

    @Inject
    EntityManager em;

    @POST
    public Response create(CreateApplicationRequest request,
                           @Context ContainerRequestContext ctx) {
        String tenancyId = (String) ctx.getProperty(TenancyFilter.TENANCY_PROPERTY);
        var app = lifecycleService.createDraft(
                request.name(), request.description(), request.servicesJson(), tenancyId);
        return Response.status(Response.Status.CREATED).entity(app).build();
    }

    @GET
    public Response list(@Context ContainerRequestContext ctx) {
        String tenancyId = (String) ctx.getProperty(TenancyFilter.TENANCY_PROPERTY);
        return Response.ok(em.createNamedQuery("ApplicationEntity.findByTenancyId", ApplicationEntity.class)
                .setParameter("tenancyId", tenancyId).getResultList()).build();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") UUID id) {
        var app = em.find(ApplicationEntity.class, id);
        if (app == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(app).build();
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") UUID id, CreateApplicationRequest request) {
        var app = em.find(ApplicationEntity.class, id);
        if (app == null) return Response.status(Response.Status.NOT_FOUND).build();

        if (request.name() != null) app.name = request.name();
        if (request.description() != null) app.description = request.description();
        if (request.servicesJson() != null) app.servicesJson = request.servicesJson();
        em.merge(app);
        return Response.ok(app).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") UUID id,
                           @Context ContainerRequestContext ctx) {
        String tenancyId = (String) ctx.getProperty(TenancyFilter.TENANCY_PROPERTY);
        lifecycleService.decommission(id, tenancyId);
        return Response.accepted().build();
    }
}
