package com.amdocs.telecom.model;

public class AppRole {
    private Short roleId;
    private RoleCode roleCode;
    private String roleName;

    public AppRole() { }

    public AppRole(RoleCode roleCode, String roleName) {
        this.roleCode = roleCode;
        this.roleName = roleName;
    }

    public Short getRoleId() { return roleId; }
    public void setRoleId(Short roleId) { this.roleId = roleId; }
    public RoleCode getRoleCode() { return roleCode; }
    public void setRoleCode(RoleCode roleCode) { this.roleCode = roleCode; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    @Override
    public String toString() {
        return "AppRole{roleId=" + roleId + ", roleCode=" + roleCode + ", roleName='" + roleName + "'}";
    }
}
