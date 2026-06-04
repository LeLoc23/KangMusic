package com.musicapp.services;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AudioProcessingServiceTest {

    private final AudioProcessingService audioProcessingService = new AudioProcessingService();

    @Test
    void testPrepareAudioChunks_SmallFile() throws IOException {
        // Create a small temporary file
        File tempFile = File.createTempFile("test_audio", ".mp3");
        tempFile.deleteOnExit();

        List<AudioProcessingService.AudioChunk> chunks = audioProcessingService.prepareAudioChunks(tempFile);

        assertNotNull(chunks);
        assertEquals(1, chunks.size());
        assertEquals(tempFile.getAbsolutePath(), chunks.get(0).getFile().getAbsolutePath());
        assertEquals(0.0, chunks.get(0).getTimeOffsetSeconds());
        assertFalse(chunks.get(0).isTemporary());
    }

    @Test
    void testPrepareAudioChunks_NonExistentFile() {
        File nonExistent = new File("non_existent_file.mp3");
        assertThrows(IllegalArgumentException.class, () -> {
            audioProcessingService.prepareAudioChunks(nonExistent);
        });
    }
}
