package com.kiri.server.domain.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.kiri.server.domain.notification.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService {

    public String send(String token, String title, String body, NotificationType notificationType) throws FirebaseMessagingException {

        Message message = Message.builder()
                .setToken(token)
                .setNotification(
                        com.google.firebase.messaging.Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build()
                )
                .putAllData(Map.of("notificationType", notificationType.name()))
                .build();

        String response = FirebaseMessaging.getInstance().send(message);

        log.info("FCM 전송 성공 notificationType={}, response={}", notificationType, response);

        return response;
    }
}
