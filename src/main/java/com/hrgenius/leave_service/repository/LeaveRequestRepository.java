package com.hrgenius.leave_service.repository;

import com.hrgenius.leave_service.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployeeCodeOrderByAppliedAtDesc(String employeeCode);
}