package com.hrgenius.leave_service.dto;

import com.hrgenius.leave_service.entity.LeaveStatus;
import com.hrgenius.leave_service.entity.LeaveType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class LeaveResponse {
    private Long id;
    private String employeeCode;
    private LeaveType leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer numberOfDays;
    private String reason;
    private LeaveStatus status;
    private String approvedBy;
    private LocalDateTime appliedAt;
}