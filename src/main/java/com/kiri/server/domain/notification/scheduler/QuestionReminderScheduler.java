package com.kiri.server.domain.notification.scheduler;

import com.kiri.server.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class QuestionReminderScheduler {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final NotificationService notificationService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void sendQuestionReminderNotifications() {
        LocalDate today = LocalDate.now(SEOUL_ZONE);
        LocalTime currentTime = LocalTime.now(SEOUL_ZONE).withSecond(0).withNano(0);

        log.info("오늘의 질문 미답변 알림 확인 시작. date={}, time={}", today, currentTime);

        notificationService.createQuestionReminderNotifications(today, currentTime);
    }
}
