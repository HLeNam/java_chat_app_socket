package client.media;

import client.ChatClient;
import client.network.AudioClient;
import client.network.VideoClient;
import ui.VideoCallDialog;
import util.Protocol;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class VideoCallManager {
    private ChatClient client;
    private String serverHost;
    private int audioPort;
    private int videoPort;
    private AudioClient audioClient;
    private VideoClient videoClient;
    private boolean isInCall;
    private String currentCallParticipant;
    private boolean isMuted;
    private boolean isVideoEnabled = true;
    private VideoCallListener callListener;

    public enum CallState {
        IDLE,
        CALLING,
        RINGING,
        CONNECTED,
        RECONNECTING,
        ENDED
    }

    private CallState currentState = CallState.IDLE;

    public VideoCallManager(ChatClient client, String serverHost, int audioPort, int videoPort) {
        this.client = client;
        this.serverHost = serverHost;
        this.audioPort = audioPort;
        this.videoPort = videoPort;
        this.isInCall = false;
        this.isMuted = false;
    }

    public void startCall(String receiver) {
        System.out.println("VideoCallManager: Bắt đầu gọi đến " + receiver);

        if (!isInCall) {
            currentCallParticipant = receiver;
            isInCall = true;

            // Khởi tạo AudioClient và VideoClient trước khi gửi request gọi
            ensureAudioClient();
            ensureVideoClient();

            if (callListener != null) {
                callListener.onCallStarting(receiver);
            }

            client.getServerConnection().sendMessage(Protocol.CMD_VIDEO_CALL_REQUEST + receiver);
            updateState(CallState.CALLING);
        } else {
            System.out.println("VideoCallManager: Không thể gọi, đang trong cuộc gọi với " + currentCallParticipant);
        }
    }

    public void acceptCall(String caller) {
        System.out.println("VideoCallManager: Chấp nhận cuộc gọi từ " + caller);

        if (!isInCall || (isInCall && caller.equals(currentCallParticipant))) {
            currentCallParticipant = caller;
            isInCall = true;

            boolean audioOk = ensureAudioClient();
            boolean videoOk = ensureVideoClient();

            if (!audioOk || !videoOk) {
                System.err.println("VideoCallManager: Không thể khởi tạo Audio/Video client!");
            }

            client.getServerConnection().sendMessage(Protocol.CMD_VIDEO_CALL_ACCEPT + caller);
            System.out.println("VideoCallManager: Đã gửi lệnh chấp nhận đến server");

            if (videoClient != null) {
                System.out.println("VideoCallManager: Bắt đầu gửi video...");
                videoClient.startCapturing();
            } else {
                System.err.println("VideoCallManager: VideoClient là null, không thể gửi video!");
            }

            if (audioClient != null) {
                System.out.println("VideoCallManager: Bắt đầu truyền âm thanh...");
                audioClient.startCapturing();
            }

            if (callListener != null) {
                callListener.onCallAccepted(caller);
            }

            updateState(CallState.CONNECTED);
        }
    }

    public void rejectCall(String caller) {
        System.out.println("VideoCallManager: Từ chối cuộc gọi từ " + caller);
        client.getServerConnection().sendMessage(Protocol.CMD_VIDEO_CALL_REJECT + caller);
    }

    public void endCall() {
        if (isInCall) {
            System.out.println("VideoCallManager: Kết thúc cuộc gọi với " + currentCallParticipant);
            client.getServerConnection().sendMessage(Protocol.CMD_VIDEO_CALL_END + currentCallParticipant);
            cleanupCall();
        }
    }

    public void handleCallRequest(String caller) {
        System.out.println("VideoCallManager: Xử lý yêu cầu gọi từ " + caller);

        if (!isInCall) {
            currentCallParticipant = caller;
            updateState(CallState.RINGING);

            SwingUtilities.invokeLater(() -> {
                VideoCallDialog dialog = new VideoCallDialog(null, client, caller, true);
                dialog.setVisible(true);
            });

            if (callListener != null) {
                callListener.onCallReceived(caller);
            } else {
                System.out.println("VideoCallManager: KHÔNG CÓ callListener đã đăng ký!");
            }
        } else {
            // Tự động từ chối nếu đang trong một cuộc gọi khác
            client.getServerConnection().sendMessage(Protocol.CMD_VIDEO_CALL_REJECT + caller);
        }
    }

    public void handleCallAccepted(String participant) {
        System.out.println("VideoCallManager: " + participant + " đã chấp nhận cuộc gọi, đang thiết lập kết nối...");

        if (isInCall && participant.equals(currentCallParticipant)) {
            boolean audioOk = ensureAudioClient();
            boolean videoOk = ensureVideoClient();

            if (!audioOk || !videoOk) {
                System.err.println("VideoCallManager: Không thể khởi tạo Audio/Video client khi xử lý cuộc gọi đã chấp nhận!");
                if (callListener != null) {
                    callListener.onStatusMessage("Lỗi kết nối: Không thể khởi tạo kết nối audio/video!");
                }
                return;
            }

            // Bắt đầu ghi âm và video
            if (audioClient != null) {
                System.out.println("VideoCallManager: Bắt đầu truyền âm thanh...");
                audioClient.startCapturing();
            }

            if (videoClient != null) {
                System.out.println("VideoCallManager: Bắt đầu gửi video...");
                videoClient.startCapturing();
            } else {
                System.err.println("VideoCallManager: VideoClient là null, không thể gửi video!");
            }

            updateState(CallState.CONNECTED);

            if (callListener != null) {
                System.out.println("VideoCallManager: Gọi callListener.onCallConnected...");
                callListener.onCallConnected(participant);
            }
        } else {
            System.err.println("VideoCallManager: handleCallAccepted nhưng không trong cuộc gọi với " + participant);
        }
    }

    public void handleCallRejected(String participant) {
        System.out.println("VideoCallManager: " + participant + " đã từ chối cuộc gọi");

        if (isInCall && participant.equals(currentCallParticipant)) {
            cleanupCall();

            if (callListener != null) {
                callListener.onCallRejected(participant);
            }

            updateState(CallState.ENDED);
        }
    }

    public void handleCallEnded(String participant) {
        System.out.println("VideoCallManager: Cuộc gọi với " + participant + " đã kết thúc");

        if (isInCall && participant.equals(currentCallParticipant)) {
            cleanupCall();

            if (callListener != null) {
                callListener.onCallEnded(participant);
            }

            updateState(CallState.ENDED);
        }
    }

    public void handleVideoToggled(String participant) {
        if (isInCall && participant.equals(currentCallParticipant) && callListener != null) {
            callListener.onVideoToggled(participant);
        }
    }

    public void setVideoEnabled(boolean enabled) {
        System.out.println("VideoCallManager: " + (enabled ? "Bật" : "Tắt") + " video");

        isVideoEnabled = enabled;
        if (videoClient != null) {
            videoClient.setVideoEnabled(enabled);
        }

        // Thông báo cho đối phương
        if (isInCall) {
            client.getServerConnection().sendMessage(Protocol.CMD_TOGGLE_VIDEO + currentCallParticipant);
        }
    }

    public boolean isVideoEnabled() {
        return isVideoEnabled;
    }

    public void setMuted(boolean muted) {
        isMuted = muted;
        if (audioClient != null) {
            audioClient.setMuted(muted);
        }
    }

    public boolean ensureAudioClient() {
        if (audioClient == null || !audioClient.isConnected()) {
            try {
                System.out.println("VideoCallManager: Khởi tạo AudioClient...");
                audioClient = new AudioClient(serverHost, audioPort, client.getCurrentUser().getUsername());
                boolean connected = audioClient.connect();
                System.out.println("VideoCallManager: AudioClient kết nối " + (connected ? "thành công" : "thất bại"));
                return connected;
            } catch (Exception e) {
                System.err.println("Lỗi khi kết nối AudioClient: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    public boolean ensureVideoClient() {
        if (videoClient == null || !videoClient.isConnected()) {
            try {
                System.out.println("VideoCallManager: Khởi tạo VideoClient...");
                videoClient = new VideoClient(serverHost, videoPort, client.getCurrentUser().getUsername());
                boolean connected = videoClient.connect();
                System.out.println("VideoCallManager: VideoClient kết nối " + (connected ? "thành công" : "thất bại"));

                VideoClient.VideoFrameCallback tempCallback = videoFrameCallback;
                if (connected && tempCallback != null) {
                    videoClient.setFrameCallback(tempCallback);
                }

                return connected;
            } catch (Exception e) {
                System.err.println("Lỗi khi kết nối VideoClient: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    private void cleanupCall() {
        System.out.println("VideoCallManager: Dọn dẹp cuộc gọi");

        if (audioClient != null) {
            audioClient.stopCapturing();
            audioClient.disconnect();
            audioClient = null;
        }

        if (videoClient != null) {
            videoClient.stopCapturing();
            videoClient.disconnect();
            videoClient = null;
        }

        isInCall = false;
        currentCallParticipant = null;
        updateState(CallState.IDLE);
    }

    private void updateState(CallState newState) {
        CallState oldState = currentState;
        currentState = newState;

        System.out.println("VideoCallManager: Cập nhật trạng thái từ " + oldState + " sang " + newState);

        if (callListener != null && oldState != newState) {
            callListener.onCallStateChanged(newState, currentCallParticipant);
        }
    }

    // Lưu trữ callback tạm thời khi VideoClient chưa được khởi tạo
    private VideoClient.VideoFrameCallback videoFrameCallback;

    public void setCallListener(VideoCallListener listener) {
        System.out.println("VideoCallManager: Đặt CallListener mới");
        this.callListener = listener;
    }

    public void setVideoFrameCallback(VideoClient.VideoFrameCallback callback) {
        System.out.println("VideoCallManager: Đặt VideoFrameCallback mới");
        this.videoFrameCallback = callback;

        if (videoClient != null) {
            videoClient.setFrameCallback(callback);
        }
    }

    public boolean isInCall() {
        return isInCall;
    }

    public boolean isMuted() {
        return isMuted;
    }

    public String getCurrentCallParticipant() {
        return currentCallParticipant;
    }

    public CallState getCallState() {
        return currentState;
    }

    public AudioClient getAudioClient() {
        return audioClient;
    }

    public VideoClient getVideoClient() {
        return videoClient;
    }

    public interface VideoCallListener {
        void onCallStarting(String participant);
        void onCallReceived(String participant);
        void onCallAccepted(String participant);
        void onCallConnected(String participant);
        void onCallRejected(String participant);
        void onCallEnded(String participant);
        void onCallStateChanged(CallState newState, String participant);
        void onVideoToggled(String participant);
        void onStatusMessage(String message);
    }

    public static class VolumeSettings {
        public float micGain;
        public float speakerGain;
        public boolean agcEnabled;
        public int videoQuality;
        public int frameRate;
    }

    public VolumeSettings loadVolumeSettings() {
        VolumeSettings settings = new VolumeSettings();
        settings.micGain = 2.0f;
        settings.speakerGain = 2.0f;
        settings.agcEnabled = false;
        settings.videoQuality = 50;
        settings.frameRate = 15;

        try {
            File settingsFile = new File(new File(System.getProperty("user.home"), ".java_socket_chat_app"), "audio_settings.properties");
            if (settingsFile.exists()) {
                Properties props = new Properties();
                try (FileInputStream in = new FileInputStream(settingsFile)) {
                    props.load(in);

                    String micGainStr = props.getProperty("mic_gain");
                    String speakerGainStr = props.getProperty("speaker_gain");
                    String agcEnabledStr = props.getProperty("agc_enabled");
                    String videoQualityStr = props.getProperty("video_quality");
                    String frameRateStr = props.getProperty("frame_rate");

                    if (micGainStr != null) {
                        settings.micGain = Float.parseFloat(micGainStr);
                    }

                    if (speakerGainStr != null) {
                        settings.speakerGain = Float.parseFloat(speakerGainStr);
                    }

                    if (agcEnabledStr != null) {
                        settings.agcEnabled = Boolean.parseBoolean(agcEnabledStr);
                    }

                    if (videoQualityStr != null) {
                        settings.videoQuality = Integer.parseInt(videoQualityStr);
                    }

                    if (frameRateStr != null) {
                        settings.frameRate = Integer.parseInt(frameRateStr);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Không thể đọc cài đặt: " + e.getMessage());
        }

        return settings;
    }

    public void saveVolumeSettings(float micGain, float speakerGain, boolean agcEnabled,
                                   int videoQuality, int frameRate) {
        try {
            Properties props = new Properties();
            props.setProperty("mic_gain", String.valueOf(micGain));
            props.setProperty("speaker_gain", String.valueOf(speakerGain));
            props.setProperty("agc_enabled", String.valueOf(agcEnabled));
            props.setProperty("video_quality", String.valueOf(videoQuality));
            props.setProperty("frame_rate", String.valueOf(frameRate));

            File settingsDir = new File(System.getProperty("user.home"), ".java_socket_chat_app");
            if (!settingsDir.exists()) {
                settingsDir.mkdirs();
            }

            File settingsFile = new File(settingsDir, "audio_settings.properties");
            try (FileOutputStream out = new FileOutputStream(settingsFile)) {
                props.store(out, "Audio and Video Settings");
            }
        } catch (Exception e) {
            System.err.println("Không thể lưu cài đặt: " + e.getMessage());
        }
    }
}