package com.kiri.server.domain.image.event;

import com.kiri.server.domain.image.service.ImageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ImageConfirmedEventListenerTest {

    private static final ImageConfirmedEvent EVENT = new ImageConfirmedEvent(
            "pending/user/1/3f2b8c1e-7d4a-4f6b-9a2e-1c5d8e9f0a7b.jpg",
            "user/original/1/3f2b8c1e-7d4a-4f6b-9a2e-1c5d8e9f0a7b.jpg");

    @Mock
    private ImageService imageService;

    @InjectMocks
    private ImageConfirmedEventListener listener;

    @Test
    @DisplayName("커밋 후에는 pending 원본만 삭제한다.")
    void deletePending() {
        listener.deletePending(EVENT);

        verify(imageService).deleteImage(EVENT.pendingKey());
        verifyNoMoreInteractions(imageService);
    }

    @Test
    @DisplayName("롤백되면 복사한 원본만 삭제한다.")
    void deleteOriginal() {
        listener.deleteOriginal(EVENT);

        verify(imageService).deleteImage(EVENT.originalKey());
        verifyNoMoreInteractions(imageService);
    }
}
