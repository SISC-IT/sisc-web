package org.sejongisc.backend.admin.dto.dashboard;

public record RoleDistributionResponse(
    String roleName,
    long count
) {}