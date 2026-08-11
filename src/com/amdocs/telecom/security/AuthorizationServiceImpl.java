package com.amdocs.telecom.security;

import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.model.RoleCode;

/** Implementation of role-based authorization rules. */
public class AuthorizationServiceImpl implements AuthorizationService {

    @Override
    public boolean hasRole(UserSession session, RoleCode requiredRole) {
        return session != null && session.hasRole(requiredRole);
    }

    @Override
    public boolean hasAnyRole(UserSession session, RoleCode... requiredRoles) {
        return session != null && session.hasAnyRole(requiredRoles);
    }

    @Override
    public void checkAccess(UserSession session, RoleCode requiredRole) throws AccessDeniedException {
        if (session == null || !session.isActive()) {
            throw new AccessDeniedException("Access denied: Unauthenticated or inactive session.");
        }
        if (!session.hasRole(requiredRole)) {
            throw new AccessDeniedException("Access denied: User '" + session.getUsername()
                    + "' does not possess required role '" + requiredRole + "'.");
        }
    }

    @Override
    public void checkAccessAny(UserSession session, RoleCode... requiredRoles) throws AccessDeniedException {
        if (session == null || !session.isActive()) {
            throw new AccessDeniedException("Access denied: Unauthenticated or inactive session.");
        }
        if (!session.hasAnyRole(requiredRoles)) {
            throw new AccessDeniedException("Access denied: User '" + session.getUsername()
                    + "' lacks required role permissions.");
        }
    }
}
