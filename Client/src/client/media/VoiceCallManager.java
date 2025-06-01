package client.media;

import client.ChatClient;
import client.network.AudioClient;
import util.Protocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

public class VoiceCallManager {
    private ChatClient client;
    private AudioClient audioClient;
    private String currentCallParticipant;
    private boolean isInCall = false;
    private boolean isMuted = false;
    private VoiceCallListener callListener;
    private String serverHost;
    private int audioPort;
    private boolean audioClientInitialized = false;

    public VoiceCallManager(ChatClient client, String serverHost, int audioPort) {
        this.client = client;
        this.serverHost = serverHost;
        this.audioPort = audioPort;
    }

    public void setCallListener(VoiceCallListener listener) {
        this.callListener = listener;
        System.out.println("Đã đăng ký CallListener: " +
                (listener != null ? listener.getClass().getSimpleName() : "null"));
    }

    public void startCall(String receiver) {
        if (!isInCall) {
            currentCallParticipant = receiver;
            isInCall = true;

            if (callListener != null) {
                callListener.onCallStarting(receiver);
            }

            boolean audioInitialized = ensureAudioClient();
            if (!audioInitialized) {
                System.err.println("Khởi tạo âm thanh thất bại, nhưng vẫn tiếp tục cuộc gọi");
            }

            client.getServerConnection().sendMessage(Protocol.CMD_VOICE_CALL_REQUEST + receiver);
        }
    }

    public void acceptCall(String caller) {
        if (!isInCall) {
            currentCallParticipant = caller;
            isInCall = true;

            ensureAudioClient();

            client.getServerConnection().sendMessage(Protocol.CMD_VOICE_CALL_ACCEPT + caller);

            if (callListener != null) {
                callListener.onCallAccepted(caller);
            }
        }
    }

    public void rejectCall(String caller) {
        client.getServerConnection().sendMessage(Protocol.CMD_VOICE_CALL_REJECT + caller);

        if (callListener != null) {
            callListener.onCallRejected(caller);
        }
    }

    public void handleCallAccepted(String participant) {
        if (isInCall && participant.equals(currentCallParticipant)) {
            ensureAudioClient();

            if (audioClientInitialized && audioClient != null && audioClient.isConnected()) {
                // Bắt đầu ghi âm
                audioClient.startCapturing();
            }

            if (callListener != null) {
                callListener.onCallConnected(participant);
            }
        }
    }

    public void handleCallRejected(String participant) {
        if (isInCall && participant.equals(currentCallParticipant)) {
            endCall(false);  // false = đừng gửi lại tin nhắn kết thúc

            if (callListener != null) {
                callListener.onCallRejected(participant);
            }
        }
    }

    public void endCall() {
        endCall(true);  // true = gửi tin nhắn kết thúc
    }

    private void endCall(boolean sendEndMessage) {
        if (isInCall) {
            if (sendEndMessage && currentCallParticipant != null) {
                client.getServerConnection().sendMessage(Protocol.CMD_VOICE_CALL_END + currentCallParticipant);
            }

            cleanupCall();

            if (callListener != null) {
                callListener.onCallEnded(currentCallParticipant);
            }
        }
    }

    public void handleCallEnded(String participant) {
        if (isInCall && participant.equals(currentCallParticipant)) {
            cleanupCall();

            if (callListener != null) {
                callListener.onCallEnded(participant);
            }
        }
    }

    private void cleanupCall() {
        isInCall = false;

        // Đóng kết nối audio
        if (audioClient != null) {
            audioClient.disconnect();
            audioClient = null;
        }

        currentCallParticipant = null;
    }

    public void setMuted(boolean muted) {
        this.isMuted = muted;
        if (audioClient != null) {
            audioClient.setMuted(muted);
        }
    }

    public boolean isInCall() {
        return isInCall;
    }

    public String getCurrentCallParticipant() {
        return currentCallParticipant;
    }

    private boolean ensureAudioClient() {
        if (!audioClientInitialized || audioClient == null || !audioClient.isConnected()) {
            try {
                audioClient = new AudioClient(serverHost, audioPort, client.getCurrentUser().getUsername());
                boolean connected = audioClient.connect();

                if (connected) {
                    audioClientInitialized = true;
                    System.out.println("Kết nối âm thanh thành công");
                    return true;
                } else {
                    System.err.println("Không thể kết nối đến Audio Server");

                    if (callListener != null) {
                        callListener.onStatusMessage("Không thể kết nối đến máy chủ âm thanh. Bạn sẽ không nghe được đối phương.");
                    }

                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            if (isInCall) {
                                ensureAudioClient();
                            }
                        }
                    }, 2000);

                    return false;
                }
            } catch (UnsupportedOperationException e) {
                System.err.println("Thiết bị âm thanh không được hỗ trợ: " + e.getMessage());

                if (callListener != null) {
                    callListener.onStatusMessage("Thiết bị âm thanh không được hỗ trợ. Vui lòng kiểm tra microphone và loa.");
                }

                return false;
            } catch (Exception e) {
                System.err.println("Lỗi khi khởi tạo kết nối âm thanh: " + e.getMessage());
                e.printStackTrace();

                if (callListener != null) {
                    callListener.onStatusMessage("Lỗi khi khởi tạo kết nối âm thanh: " + e.getMessage());
                }

                return false;
            }
        }

        return true;
    }

    public AudioClient getAudioClient() {
        return audioClient;
    }

    public void saveVolumeSettings(float micGain, float speakerGain, boolean agcEnabled) {
        try {
            Properties props = new Properties();
            props.setProperty("mic_gain", String.valueOf(micGain));
            props.setProperty("speaker_gain", String.valueOf(speakerGain));
            props.setProperty("agc_enabled", String.valueOf(agcEnabled));

            File settingsDir = new File(System.getProperty("user.home"), ".java_socker_chat_app");
            if (!settingsDir.exists()) {
                settingsDir.mkdirs();
            }

            File settingsFile = new File(settingsDir, "audio_settings.properties");
            try (FileOutputStream out = new FileOutputStream(settingsFile)) {
                props.store(out, "Audio Settings");
            }

            System.out.println("Đã lưu cài đặt âm lượng");
        } catch (Exception e) {
            System.err.println("Không thể lưu cài đặt âm lượng: " + e.getMessage());
        }
    }

    public VolumeSettings loadVolumeSettings() {
        VolumeSettings settings = new VolumeSettings();
        settings.micGain = 2.5f;
        settings.speakerGain = 2.0f;
        settings.agcEnabled = false;

        try {
            File settingsFile = new File(new File(System.getProperty("user.home"), ".java_socker_chat_app"), "audio_settings.properties");
            if (settingsFile.exists()) {
                Properties props = new Properties();
                try (FileInputStream in = new FileInputStream(settingsFile)) {
                    props.load(in);

                    String micGainStr = props.getProperty("mic_gain");
                    String speakerGainStr = props.getProperty("speaker_gain");
                    String agcEnabledStr = props.getProperty("agc_enabled");

                    if (micGainStr != null) {
                        settings.micGain = Float.parseFloat(micGainStr);
                    }

                    if (speakerGainStr != null) {
                        settings.speakerGain = Float.parseFloat(speakerGainStr);
                    }

                    if (agcEnabledStr != null) {
                        settings.agcEnabled = Boolean.parseBoolean(agcEnabledStr);
                    }

                    System.out.println("Đã đọc cài đặt âm lượng");
                }
            }
        } catch (Exception e) {
            System.err.println("Không thể đọc cài đặt âm lượng: " + e.getMessage());
        }

        return settings;
    }

    public static class VolumeSettings {
        public float micGain;
        public float speakerGain;
        public boolean agcEnabled;
    }

    public interface VoiceCallListener {
        void onCallStarting(String participant);
        void onCallAccepted(String participant);
        void onCallConnected(String participant);
        void onCallRejected(String participant);
        void onCallEnded(String participant);
        void onStatusMessage(String message);
    }
}