package ui;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class SoundPlayer {
    private Clip clip;
    private boolean isLooping = false;

    private static final Map<String, byte[]> soundCache = new HashMap<>();

    public void playSound(String resourcePath) {
        playSound(resourcePath, false);
    }

    public void playSound(String resourcePath, boolean loop) {
        stopSound();

        String cleanedPath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        if (cleanedPath.startsWith("resources/")) {
            cleanedPath = cleanedPath.substring("resources/".length());
        }

        System.out.println("Đang tìm âm thanh: " + cleanedPath);

        try {
            byte[] soundData = soundCache.get(cleanedPath);

            if (soundData == null) {
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream(cleanedPath);
                if (inputStream == null) {
                    inputStream = getClass().getResourceAsStream("/" + cleanedPath);
                }

                if (inputStream == null) {
                    System.err.println("❌ Không tìm thấy file âm thanh: " + cleanedPath);
                    return;
                }

                // Đọc toàn bộ dữ liệu
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;

                while ((read = inputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }

                inputStream.close();

                soundData = out.toByteArray();
                soundCache.put(cleanedPath, soundData);
                System.out.println("📦 Đã cache file âm thanh: " + cleanedPath);
            } else {
                System.out.println("📦 Sử dụng âm thanh từ cache: " + cleanedPath);
            }

            ByteArrayInputStream byteInputStream = new ByteArrayInputStream(soundData);

            AudioInputStream audioStream = AudioSystem.getAudioInputStream(byteInputStream);
            clip = AudioSystem.getClip();
            clip.open(audioStream);

            if (loop) {
                clip.loop(Clip.LOOP_CONTINUOUSLY);
                isLooping = true;
            } else {
                clip.start();
                isLooping = false;
            }

            System.out.println("✅ Đang phát âm thanh: " + cleanedPath);
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi phát âm thanh: " + cleanedPath);
            e.printStackTrace();
        }
    }

    public void stopSound() {
        if (clip != null && clip.isRunning()) {
            clip.stop();
            clip.close();
        }
        isLooping = false;
    }

    public boolean isPlaying() {
        return clip != null && clip.isRunning();
    }

    public boolean isLooping() {
        return isLooping;
    }
}