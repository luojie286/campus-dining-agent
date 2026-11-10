package com.sky.task;

import com.sky.mapper.OrderLifecycleRepository;
import com.sky.service.OrderLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@Profile("!bootstrap")
@ConditionalOnProperty(name = "sky.orders.timeout-task-enabled", havingValue = "true", matchIfMissing = true)
public class OrderTimeoutTask {
    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutTask.class);
    private final OrderLifecycleRepository lifecycle;
    private final OrderLifecycleService orders;
    private final Clock clock;

    public OrderTimeoutTask(OrderLifecycleRepository lifecycle, OrderLifecycleService orders, Clock clock) {
        this.lifecycle = lifecycle;
        this.orders = orders;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void closeExpiredOrders() {
        for (long id : lifecycle.expiredMockOrders(LocalDateTime.now(clock))) {
            try {
                orders.cancelExpiredMock(id);
            } catch (RuntimeException exception) {
                log.warn("自动关闭订单失败，订单ID={}", id, exception);
            }
        }
    }
}
