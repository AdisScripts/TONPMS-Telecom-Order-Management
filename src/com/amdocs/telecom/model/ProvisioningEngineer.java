package com.amdocs.telecom.model;

import java.util.ArrayList;
import java.util.List;

public class ProvisioningEngineer {
    private Long engineerId;
    private String employeeCode;
    private String engineerName;
    private String specialization;
    private String region;
    private EngineerAvailability availability;
    private Integer activeTasks;
    private Integer experienceYears;
    private Long userId;
    private AppUser user;
    private List<ProvisioningRequest> provisioningRequests = new ArrayList<ProvisioningRequest>();

    public ProvisioningEngineer() { }

    public ProvisioningEngineer(String employeeCode, String engineerName, String specialization, String region) {
        this.employeeCode = employeeCode;
        this.engineerName = engineerName;
        this.specialization = specialization;
        this.region = region;
    }

    public Long getEngineerId() { return engineerId; }
    public void setEngineerId(Long engineerId) { this.engineerId = engineerId; }
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    public String getEngineerName() { return engineerName; }
    public void setEngineerName(String engineerName) { this.engineerName = engineerName; }
    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public EngineerAvailability getAvailability() { return availability; }
    public void setAvailability(EngineerAvailability availability) { this.availability = availability; }
    public Integer getActiveTasks() { return activeTasks; }
    public void setActiveTasks(Integer activeTasks) { this.activeTasks = activeTasks; }
    public Integer getExperienceYears() { return experienceYears; }
    public void setExperienceYears(Integer experienceYears) { this.experienceYears = experienceYears; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public List<ProvisioningRequest> getProvisioningRequests() { return provisioningRequests; }
    public void setProvisioningRequests(List<ProvisioningRequest> provisioningRequests) { this.provisioningRequests = provisioningRequests; }

    @Override
    public String toString() {
        return "ProvisioningEngineer{engineerId=" + engineerId + ", employeeCode='" + employeeCode
                + "', engineerName='" + engineerName + "', availability=" + availability + "}";
    }
}
