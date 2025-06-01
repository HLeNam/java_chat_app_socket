package server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoClientHandler implements Runnable {
    private final Socket clientSocket;
    private final String username;
    private String callPartner;
    private InputStream in;
    private OutputStream out;
    private AtomicBoolean running = new AtomicBoolean(true);

    public VideoClientHandler(Socket clientSocket, String username) {
        this.clientSocket = clientSocket;
        this.username = username;
        try {
            this.in = clientSocket.getInputStream();
            this.out = clientSocket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        byte[] buffer = new byte[65536]; // Larger buffer for video frames
        int bytesRead;

        try {
            while (running.get()) {
                bytesRead = in.read(buffer);
                if (bytesRead == -1) {
                    break;
                }

                if (callPartner != null && bytesRead > 0) {
                    VideoClientHandler partner = ChatServer.getVideoServer().getClient(callPartner);
                    if (partner != null) {
                        partner.sendVideoData(buffer, bytesRead);
                    }
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                System.err.println("Lỗi xử lý dữ liệu video từ " + username + ": " + e.getMessage());
            }
        } finally {
            stop();
            ChatServer.getVideoServer().removeClient(username);
        }
    }

    public void sendVideoData(byte[] data, int length) {
        if (running.get() && out != null) {
            try {
                out.write(data, 0, length);
                out.flush();
            } catch (IOException e) {
                System.err.println("Lỗi gửi dữ liệu video đến " + username + ": " + e.getMessage());
            }
        }
    }

    public void setCallPartner(String partner) {
        this.callPartner = partner;
    }

    public void stop() {
        running.set(false);
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getUsername() {
        return username;
    }

    public String getCallPartner() {
        return callPartner;
    }
}
