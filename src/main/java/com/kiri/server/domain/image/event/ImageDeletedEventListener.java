package com.kiri.server.domain.image.event;

import com.kiri.server.domain.image.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ImageDeletedEventListener {

    private final ImageService imageService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deleteImages(ImageDeletedEvent event) {
        event.imageKeys().forEach(imageService::deleteImage);
    }
}
