package com.sky.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sky.vo.OrderVO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 订单事件统一出口：事件格式一致，并且只在数据库事务提交成功后推送。
 * 回滚时不会推送"已支付/已完成"这类不存在的状态。
 */
@Component
@Profile("!bootstrap")
public class OrderEventPublisher {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrderEvent(String eventId, String type, long orderId, String orderNumber,
                             OffsetDateTime occurredAt, String status, Integer urgeCount) {
    }

    private final OrderWebSocketHandler socket;
    private final Clock clock;

    public OrderEventPublisher(OrderWebSocketHandler socket, Clock clock) {
        this.socket = socket;
        this.clock = clock;
    }

    public void publishAfterCommit(String type, OrderVO order, Integer urgeCount) {
        OrderEvent event = new OrderEvent(
                UUID.randomUUID().toString(),
                type,
                order.id(),
                order.number(),
                OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS),
                order.status(),
                urgeCount);
        long ownerId = order.userId();

        boolean inTransaction = TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive();
        if (!inTransaction) {
            socket.broadcast(event, ownerId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                socket.broadcast(event, ownerId);
            }
        });
    }
}
