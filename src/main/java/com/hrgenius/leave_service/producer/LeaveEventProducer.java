package com.hrgenius.leave_service.producer;

import com.hrgenius.leave_service.dto.LeaveApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveEventProducer {

    private final AmqpTemplate amqpTemplate;

    @Value("${app.rabbitmq.exchange:hrgenius.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.routing-key-leave-approved:leave.approved}")
    private String leaveApprovedRoutingKey;

    public void publishLeaveApprovedEvent(LeaveApprovedEvent event) {
        try {
            log.info("Publishing LeaveApprovedEvent for Employee: {} to RabbitMQ", event.getEmployeeCode());
            amqpTemplate.convertAndSend(exchange, leaveApprovedRoutingKey, event);
            log.info("Successfully published LeaveApprovedEvent for request ID: {}", event.getLeaveRequestId());
        } catch (Exception ex) {
            log.error("Failed to publish LeaveApprovedEvent: {}", ex.getMessage(), ex);
        }
    }
}