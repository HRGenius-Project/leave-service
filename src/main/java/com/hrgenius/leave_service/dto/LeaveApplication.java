package com.hrgenius.leave_service.dto;

import com.hrgenius.leave_service.entity.LeaveType;
import lombok.Data;

import java.time.LocalDate;

@Data
public class LeaveApplication {
    private String employeeCode;
    private LeaveType leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
}