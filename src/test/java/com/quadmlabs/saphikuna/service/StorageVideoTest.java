package com.quadmlabs.saphikuna.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas de los videos: solo MP4 de verdad y de tamaño permitido. */
class StorageVideoTest {

    /** Los primeros bytes de un MP4: 4 de tamaño de caja y luego "ftyp". */
    private static byte[] mp4Bytes(int total) {
        byte[] b = new byte[total];
        b[3] = 24;
        b[4] = 'f';
        b[5] = 't';
        b[6] = 'y';
        b[7] = 'p';
        return b;
    }

    private final StorageService storage = new StorageService() {
        public String upload(MultipartFile f, String p) {
            return p;
        }

        public String uploadVideo(MultipartFile f, String p) {
            return p;
        }

        public boolean ping() {
            return true;
        }

        public String getPublicUrl(String p) {
            return p;
        }

        public void delete(String p) {
        }
    };

    @Test
    void aRealMp4IsAccepted() {
        assertDoesNotThrow(() -> storage.validateVideo(
                new MockMultipartFile("newVideo", "baile.mp4", "video/mp4", mp4Bytes(3_000_000))));
    }

    @Test
    void noVideoIsFine() {
        assertDoesNotThrow(() -> storage.validateVideo(null));
        assertDoesNotThrow(() -> storage.validateVideo(new MockMultipartFile("newVideo", new byte[0])));
    }

    @Test
    void aVideoOverTheLimitIsRejectedWithItsSize() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> storage.validateVideo(
                new MockMultipartFile("newVideo", "grande.mp4", "video/mp4", mp4Bytes(16 * 1024 * 1024))));
        assertTrue(e.getMessage().contains("16 MB") && e.getMessage().contains("15 MB"), e.getMessage());
    }

    @Test
    void otherFormatsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> storage.validateVideo(
                new MockMultipartFile("newVideo", "peli.avi", "video/x-msvideo", mp4Bytes(1000))));
    }

    @Test
    void aFileRenamedToMp4ButNotAnMp4IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> storage.validateVideo(
                new MockMultipartFile("newVideo", "falso.mp4", "video/mp4", "esto es texto, no un video".getBytes())));
    }
}
