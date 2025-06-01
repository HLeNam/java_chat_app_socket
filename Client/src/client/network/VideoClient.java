package client.network;

import com.github.sarxos.webcam.Webcam;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class VideoClient implements Runnable {
    private String serverHost;
    private int serverPort;
    private String username;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private AtomicBoolean running = new AtomicBoolean(false);
    private AtomicBoolean isSending = new AtomicBoolean(false);
    private AtomicBoolean isVideoEnabled = new AtomicBoolean(true);
    private Thread receiveThread;
    private Thread captureThread;
    private Webcam webcam;
    private VideoFrameCallback frameCallback;
    private static int totalInstances = 0;
    private final int instanceId;
    private int frameRate = 15;  // Khung hình/giây
    private int quality = 50;    // Chất lượng nén JPEG (0-100)
    private final Object webcamLock = new Object();
    private final AtomicLong lastFrameTimestamp = new AtomicLong(0);

    public VideoClient(String serverHost, int serverPort, String username) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.username = username;
        this.instanceId = ++totalInstances;
        System.out.println("[VideoClient #" + instanceId + "] Đang tạo instance mới cho " + username);
    }

    public boolean connect() {
        try {
            System.out.println("[VideoClient #" + instanceId + "] Đang kết nối đến " + serverHost + ":" + serverPort + "...");
            socket = new Socket(serverHost, serverPort);
            in = socket.getInputStream();
            out = socket.getOutputStream();

            out.write(username.getBytes(StandardCharsets.UTF_8));
            out.flush();

            initWebcam();

            running.set(true);
            receiveThread = new Thread(this);
            receiveThread.start();

            System.out.println("[VideoClient #" + instanceId + "] Đã kết nối thành công đến Video Server");
            return true;
        } catch (IOException e) {
            System.err.println("[VideoClient #" + instanceId + "] Không thể kết nối đến Video Server: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void initWebcam() {
        try {
            // Lấy webcam mặc định
            webcam = Webcam.getDefault();
            if (webcam != null) {
                // Đặt kích thước khung hình là 320x240 cho local video (nhỏ hơn để tiết kiệm băng thông)
                webcam.setViewSize(new java.awt.Dimension(320, 240));
                System.out.println("[VideoClient] Đã khởi tạo webcam: " + webcam.getName());
            } else {
                System.err.println("[VideoClient] Không tìm thấy webcam trên thiết bị này");
            }
        } catch (Exception e) {
            System.err.println("[VideoClient] Lỗi khi khởi tạo webcam: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void startCapturing() {
        System.out.println("[VideoClient #" + instanceId + "] startCapturing() được gọi. isSending=" + isSending.get() +
                ", webcam=" + (webcam != null ? "available" : "null"));

        if (isSending.get()) {
            System.out.println("[VideoClient #" + instanceId + "] Đã đang gửi video, bỏ qua yêu cầu");
            return;
        }

        if (webcam == null) {
            System.err.println("[VideoClient #" + instanceId + "] Webcam không khả dụng, không thể bắt đầu gửi video");
            return;
        }

        try {
            synchronized (webcamLock) {
                if (!webcam.isOpen()) {
                    try {
                        System.out.println("[VideoClient] Mở webcam...");
                        webcam.open();
                        System.out.println("[VideoClient] Webcam đã mở: " + webcam.isOpen());
                    } catch (com.github.sarxos.webcam.WebcamLockException e) {
                        System.err.println("[VideoClient] Webcam đã bị khóa: " + e.getMessage());
                        System.out.println("[VideoClient] Giả định webcam đã mở và tiếp tục...");
                    }
                } else {
                    System.out.println("[VideoClient] Webcam đã được mở trước đó");
                }
            }

            isSending.set(true);
            captureThread = new Thread(() -> {
                long frameInterval = 1000 / frameRate;
                long lastFrameTime = 0;

                while (isSending.get() && running.get()) {
                    try {
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastFrameTime >= frameInterval &&
                                currentTime - lastFrameTimestamp.get() >= frameInterval/2) {

                            lastFrameTimestamp.set(currentTime);

                            if (isVideoEnabled.get()) {
                                BufferedImage image;
                                boolean webcamOpen = false;

                                synchronized (webcamLock) {
                                    webcamOpen = webcam != null && webcam.isOpen();
                                    image = webcamOpen ? webcam.getImage() : null;
                                }

                                if (image != null) {
                                    if (frameCallback != null) {
                                        try {
                                            BufferedImage localFrame = copyImage(image);
                                            if (localFrame != null) {
                                                frameCallback.onFrameReceived(localFrame);
                                            }
                                        } catch (Exception e) {
                                            System.err.println("[VideoClient] Lỗi khi tạo bản sao frame local: " + e.getMessage());
                                        }
                                    }

                                    sendVideoFrame(image);
                                    lastFrameTime = currentTime;
                                }
                            } else {
                                sendBlackFrame();
                                lastFrameTime = currentTime;
                            }
                            Thread.sleep(1);
                        } else {
                            Thread.sleep(1);
                        }
                    } catch (Exception e) {
                        if (isSending.get()) {
                            System.err.println("[VideoClient] Lỗi khi ghi và gửi khung hình: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }

                System.out.println("[VideoClient] Đã dừng ghi và gửi khung hình");
            }, "WebcamCapture");

            captureThread.start();
            System.out.println("[VideoClient] Đã bắt đầu ghi và gửi khung hình");
        } catch (Exception e) {
            System.err.println("[VideoClient] Lỗi khi bắt đầu ghi khung hình: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private BufferedImage copyImage(BufferedImage source) {
        if (source == null) {
            System.err.println("[VideoClient] Source image is null");
            return null;
        }

        try {
            int type = (source.getType() == 0) ? BufferedImage.TYPE_INT_RGB : source.getType();

            BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), type);
            Graphics2D g = copy.createGraphics();
            g.drawImage(source, 0, 0, null);
            g.dispose();
            return copy;
        } catch (Exception e) {
            System.err.println("[VideoClient] Error copying image: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void sendVideoFrame(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality / 100.0f);

        javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
        writer.setOutput(ios);
        writer.write(null, new javax.imageio.IIOImage(image, null, null), param);

        writer.dispose();
        ios.close();

        byte[] imageData = baos.toByteArray();
        baos.close();

        ByteArrayOutputStream frameData = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(frameData);
        dos.writeInt(imageData.length);
        dos.write(imageData);

        if (out != null) {
            out.write(frameData.toByteArray());
            out.flush();
        }
    }

    private void sendBlackFrame() throws IOException {
        BufferedImage blackFrame = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        sendVideoFrame(blackFrame);
    }

    public void stopCapturing() {
        isSending.set(false);
        if (captureThread != null) {
            try {
                captureThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        synchronized (webcamLock) {
            if (webcam != null && webcam.isOpen()) {
                try {
                    webcam.close();
                    System.out.println("[VideoClient] Webcam đã được đóng");
                } catch (Exception e) {
                    System.err.println("[VideoClient] Lỗi khi đóng webcam: " + e.getMessage());
                }
            }
        }
    }

    public void setVideoEnabled(boolean enabled) {
        isVideoEnabled.set(enabled);
    }

    public boolean isVideoEnabled() {
        return isVideoEnabled.get();
    }

    public boolean isWebcamAvailable() {
        return webcam != null;
    }

    public void setFrameCallback(VideoFrameCallback callback) {
        this.frameCallback = callback;

        if (isSending.get() && webcam != null && isVideoEnabled.get()) {
            try {
                BufferedImage image = captureOneFrame();
                if (image != null && callback != null) {
                    callback.onFrameReceived(image);
                }
            } catch (Exception e) {
                System.err.println("[VideoClient] Lỗi khi lấy frame cho callback mới: " + e.getMessage());
            }
        }
    }

    public void setFrameRate(int fps) {
        this.frameRate = Math.max(1, Math.min(30, fps));
    }

    public void setQuality(int quality) {
        this.quality = Math.max(10, Math.min(100, quality));
    }

    public BufferedImage captureOneFrame() {
        if (webcam != null) {
            synchronized (webcamLock) {
                try {
                    if (!webcam.isOpen()) {
                        try {
                            webcam.open();
                        } catch (com.github.sarxos.webcam.WebcamLockException e) {
                            System.err.println("[VideoClient] Webcam đã bị khóa khi thử mở: " + e.getMessage());
                        }
                    }

                    if (webcam.isOpen()) {
                        return webcam.getImage();
                    }
                } catch (Exception e) {
                    System.err.println("[VideoClient] Lỗi khi chụp frame: " + e.getMessage());
                }
            }
        }
        return null;
    }

    public boolean isSending() {
        return isSending.get();
    }

    @Override
    public void run() {
        ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream();
        int frameSize = -1;

        while (running.get()) {
            try {
                byte[] buffer = new byte[8192];
                int bytesRead = in.read(buffer);

                if (bytesRead == -1) {
                    break;  // Kết nối đã đóng
                }

                if (bytesRead > 0) {
                    frameBuffer.write(buffer, 0, bytesRead);

                    while (frameBuffer.size() >= 4) {
                        if (frameSize < 0) {
                            byte[] sizeBytes = frameBuffer.toByteArray();
                            frameSize = ((sizeBytes[0] & 0xFF) << 24) |
                                    ((sizeBytes[1] & 0xFF) << 16) |
                                    ((sizeBytes[2] & 0xFF) << 8) |
                                    (sizeBytes[3] & 0xFF);

                            frameBuffer.reset();
                            frameBuffer.write(sizeBytes, 4, sizeBytes.length - 4);
                        }

                        if (frameBuffer.size() >= frameSize) {
                            byte[] frameBytes = frameBuffer.toByteArray();
                            processVideoFrame(frameBytes, 0, frameSize);

                            frameBuffer.reset();
                            if (frameBytes.length > frameSize) {
                                frameBuffer.write(frameBytes, frameSize, frameBytes.length - frameSize);
                            }

                            frameSize = -1;
                        } else {
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("[VideoClient] Lỗi khi nhận dữ liệu video: " + e.getMessage());
                    e.printStackTrace();
                    break;
                }
            }
        }

        disconnect();
    }

    private void processVideoFrame(byte[] frameData, int offset, int length) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(frameData, offset, length);
            BufferedImage image = ImageIO.read(bais);
            bais.close();

            if (image != null) {
                if (image.getWidth() < 400 && image.getHeight() < 300) {
                    BufferedImage resized = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = resized.createGraphics();
                    g.drawImage(image, 0, 0, 640, 480, null);
                    g.dispose();
                    image = resized;
                }

                if (frameCallback != null) {
                    frameCallback.onFrameReceived(image);
                }
            }
        } catch (Exception e) {
            System.err.println("[VideoClient] Lỗi khi xử lý khung hình: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void disconnect() {
        running.set(false);
        stopCapturing();

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

            synchronized (webcamLock) {
                if (webcam != null && webcam.isOpen()) {
                    try {
                        webcam.close();
                    } catch (Exception e) {
                        System.err.println("[VideoClient] Lỗi khi đóng webcam trong disconnect: " + e.getMessage());
                    }
                }
            }

            System.out.println("[VideoClient] Đã ngắt kết nối");
        } catch (IOException e) {
            System.err.println("[VideoClient] Lỗi khi đóng kết nối: " + e.getMessage());
        }
    }

    public interface VideoFrameCallback {
        void onFrameReceived(BufferedImage frame);
    }
}