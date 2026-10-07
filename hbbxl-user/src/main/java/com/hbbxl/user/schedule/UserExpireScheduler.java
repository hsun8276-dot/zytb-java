package com.hbbxl.user.schedule;

import com.hbbxl.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

/** 到期账号自动停用: 服务启动时+每小时整点执行 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserExpireScheduler {

    private final UserService userService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "0 0 * * * *")
    public void hourly() {
        run();
    }

    private void run() {
        try {
            int n = userService.autoDisableExpired();
            if (n > 0) log.info("到期自动停用账号: {} 个", n);
        } catch (Exception e) {
            log.error("到期自动停用任务失败", e);
        }
    }
}
