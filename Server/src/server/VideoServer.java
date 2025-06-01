package server;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VideoServer implements Runnable {
    private final int port;
    private ServerSocket serverSocket;
    private boolean running = true;
    private Map<String, VideoClientHandler> clients = new ConcurrentHashMap<>();

    public VideoServer(int port) {
        this.port = port;
    }

    public VideoServer(String host, int port) {
        this.port = port;
        try {
            InetAddress bindAddress = InetAddress.getByName(host);
            serverSocket = new ServerSocket(port, 50, bindAddress);
        } catch (IOException e) {
            System.err.println("Không thể khởi tạo VideoServer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            if (serverSocket == null || serverSocket.isClosed()) {
                serverSocket = new ServerSocket(port);
            }

            System.out.println("Video Server đã khởi động trên port " + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Kết nối video mới: " + clientSocket.getRemoteSocketAddress());

                // Đọc tên người dùng
                InputStream is = clientSocket.getInputStream();
                byte[] buffer = new byte[1024];
                int bytesRead = is.read(buffer);

                if (bytesRead > 0) {
                    String username = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);

                    // Tạo handler mới cho client
                    VideoClientHandler handler = new VideoClientHandler(clientSocket, username);
                    clients.put(username, handler);
                    new Thread(handler).start();

                    System.out.println("Người dùng " + username + " đã kết nối đến Video Server");
                }
            }
        } catch (IOException e) {
            System.err.println("Lỗi Video Server: " + e.getMessage());
//            e.printStackTrace();
        } finally {
            stop();
        }
    }

    public void stop() {
        running = false;
        for (VideoClientHandler handler : clients.values()) {
            handler.stop();
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public VideoClientHandler getClient(String username) {
        return clients.get(username);
    }

    public void removeClient(String username) {
        clients.remove(username);
        System.out.println("Người dùng " + username + " đã ngắt kết nối khỏi Video Server");
    }

    public void setupVideoCall(String caller, String receiver) {
        VideoClientHandler callerHandler = getClient(caller);
        VideoClientHandler receiverHandler = getClient(receiver);

        if (callerHandler != null && receiverHandler != null) {
            callerHandler.setCallPartner(receiver);
            receiverHandler.setCallPartner(caller);
            System.out.println("Đã thiết lập kênh video giữa " + caller + " và " + receiver);
        } else {
            System.err.println("Không thể thiết lập kênh video: callerHandler=" +
                    (callerHandler != null ? "OK" : "null") + ", receiverHandler=" +
                    (receiverHandler != null ? "OK" : "null"));
        }
    }
}
