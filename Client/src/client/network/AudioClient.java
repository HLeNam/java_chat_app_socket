package client.network;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioClient implements Runnable {
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private String username;
    private String serverHost;
    private int serverPort;
    private AtomicBoolean running = new AtomicBoolean(false);
    private Thread receiveThread;

    private TargetDataLine microphone;
    private SourceDataLine speakers;
    private AtomicBoolean isCapturing = new AtomicBoolean(false);
    private Thread captureThread;

    private static int totalInstances = 0;
    private final int instanceId;

    private boolean microphoneAvailable = false;
    private boolean speakersAvailable = false;

    private float recordingGain = 2.5f;
    private float playbackGain = 2.0f;

    private boolean agcEnabled = true;
    private float targetLevel = 0.7f; // Target là 70% mức max
    private float agcGain = 2.0f;
    private float minGain = 1.0f;
    private float maxGain = 8.0f;
    private float agcAttack = 0.01f; // Tốc độ tăng gain
    private float agcDecay = 0.002f; // Tốc độ giảm gain

    public AudioClient(String serverHost, int serverPort, String username) {
        this(serverHost, serverPort, username, 2.5f); // Mặc định gain = 2.5
    }

    public AudioClient(String serverHost, int serverPort, String username, float recordingGain) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.username = username;
        this.instanceId = ++totalInstances;
        this.recordingGain = Math.max(1.0f, Math.min(5.0f, recordingGain)); // Giới hạn từ 1.0 đến 5.0
        System.out.println("[AudioClient #" + instanceId + "] Đang tạo instance mới cho " + username +
                " với gain = " + this.recordingGain);
    }

    public boolean connect() {
        try {
            System.out.println("[AudioClient #" + instanceId + "] Đang kết nối đến " + serverHost + ":" + serverPort + "...");
            socket = new Socket(serverHost, serverPort);
            in = socket.getInputStream();
            out = socket.getOutputStream();

            // Gửi username để đăng ký với server
            out.write(username.getBytes(StandardCharsets.UTF_8));
            out.flush();

            // Khởi tạo các thiết bị âm thanh
            try {
                initAudioDevices();
                microphoneAvailable = (microphone != null && microphone.isOpen());
                speakersAvailable = (speakers != null && speakers.isOpen());
            } catch (Exception e) {
                System.err.println("Không thể khởi tạo thiết bị âm thanh: " + e.getMessage());
                microphoneAvailable = false;
                speakersAvailable = false;
            }

            running.set(true);
            if (speakersAvailable) {
                receiveThread = new Thread(this);
                receiveThread.start();
            }

            System.out.println("[AudioClient #" + instanceId + "] Đã kết nối thành công đến Audio Server");
            System.out.println("Trạng thái thiết bị: Microphone: " +
                    (microphoneAvailable ? "Khả dụng" : "Không khả dụng") +
                    ", Loa: " + (speakersAvailable ? "Khả dụng" : "Không khả dụng"));

            // Trả về true ngay cả khi không có thiết bị âm thanh
            return true;
        } catch (IOException e) {
            System.err.println("[AudioClient #" + instanceId + "] Không thể kết nối đến Audio Server: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void initAudioDevices() {
        try {
            // Định dạng tốt nhất cho giọng nói là 16kHz, 16-bit
            AudioFormat preferredFormat = new AudioFormat(16000.0f, 16, 1, true, false);

            AudioFormat[] formats = {
                    // Định dạng 1: 16kHz, 16-bit, mono, PCM_SIGNED (chất lượng tốt)
                    preferredFormat,

                    // Định dạng 2: 8kHz, 16-bit, mono, PCM_SIGNED
                    new AudioFormat(8000.0f, 16, 1, true, false),

                    // Định dạng 3: 16kHz, 8-bit, mono, PCM_SIGNED
                    new AudioFormat(16000.0f, 8, 1, true, false),

                    // Định dạng 4: 8kHz, 8-bit, mono, PCM_SIGNED
                    new AudioFormat(8000.0f, 8, 1, true, false),

                    // Định dạng 5: 8kHz, 8-bit, mono, ULAW (nén)
                    new AudioFormat(AudioFormat.Encoding.ULAW, 8000.0f, 8, 1, 1, 8000.0f, false)
            };

            boolean foundSupportedFormat = false;
            AudioFormat selectedFormat = null;

            // Thử từng định dạng cho đến khi tìm thấy một định dạng được hỗ trợ
            for (AudioFormat format : formats) {
                try {
                    System.out.println("Đang thử định dạng âm thanh: " + formatToString(format));

                    DataLine.Info microphoneInfo = new DataLine.Info(TargetDataLine.class, format);
                    DataLine.Info speakersInfo = new DataLine.Info(SourceDataLine.class, format);

                    // Kiểm tra xem định dạng có được hỗ trợ không
                    if (AudioSystem.isLineSupported(microphoneInfo) && AudioSystem.isLineSupported(speakersInfo)) {
                        microphone = (TargetDataLine) AudioSystem.getLine(microphoneInfo);
                        speakers = (SourceDataLine) AudioSystem.getLine(speakersInfo);

                        // Thử mở thiết bị để đảm bảo chúng thật sự hoạt động
                        microphone.open(format);
                        speakers.open(format);

                        foundSupportedFormat = true;
                        System.out.println("Đã tìm thấy định dạng âm thanh được hỗ trợ: " + formatToString(format));
                        break;
                    }
                } catch (LineUnavailableException e) {
                    System.out.println("Định dạng âm thanh không được hỗ trợ: " + e.getMessage());
                }
            }

            if (!foundSupportedFormat) {
                // Không tìm thấy định dạng hỗ trợ, thử tìm thiết bị mặc định
                try {
                    System.out.println("Đang thử tìm thiết bị âm thanh mặc định...");
                    Mixer mixer = AudioSystem.getMixer(null); // null = mixer mặc định

                    // Liệt kê tất cả các dòng hỗ trợ
                    Line.Info[] lineInfos = mixer.getTargetLineInfo();
                    for (Line.Info lineInfo : lineInfos) {
                        System.out.println("Tìm thấy target line: " + lineInfo);
                        if (lineInfo instanceof DataLine.Info) {
                            AudioFormat format = ((DataLine.Info) lineInfo).getFormats()[0];
                            System.out.println("Thử định dạng từ dòng mặc định: " + formatToString(format));

                            // Thử tạo thiết bị với định dạng này
                            DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
                            DataLine.Info spkInfo = new DataLine.Info(SourceDataLine.class, format);

                            if (AudioSystem.isLineSupported(micInfo) && AudioSystem.isLineSupported(spkInfo)) {
                                microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
                                speakers = (SourceDataLine) AudioSystem.getLine(spkInfo);

                                microphone.open(format);
                                speakers.open(format);

                                selectedFormat = format;
                                foundSupportedFormat = true;
                                System.out.println("Đã tìm thấy định dạng âm thanh từ thiết bị mặc định");
                                break;
                            }
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("Không thể tìm thiết bị âm thanh mặc định: " + ex.getMessage());
                }
            }

            if (!foundSupportedFormat) {
                System.err.println("Không tìm thấy định dạng âm thanh được hỗ trợ. Voice call sẽ không hoạt động.");
                throw new UnsupportedOperationException("Không tìm thấy thiết bị âm thanh hỗ trợ");
            }

            // Khởi động speakers nếu đã được mở
            if (speakers != null && speakers.isOpen()) {
                speakers.start();
            }

        } catch (Exception e) {
            System.err.println("Lỗi khi khởi tạo thiết bị âm thanh: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String formatToString(AudioFormat format) {
        return String.format("Sample Rate: %.1f Hz, Sample Size: %d bits, Channels: %d, Encoding: %s",
                format.getSampleRate(), format.getSampleSizeInBits(),
                format.getChannels(), format.getEncoding());
    }

    @Override
    public void run() {
        byte[] buffer = new byte[1024];
        int bytesRead;

        while (running.get()) {
            try {
                // Đọc dữ liệu từ server
                bytesRead = in.read(buffer);
                if (bytesRead == -1) {
                    // Kết nối đã đóng
                    break;
                }

                // Phát âm thanh nhận được với gain
                if (bytesRead > 0 && speakers != null && speakers.isOpen()) {
                    byte[] processedBuffer = applyPlaybackGain(buffer, bytesRead);
                    speakers.write(processedBuffer, 0, bytesRead);
                }
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("Lỗi khi nhận dữ liệu âm thanh: " + e.getMessage());
                    e.printStackTrace();
                    break;
                }
            }
        }

        disconnect();
    }

    public void startCapturing() {
        if (!microphoneAvailable) {
            System.err.println("Không thể bắt đầu thu âm vì microphone không khả dụng");
            return;
        }

        if (isCapturing.get() || microphone == null) return;

        try {
            if (!microphone.isOpen()) {
                microphone.open(microphone.getFormat());
            }
            microphone.start();
            isCapturing.set(true);

            captureThread = new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (isCapturing.get() && microphone.isOpen()) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        // Tính toán gain tự động nếu AGC được bật
                        float effectiveGain = agcEnabled ? calculateAGCGain(buffer, bytesRead) : recordingGain;

                        // Áp dụng gain
                        byte[] processedBuffer;
                        if (agcEnabled) {
                            processedBuffer = applyCustomGain(buffer, bytesRead, effectiveGain);
                        } else {
                            processedBuffer = applyGain(buffer, bytesRead);
                        }

                        sendAudioData(processedBuffer, bytesRead);
                    }
                }
            }, "MicrophoneCapture");

            captureThread.start();
            System.out.println("Bắt đầu thu âm từ microphone");
        } catch (Exception e) {
            System.err.println("Không thể bắt đầu thu âm: " + e.getMessage());
            e.printStackTrace();
            microphoneAvailable = false;
        }
    }

    public void stopCapturing() {
        isCapturing.set(false);
        if (microphone != null && microphone.isOpen()) {
            microphone.stop();
            microphone.close();
            System.out.println("Đã dừng thu âm từ microphone");
        }

        if (captureThread != null && captureThread.isAlive()) {
            try {
                captureThread.join(500);  // Chờ tối đa 500ms
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendAudioData(byte[] audioData, int length) {
        if (!running.get() || out == null) return;

        try {
            out.write(audioData, 0, length);
            out.flush();
        } catch (IOException e) {
            System.err.println("Lỗi khi gửi dữ liệu âm thanh: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void disconnect() {
        running.set(false);
        stopCapturing();

        try {
            if (speakers != null && speakers.isOpen()) {
                speakers.drain();
                speakers.stop();
                speakers.close();
            }

            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

            System.out.println("Đã ngắt kết nối khỏi Audio Server");
        } catch (IOException e) {
            System.err.println("Lỗi khi ngắt kết nối AudioClient: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public boolean isCapturing() {
        return isCapturing.get();
    }

    public boolean isMicrophoneAvailable() {
        return microphoneAvailable;
    }

    public boolean isSpeakersAvailable() {
        return speakersAvailable;
    }

    public void setMuted(boolean muted) {
        if (muted) {
            stopCapturing();
        } else if (!isCapturing.get() && running.get()) {
            startCapturing();
        }
    }

    public void setRecordingGain(float gain) {
        this.recordingGain = Math.max(1.0f, Math.min(5.0f, gain));
        System.out.println("[AudioClient #" + instanceId + "] Đã đặt gain = " + this.recordingGain);
    }

    private byte[] applyGain(byte[] audioData, int bytesRead) {
        // Nếu gain = 1.0, không cần xử lý
        if (recordingGain == 1.0f) {
            return audioData;
        }

        byte[] processedData = new byte[bytesRead];

        // Nếu đang sử dụng định dạng 16-bit
        if (isUsingFormat16Bit()) {
            // Xử lý 16-bit audio (mỗi mẫu chiếm 2 byte)
            for (int i = 0; i < bytesRead; i += 2) {
                if (i + 1 >= bytesRead) break; // Tránh tràn mảng

                // Chuyển 2 byte thành short (16-bit signed)
                short sample = (short) ((audioData[i+1] << 8) | (audioData[i] & 0xFF));

                // Áp dụng gain
                sample = (short) (sample * recordingGain);

                // Giới hạn trong phạm vi của short
                if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE;
                if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE;

                // Chuyển lại thành 2 byte
                processedData[i] = (byte) (sample & 0xFF);
                processedData[i+1] = (byte) ((sample >> 8) & 0xFF);
            }
        } else {
            // Xử lý 8-bit audio (mỗi mẫu chiếm 1 byte)
            for (int i = 0; i < bytesRead; i++) {
                int sample = audioData[i];
                sample = (int) (sample * recordingGain);

                // Giới hạn trong phạm vi của byte
                if (sample > 127) sample = 127;
                if (sample < -128) sample = -128;

                processedData[i] = (byte) sample;
            }
        }

        return processedData;
    }

    public void setPlaybackGain(float gain) {
        this.playbackGain = Math.max(1.0f, Math.min(5.0f, gain));
        System.out.println("[AudioClient #" + instanceId + "] Đã đặt playback gain = " + this.playbackGain);
    }

    private byte[] applyPlaybackGain(byte[] audioData, int bytesRead) {
        // Nếu gain = 1.0, không cần xử lý
        if (playbackGain == 1.0f) {
            return audioData;
        }

        byte[] processedData = new byte[bytesRead];

        // Nếu đang sử dụng định dạng 16-bit
        if (isUsingFormat16Bit()) {
            // Xử lý 16-bit audio (mỗi mẫu chiếm 2 byte)
            for (int i = 0; i < bytesRead; i += 2) {
                if (i + 1 >= bytesRead) break; // Tránh tràn mảng

                // Chuyển 2 byte thành short (16-bit signed)
                short sample = (short) ((audioData[i+1] << 8) | (audioData[i] & 0xFF));

                // Áp dụng gain
                sample = (short) (sample * playbackGain);

                // Giới hạn trong phạm vi của short
                if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE;
                if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE;

                // Chuyển lại thành 2 byte
                processedData[i] = (byte) (sample & 0xFF);
                processedData[i+1] = (byte) ((sample >> 8) & 0xFF);
            }
        } else {
            // Xử lý 8-bit audio (mỗi mẫu chiếm 1 byte)
            for (int i = 0; i < bytesRead; i++) {
                int sample = audioData[i];
                sample = (int) (sample * playbackGain);

                // Giới hạn trong phạm vi của byte
                if (sample > 127) sample = 127;
                if (sample < -128) sample = -128;

                processedData[i] = (byte) sample;
            }
        }

        return processedData;
    }

    private boolean isUsingFormat16Bit() {
        return microphone != null && microphone.getFormat().getSampleSizeInBits() == 16;
    }

    public void setAGCEnabled(boolean enabled) {
        this.agcEnabled = enabled;
    }

    private float calculateAGCGain(byte[] audioData, int bytesRead) {
        if (!agcEnabled) {
            return recordingGain;
        }

        float currentLevel = 0;

        if (isUsingFormat16Bit()) {
            // Xử lý 16-bit audio
            for (int i = 0; i < bytesRead; i += 2) {
                if (i + 1 >= bytesRead) break;

                short sample = (short) ((audioData[i+1] << 8) | (audioData[i] & 0xFF));
                float normalizedSample = Math.abs(sample) / 32768.0f; // Chuẩn hóa về 0-1

                if (normalizedSample > currentLevel) {
                    currentLevel = normalizedSample;
                }
            }
        } else {
            // Xử lý 8-bit audio
            for (int i = 0; i < bytesRead; i++) {
                float normalizedSample = Math.abs(audioData[i]) / 128.0f; // Chuẩn hóa về 0-1

                if (normalizedSample > currentLevel) {
                    currentLevel = normalizedSample;
                }
            }
        }

        // Điều chỉnh gain dần dần để đạt mức mong muốn
        if (currentLevel > 0) {
            float desiredGain = targetLevel / currentLevel;

            // Áp dụng attack/decay để làm mịn sự thay đổi
            if (desiredGain > agcGain) {
                // Attack - tăng gain từ từ
                agcGain += agcAttack * (desiredGain - agcGain);
            } else {
                // Decay - giảm gain từ từ
                agcGain -= agcDecay * (agcGain - desiredGain);
            }

            // Giới hạn gain trong khoảng min-max
            agcGain = Math.max(minGain, Math.min(maxGain, agcGain));
        }

        return agcGain;
    }

    private byte[] applyCustomGain(byte[] audioData, int bytesRead, float customGain) {
        // Nếu gain = 1.0, không cần xử lý
        if (customGain == 1.0f) {
            return audioData;
        }

        byte[] processedData = new byte[bytesRead];

        // Nếu đang sử dụng định dạng 16-bit
        if (isUsingFormat16Bit()) {
            // Xử lý 16-bit audio (mỗi mẫu chiếm 2 byte)
            for (int i = 0; i < bytesRead; i += 2) {
                if (i + 1 >= bytesRead) break; // Tránh tràn mảng

                // Chuyển 2 byte thành short (16-bit signed)
                short sample = (short) ((audioData[i+1] << 8) | (audioData[i] & 0xFF));

                // Áp dụng gain
                sample = (short) (sample * customGain);

                // Giới hạn trong phạm vi của short
                if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE;
                if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE;

                // Chuyển lại thành 2 byte
                processedData[i] = (byte) (sample & 0xFF);
                processedData[i+1] = (byte) ((sample >> 8) & 0xFF);
            }
        } else {
            // Xử lý 8-bit audio (mỗi mẫu chiếm 1 byte)
            for (int i = 0; i < bytesRead; i++) {
                int sample = audioData[i];
                sample = (int) (sample * customGain);

                // Giới hạn trong phạm vi của byte
                if (sample > 127) sample = 127;
                if (sample < -128) sample = -128;

                processedData[i] = (byte) sample;
            }
        }

        return processedData;
    }
}