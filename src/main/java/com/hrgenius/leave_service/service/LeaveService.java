package com.hrgenius.leave_service.service;

import com.hrgenius.leave_service.dto.LeaveApplication;
import com.hrgenius.leave_service.dto.LeaveApprovedEvent;
import com.hrgenius.leave_service.dto.LeaveResponse;
import com.hrgenius.leave_service.entity.*;
import com.hrgenius.leave_service.repository.LeaveBalanceRepository;
import com.hrgenius.leave_service.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.routing-key-leave-approved}")
    private String routingKeyLeaveApproved;

    public LeaveBalance initializeBalance(String employeeCode) {
        return leaveBalanceRepository.findByEmployeeCode(employeeCode)
                .orElseGet(() -> leaveBalanceRepository.save(
                        LeaveBalance.builder()
                                .employeeCode(employeeCode)
                                .casualLeave(12)
                                .sickLeave(10)
                                .paidLeave(15)
                                .build()
                ));
    }

    public LeaveBalance getBalance(String employeeCode) {
        return leaveBalanceRepository.findByEmployeeCode(employeeCode)
                .orElseGet(() -> initializeBalance(employeeCode));
    }

    @Transactional
    public LeaveResponse applyLeave(LeaveApplication dto) {
        if (dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }

        int requestedDays = (int) ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1;
        LeaveBalance balance = getBalance(dto.getEmployeeCode());

        boolean hasEnoughBalance = switch (dto.getLeaveType()) {
            case CASUAL -> balance.getCasualLeave() >= requestedDays;
            case SICK -> balance.getSickLeave() >= requestedDays;
            case PAID -> balance.getPaidLeave() >= requestedDays;
        };

        if (!hasEnoughBalance) {
            throw new IllegalStateException("Insufficient " + dto.getLeaveType() + " leave balance");
        }

        LeaveRequest leaveRequest = LeaveRequest.builder()
                .employeeCode(dto.getEmployeeCode())
                .leaveType(dto.getLeaveType())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .numberOfDays(requestedDays)
                .reason(dto.getReason())
                .status(LeaveStatus.PENDING)
                .build();

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        return mapToResponse(saved);
    }

    @Transactional
    public LeaveResponse updateLeaveStatus(Long leaveId, LeaveStatus newStatus, String approverCode) {
        LeaveRequest request = leaveRequestRepository.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + leaveId));

        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalStateException("Leave request is already processed: " + request.getStatus());
        }

        request.setStatus(newStatus);
        request.setApprovedBy(approverCode);

        if (newStatus == LeaveStatus.APPROVED) {
            LeaveBalance balance = getBalance(request.getEmployeeCode());
            switch (request.getLeaveType()) {
                case CASUAL -> balance.setCasualLeave(balance.getCasualLeave() - request.getNumberOfDays());
                case SICK -> balance.setSickLeave(balance.getSickLeave() - request.getNumberOfDays());
                case PAID -> balance.setPaidLeave(balance.getPaidLeave() - request.getNumberOfDays());
            }
            leaveBalanceRepository.save(balance);

            // RabbitMQ Event Publish
            LeaveApprovedEvent event = LeaveApprovedEvent.builder()
                    .leaveRequestId(request.getId())
                    .employeeCode(request.getEmployeeCode())
                    .leaveType(request.getLeaveType().name())
                    .startDate(request.getStartDate())
                    .endDate(request.getEndDate())
                    .numberOfDays(request.getNumberOfDays())
                    .approvedBy(approverCode)
                    .build();

            rabbitTemplate.convertAndSend(exchange, routingKeyLeaveApproved, event);
            log.info("Published leave.approved event for employee: {}", request.getEmployeeCode());
        }

        return mapToResponse(leaveRequestRepository.save(request));
    }

    public List<LeaveResponse> getEmployeeLeaves(String employeeCode) {
        return leaveRequestRepository.findByEmployeeCodeOrderByAppliedAtDesc(employeeCode)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private LeaveResponse mapToResponse(LeaveRequest request) {
        return LeaveResponse.builder()
                .id(request.getId())
                .employeeCode(request.getEmployeeCode())
                .leaveType(request.getLeaveType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .numberOfDays(request.getNumberOfDays())
                .reason(request.getReason())
                .status(request.getStatus())
                .approvedBy(request.getApprovedBy())
                .appliedAt(request.getAppliedAt())
                .build();
    }
}