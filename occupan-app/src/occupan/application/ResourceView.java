package occupan.application;

import occupan.AssignmentService.Status;
import occupan.domain.Resource;
import occupan.domain.ResourceType;

/** A resource's details joined with its current status from the tested unit. */
public record ResourceView(String resourceId, ResourceType type, String location, Status status,
                           boolean retired) {
    static ResourceView of(Resource resource, Status status) {
        return new ResourceView(resource.resourceId(), resource.type(), resource.location(), status,
                resource.retired());
    }
}
