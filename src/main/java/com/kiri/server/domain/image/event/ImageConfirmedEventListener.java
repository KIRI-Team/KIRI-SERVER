package com.kiri.server.domain.image.event;

import com.kiri.server.domain.image.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ImageConfirmedEventListener {

    private final ImageService imageService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deletePending(ImageConfirmedEvent event) {
        imageService.deleteImage(event.pendingKey());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void deleteOriginal(ImageConfirmedEvent event) {
        imageService.deleteImage(event.originalKey());
    }
}
