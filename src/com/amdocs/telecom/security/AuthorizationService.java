package com.amdocs.telecom.security;

import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.model.RoleCode;

/** Contract for enforcing role-based access authorization. */
public interface AuthorizationService {
    boolean hasRole(UserSession session, RoleCode requiredRole);
    boolean hasAnyRole(UserSession session, RoleCode... requiredRoles);
    void checkAccess(UserSession session, RoleCode requiredRole) throws AccessDeniedException;
    void checkAccessAny(UserSession session, RoleCode... requiredRoles) throws AccessDeniedException;
}
