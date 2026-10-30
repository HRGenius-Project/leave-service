package com.hrgenius.leave_service.service;

import com.hrgenius.leave_service.dto.LeaveApplication;
import com.hrgenius.leave_service.dto.LeaveResponse;
import com.hrgenius.leave_service.entity.LeaveBalance;
import com.hrgenius.leave_service.entity.LeaveRequest;
import com.hrgenius.leave_service.entity.LeaveStatus;
import com.hrgenius.leave_service.repository.LeaveBalanceRepository;
import com.hrgenius.leave_service.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;

    @Transactional(readOnly = true)
    public List<LeaveResponse> getAllPendingLeaves() {
        return leaveRequestRepository.findByStatusOrderByAppliedAtDesc(LeaveStatus.PENDING)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> getEmployeeLeaves(String employeeCode) {
        return leaveRequestRepository.findByEmployeeCodeOrderByAppliedAtDesc(employeeCode)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LeaveBalance getBalance(String employeeCode) {
        return leaveBalanceRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave balance record not found"));
    }

    @Transactional
    public LeaveResponse applyLeave(LeaveApplication dto) {
        if (dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date cannot be after end date");
        }

        boolean hasOverlap = leaveRequestRepository.hasOverlappingLeave(
                dto.getEmployeeCode(),
                dto.getStartDate(),
                dto.getEndDate()
        );
        if (hasOverlap) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An overlapping leave request already exists");
        }

        long days = ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1;

        LeaveRequest request = LeaveRequest.builder()
                .employeeCode(dto.getEmployeeCode())
                .leaveType(dto.getLeaveType())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .numberOfDays((int) days)
                .reason(dto.getReason())
                .status(LeaveStatus.PENDING)
                .build();

        LeaveRequest saved = leaveRequestRepository.save(request);
        return mapToResponse(saved);
    }

    @Transactional
    public LeaveResponse updateLeaveStatus(Long id, LeaveStatus status, String approverCode) {
        LeaveRequest request = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request not found"));

        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending leaves can be approved or rejected");
        }

        request.setStatus(status);
        request.setApprovedBy(approverCode);

        if (status == LeaveStatus.APPROVED) {
            leaveBalanceRepository.findByEmployeeCode(request.getEmployeeCode()).ifPresent(balance -> {
                deductLeaveBalance(balance, request.getLeaveType().name(), request.getNumberOfDays());
                leaveBalanceRepository.save(balance);
            });
        }

        LeaveRequest updated = leaveRequestRepository.save(request);
        return mapToResponse(updated);
    }

    private void deductLeaveBalance(LeaveBalance balance, String type, int days) {
        switch (type.toUpperCase()) {
            case "CASUAL", "CASUAL_LEAVE" -> {
                int current = balance.getCasualLeave() != null ? balance.getCasualLeave() : 0;
                balance.setCasualLeave(Math.max(0, current - days));
            }
            case "SICK", "SICK_LEAVE" -> {
                int current = balance.getSickLeave() != null ? balance.getSickLeave() : 0;
                balance.setSickLeave(Math.max(0, current - days));
            }
            case "PAID", "PAID_LEAVE", "EARNED", "EARNED_LEAVE" -> {
                int current = balance.getPaidLeave() != null ? balance.getPaidLeave() : 0;
                balance.setPaidLeave(Math.max(0, current - days));
            }
            default -> {}
        }
    }

    private LeaveResponse mapToResponse(LeaveRequest lr) {
        return LeaveResponse.builder()
                .id(lr.getId())
                .employeeCode(lr.getEmployeeCode())
                .leaveType(lr.getLeaveType())
                .startDate(lr.getStartDate())
                .endDate(lr.getEndDate())
                .numberOfDays(lr.getNumberOfDays())
                .reason(lr.getReason())
                .status(lr.getStatus())
                .approvedBy(lr.getApprovedBy())
                .appliedAt(lr.getAppliedAt())
                .build();
    }
}