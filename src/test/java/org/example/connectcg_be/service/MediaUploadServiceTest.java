package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.MediaUploadResponse;
import org.example.connectcg_be.entity.Media;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.MediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaUploadServiceTest {

    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private UserService userService;
    @Mock
    private org.example.connectcg_be.queue.producer.MediaQueueProducer mediaQueueProducer;
    @Mock
    private org.example.connectcg_be.queue.producer.VideoQueueProducer videoQueueProducer;
    @Mock
    private VideoOptimizationService videoOptimizationService;

    private MediaUploadService mediaUploadService;
    private MockMultipartFile png;

    @BeforeEach
    void setUp() {
        mediaUploadService = new MediaUploadService(
                objectStorageService,
                mediaRepository,
                userService,
                new MediaFileValidator(),
                new ImageOptimizationService(),
                mediaQueueProducer,
                videoQueueProducer,
                videoOptimizationService);
        png = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00});
    }

    @Test
    void storesObjectAndPersistsOwnedMetadata() throws Exception {
        User uploader = new User();
        uploader.setId(42);
        when(userService.findByIdUser(42)).thenReturn(uploader);
        when(objectStorageService.store(any(), any(Long.class), any(), any()))
                .thenReturn(new StoredObject("connect-media", "avatar/2026/08/id.png", "http://localhost:9000/connect-media/avatar/2026/08/id.png"));
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> {
            Media media = invocation.getArgument(0);
            media.setId(7);
            return media;
        });

        MediaUploadResponse result = mediaUploadService.upload(png, "avatar", 42);

        assertEquals(7, result.mediaId());
        assertEquals("avatar/2026/08/id.png", result.objectKey());
        verify(mediaRepository).save(any(Media.class));
        verify(mediaQueueProducer).enqueueMediaProcessing(eq(7), eq("avatar/2026/08/id.png"), eq("IMAGE"), eq("AVATAR"), any());
    }

    @Test
    void removesStoredObjectWhenMetadataPersistenceFails() throws Exception {
        User uploader = new User();
        uploader.setId(42);
        when(userService.findByIdUser(42)).thenReturn(uploader);
        StoredObject stored = new StoredObject(
                "connect-media", "avatar/2026/08/id.png", "http://localhost:9000/connect-media/avatar/2026/08/id.png");
        when(objectStorageService.store(any(), any(Long.class), any(), any())).thenReturn(stored);
        when(mediaRepository.save(any(Media.class))).thenThrow(new RuntimeException("database unavailable"));

        assertThrows(RuntimeException.class, () -> mediaUploadService.upload(png, "avatar", 42));

        verify(objectStorageService).delete(stored.objectKey());
    }

    @Test
    void rejectsUnknownCategoryBeforeUploading() {
        assertThrows(MediaValidationException.class, () -> mediaUploadService.upload(png, "../../etc", 42));
    }

    @Test
    void processAsyncMedia_skipsNonImage() {
        mediaUploadService.processAsyncMedia(10, "video/2026/08/vid.mp4", "VIDEO", "POST");
        org.mockito.Mockito.verifyNoInteractions(mediaRepository);
    }

    @Test
    void processAsyncMedia_skipsWhenNotFoundOrDeleted() {
        when(mediaRepository.findById(999)).thenReturn(java.util.Optional.empty());

        mediaUploadService.processAsyncMedia(999, "post/2026/08/img.jpg", "IMAGE", "POST");

        verify(mediaRepository).findById(999);
        org.mockito.Mockito.verifyNoMoreInteractions(mediaRepository);
    }

    @Test
    void uploadVideo_storesObjectAndEnqueuesVideoProcessing() {
        User uploader = new User();
        uploader.setId(42);
        when(userService.findByIdUser(42)).thenReturn(uploader);

        MockMultipartFile videoFile = new MockMultipartFile(
                "file",
                "demo.mp4",
                "video/mp4",
                new byte[] {0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'});

        MockMultipartFile thumbFile = new MockMultipartFile(
                "thumbnail",
                "thumb.jpg",
                "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});

        when(objectStorageService.store(any(), any(Long.class), eq("video/mp4"), any()))
                .thenReturn(new StoredObject("connect-media", "post/2026/10/vid.mp4", "http://localhost:9000/connect-media/post/2026/10/vid.mp4"));
        when(objectStorageService.store(any(), any(Long.class), eq("image/jpeg"), any()))
                .thenReturn(new StoredObject("connect-media", "post/2026/10/thumb_vid.jpg", "http://localhost:9000/connect-media/post/2026/10/thumb_vid.jpg"));

        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> {
            Media media = invocation.getArgument(0);
            media.setId(99);
            return media;
        });

        MediaUploadResponse result = mediaUploadService.upload(videoFile, "post", 42, thumbFile);

        assertEquals(99, result.mediaId());
        assertEquals("http://localhost:9000/connect-media/post/2026/10/thumb_vid.jpg", result.thumbnailUrl());
        verify(videoQueueProducer).enqueueVideoProcessing(eq(99), any(), eq("POST"), eq("video/mp4"), any());
    }

    @Test
    void processAsyncVideo_skipsWhenNotFoundOrDeleted() {
        when(mediaRepository.findById(999)).thenReturn(java.util.Optional.empty());

        mediaUploadService.processAsyncVideo(999, "post/2026/10/vid.mp4", "POST", "video/mp4", 1000L);

        verify(mediaRepository).findById(999);
        org.mockito.Mockito.verifyNoMoreInteractions(mediaRepository);
    }
}
