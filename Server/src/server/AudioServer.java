package server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AudioServer implements Runnable {
    private ServerSocket serverSocket;
    private final int port;
    private boolean isRunning;
    private Map<String, AudioClientHandler> clients = new ConcurrentHashMap<>();

    public AudioServer(int port) {
        this.port = port;
    }

    public AudioServer(String host, int port) {
        this.port = port;
        try {
            InetAddress bindAddress = InetAddress.getByName(host);
            serverSocket = new ServerSocket(port, 50, bindAddress);
        } catch (IOException e) {
            System.err.println("Không thể khởi tạo AudioServer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void run () {
        try {
            if (serverSocket == null || serverSocket.isClosed()) {
                serverSocket = new ServerSocket(port);
            }

            isRunning = true;
            System.out.println("Audio Server đã khởi động trên port " + port);

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    handleNewConnection(clientSocket);
                } catch (IOException e) {
                    if (isRunning) {
                        System.err.println("Lỗi khi chấp nhận kết nối: " + e.getMessage());
//                        e.printStackTrace();
                    }
                }
            };
        } catch (Exception e) {
            System.err.println("Không thể khởi động Audio Server: " + e.getMessage());
//            e.printStackTrace();
        }
    }

    private void handleNewConnection(Socket clientSocket) {
        try {
            AudioClientHandler clientHandler = new AudioClientHandler(clientSocket, this);
            Thread clientThread = new Thread(clientHandler);
            clientThread.start();
        } catch (IOException e) {
            System.err.println("Lỗi khi tạo handler cho client: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void logConnectedClients() {
        System.out.println("==== Danh sách client đã kết nối với AudioServer ====");
        if (clients.isEmpty()) {
            System.out.println("Không có client nào kết nối");
        } else {
            for (String username : clients.keySet()) {
                System.out.println(" - " + username);
            }
        }
        System.out.println("==================================================");
    }


    public void registerClient(String username, AudioClientHandler handler) {
        clients.put(username, handler);
        System.out.println("Người dùng " + username + " đã kết nối với Audio Server");
        logConnectedClients();
    }

    public void unregisterClient(String username) {
        clients.remove(username);
        System.out.println("Người dùng " + username + " đã ngắt kết nối khỏi Audio Server");
    }

    public AudioClientHandler getClient(String username) {
        AudioClientHandler handler = clients.get(username);
        if (handler == null) {
            System.out.println("WARNING: Không tìm thấy AudioClientHandler cho người dùng " + username);
            logConnectedClients();
        }
        return handler;
    }

    public void stop() {
        isRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("Lỗi khi đóng Audio Server: " + e.getMessage());
                e.printStackTrace();
            }
        }

        for (AudioClientHandler handler : clients.values()) {
            handler.closeConnection();
        }
        clients.clear();
    }
}
