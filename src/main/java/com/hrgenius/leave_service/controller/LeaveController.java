package com.hrgenius.leave_service.controller;

import com.hrgenius.leave_service.dto.LeaveApplication;
import com.hrgenius.leave_service.dto.LeaveResponse;
import com.hrgenius.leave_service.entity.LeaveBalance;
import com.hrgenius.leave_service.entity.LeaveStatus;
import com.hrgenius.leave_service.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    @PostMapping("/apply")
    public ResponseEntity<LeaveResponse> applyLeave(@RequestBody LeaveApplication dto) {
        return ResponseEntity.ok(leaveService.applyLeave(dto));
    }

    @GetMapping("/balance/{employeeCode}")
    public ResponseEntity<LeaveBalance> getBalance(@PathVariable String employeeCode) {
        return ResponseEntity.ok(leaveService.getBalance(employeeCode));
    }

    @GetMapping("/history/{employeeCode}")
    public ResponseEntity<List<LeaveResponse>> getHistory(@PathVariable String employeeCode) {
        return ResponseEntity.ok(leaveService.getEmployeeLeaves(employeeCode));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<LeaveResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam LeaveStatus status,
            @RequestParam String approverCode) {
        return ResponseEntity.ok(leaveService.updateLeaveStatus(id, status, approverCode));
    }
}