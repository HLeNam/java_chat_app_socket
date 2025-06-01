package server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class AudioClientHandler implements Runnable {
    private Socket clientSocket;
    private AudioServer audioServer;
    private InputStream in;
    private OutputStream out;
    private String username;
    private String currentCallPartner;
    private boolean isRunning;

    public AudioClientHandler(Socket socket, AudioServer server) throws IOException {
        this.clientSocket = socket;
        this.audioServer = server;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    @Override
    public void run() {
        try {
            byte[] usernameBytes = new byte[1024];
            int bytesRead = in.read(usernameBytes);
            if (bytesRead > 0) {
                username = new String(usernameBytes, 0, bytesRead, StandardCharsets.UTF_8).trim();
                System.out.println("Người dùng " + username + " đã kết nối với Audio Server");
                audioServer.registerClient(username, this);
            }

            isRunning = true;

            byte[] buffer = new byte[1024];
            while (isRunning) {
                try {
                    bytesRead = in.read(buffer);
                    if (bytesRead == -1) {
                        break;
                    }

                    if (bytesRead > 0 && currentCallPartner != null) {
                        AudioClientHandler partnerHandler = audioServer.getClient(currentCallPartner);
                        if (partnerHandler != null) {
                            partnerHandler.sendAudioData(buffer, bytesRead);
                        }
                    }
                } catch (IOException e) {
                    if (isRunning) {
                        System.err.println("Lỗi khi đọc dữ liệu âm thanh: " + e.getMessage());
                        break;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Lỗi trong AudioClientHandler: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }

    public void sendAudioData(byte[] audioData, int length) {
        if (!isRunning || out == null) return;

        try {
            out.write(audioData, 0, length);
            out.flush();
        } catch (IOException e) {
            System.err.println("Lỗi khi gửi dữ liệu âm thanh: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setCallPartner(String partnerUsername) {
        this.currentCallPartner = partnerUsername;
        System.out.println("Đã thiết lập kênh âm thanh giữa " + username + " và " + partnerUsername);
    }

    public void endCall() {
        this.currentCallPartner = null;
    }

    public void closeConnection() {
        isRunning = false;

        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Lỗi khi đóng kết nối audio: " + e.getMessage());
            e.printStackTrace();
        }

        if (username != null) {
            audioServer.unregisterClient(username);
        }
    }

    public String getUsername() {
        return username;
    }
}
