package com.hrgenius.leave_service.repository;

import com.hrgenius.leave_service.entity.LeaveRequest;
import com.hrgenius.leave_service.entity.LeaveStatus;
import com.hrgenius.leave_service.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmployeeCodeOrderByAppliedAtDesc(String employeeCode);

    List<LeaveRequest> findByStatusOrderByAppliedAtDesc(LeaveStatus status);

    // Overlapping Leave Check (Double-booking rokne ke liye)
    @Query("""
        SELECT COUNT(lr) > 0 FROM LeaveRequest lr
        WHERE lr.employeeCode = :employeeCode
        AND lr.status != 'REJECTED'
        AND ((:startDate BETWEEN lr.startDate AND lr.endDate)
             OR (:endDate BETWEEN lr.startDate AND lr.endDate)
             OR (lr.startDate BETWEEN :startDate AND :endDate))
    """)
    boolean hasOverlappingLeave(@Param("employeeCode") String employeeCode,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate);

    // Payroll Integration: Monthly Unpaid/LOP Leave days count karne ke liye
    @Query("""
        SELECT COALESCE(SUM(lr.numberOfDays), 0) FROM LeaveRequest lr
        WHERE lr.employeeCode = :employeeCode
        AND lr.leaveType = :leaveType
        AND lr.status = 'APPROVED'
        AND lr.startDate >= :startDate AND lr.endDate <= :endDate
    """)
    Integer countApprovedDaysByTypeAndDateRange(@Param("employeeCode") String employeeCode,
                                                @Param("leaveType") LeaveType leaveType,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);
}