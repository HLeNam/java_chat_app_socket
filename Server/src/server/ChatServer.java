package server;

import db.DatabaseManager;
import service.FileService;
import util.Protocol;

import javax.swing.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private static final int PORT = 9999;
    private static final int FILE_PORT = 9998;
    private static final int AUDIO_PORT = 9997; 
    private static final int VIDEO_PORT = 9996;

    private ServerSocket serverSocket;

    private ServerSocket fileServerSocket;

    private static AudioServer audioServer;

    private static VideoServer videoServer;

    private static Map<String, ClientHandler> clientHandlers = new ConcurrentHashMap<>();

    private static Map<String, String> onlineUsers = new ConcurrentHashMap<>();

    private int chatPort = 9999;
    private int filePort = 9998;
    private int audioPort = 9997;
    private int videoPort = 9996;
    private String hostAddress = "0.0.0.0"; // Mặc định lắng nghe trên tất cả giao diện mạng

    public ChatServer() {
        instance = this;
    }

    public ChatServer(String hostAddress, int chatPort, int filePort, int audioPort, int videoPort) {
        this.hostAddress = hostAddress;
        this.chatPort = chatPort;
        this.filePort = filePort;
        this.audioPort = audioPort;
        this.videoPort = videoPort;

        instance = this;
    }

    public void start() {
        try {
            DatabaseManager.initDatabase();

            // Tạo ServerSocket với địa chỉ host được chỉ định
            InetAddress bindAddress = InetAddress.getByName(hostAddress);
            serverSocket = new ServerSocket(chatPort, 50, bindAddress);

            System.out.println("Chat server đã khởi động ở " + hostAddress + ":" + chatPort);

            FileService.startFileServer(hostAddress, filePort);

            // Khởi động audio server
            audioServer = new AudioServer(hostAddress, audioPort);
            new Thread(audioServer).start();
            System.out.println("Audio Server đã khởi động trên " + hostAddress + ":" + audioPort);

            // Khởi động video server
            videoServer = new VideoServer(hostAddress, videoPort);
            new Thread(videoServer).start();
            System.out.println("Video Server đã khởi động trên " + hostAddress + ":" + videoPort);

            while (true) {
                Socket socket = serverSocket.accept();

                ClientHandler clientHandler = new ClientHandler(socket);
                clientHandlers.put(clientHandler.getId(), clientHandler);
                new Thread(clientHandler).start();

                System.out.println("Client mới đã kết nối: " + socket.getInetAddress());
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi khởi động server: " + e.getMessage());
//            e.printStackTrace();
        }
    }

    public void stop() {
        try {
            for (ClientHandler clientHandler : clientHandlers.values()) {
                clientHandler.closeConnection();
            }

            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            if (fileServerSocket != null && !fileServerSocket.isClosed()) {
                fileServerSocket.close();
            }

            FileService.stopFileServer();

            System.out.println("Chat server đã dừng.");

            // Dừng audio server
            if (audioServer != null) {
                audioServer.stop();
                System.out.println("Audio Server đã dừng");
            }

            // Dừng video server
            if (videoServer != null) {
                videoServer.stop();
                System.out.println("Video Server đã dừng");
            }
        }  catch (IOException e) {
            System.err.println("Lỗi khi đóng server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static ClientHandler getClientHandler(String username) {
        String handlerId = onlineUsers.get(username);
        if (handlerId != null) {
            // Lấy instance của ChatServer
            ChatServer instance = ChatServer.getInstance();
            if (instance != null) {
                return instance.clientHandlers.get(handlerId);
            }
        }
        return null;
    }

    public static void broadcastToAllClients(String message) {
        ChatServer instance = ChatServer.getInstance();
        if (instance != null) {
            for (String username : onlineUsers.keySet()) {
                ClientHandler handler = getClientHandler(username);
                if (handler != null) {
                    handler.sendMessage(message);
                }
            }
        }
    }

    public static void broadcastMessage(String message) {
        for (ClientHandler handler : clientHandlers.values()) {
            handler.sendMessage(message);
        }
    }

    public static List<String> getOnlineUsers() {
        return new ArrayList<>(onlineUsers.keySet());
    }

    public static void addOnlineUser(String username, String handlerId) {
        onlineUsers.put(username, handlerId);
        System.out.println("Người dùng đã đăng nhập: " + username);
    }

    public static void removeOnlineUser(String username) {
        onlineUsers.remove(username);
        System.out.println("Người dùng đã đăng xuất: " + username);
    }

    public static boolean isUserOnline(String username) {
        return onlineUsers.containsKey(username);
    }

    private static ChatServer instance;

    public static ChatServer getInstance() {
        return instance;
    }

    public void removeClientHandler(String handlerId) {
        clientHandlers.remove(handlerId);
        System.out.println("Client handler đã bị xóa: " + handlerId);
    }

    public static void setupAudioCall(String caller, String receiver) {
        if (audioServer == null) {
            System.err.println("ERROR: AudioServer chưa được khởi động");
            return;
        }

        System.out.println("Đang thiết lập kênh âm thanh giữa " + caller + " và " + receiver);

        audioServer.logConnectedClients();

        AudioClientHandler callerHandler = audioServer.getClient(caller);
        AudioClientHandler receiverHandler = audioServer.getClient(receiver);

        if (callerHandler == null) {
            System.err.println("ERROR: Không tìm thấy AudioClientHandler cho người gọi: " + caller);
        }

        if (receiverHandler == null) {
            System.err.println("ERROR: Không tìm thấy AudioClientHandler cho người nhận: " + receiver);
        }

        if (callerHandler != null && receiverHandler != null) {
            callerHandler.setCallPartner(receiver);
            receiverHandler.setCallPartner(caller);
            System.out.println("SUCCESS: Đã thiết lập kênh âm thanh giữa " + caller + " và " + receiver);

            notifyAudioCallSetupSuccess(caller, receiver);
        } else {
            System.err.println("ERROR: Không thể thiết lập kênh âm thanh do AudioClientHandler không tồn tại");

            notifyAudioCallSetupFailure(caller, receiver);
        }
    }

    private static void notifyAudioCallSetupSuccess(String caller, String receiver) {
        ClientHandler callerHandler = getClientHandler(caller);
        ClientHandler receiverHandler = getClientHandler(receiver);

        if (callerHandler != null) {
            callerHandler.sendMessage(Protocol.SVR_INFO + "Kênh âm thanh đã được thiết lập thành công");
        }

        if (receiverHandler != null) {
            receiverHandler.sendMessage(Protocol.SVR_INFO + "Kênh âm thanh đã được thiết lập thành công");
        }
    }

    private static void notifyAudioCallSetupFailure(String caller, String receiver) {
        ClientHandler callerHandler = getClientHandler(caller);
        ClientHandler receiverHandler = getClientHandler(receiver);

        if (callerHandler != null) {
            callerHandler.sendMessage(Protocol.SVR_ERROR + "Không thể thiết lập kênh âm thanh, vui lòng thử lại");
        }

        if (receiverHandler != null) {
            receiverHandler.sendMessage(Protocol.SVR_ERROR + "Không thể thiết lập kênh âm thanh, vui lòng thử lại");
        }
    }

    public static void endAudioCall(String user1, String user2) {
        AudioClientHandler user1Handler = audioServer.getClient(user1);
        AudioClientHandler user2Handler = audioServer.getClient(user2);

        if (user1Handler != null) {
            user1Handler.endCall();
        }

        if (user2Handler != null) {
            user2Handler.endCall();
        }
    }

    public static void setupVideoCall(String caller, String receiver) {
        if (videoServer != null) {
            videoServer.setupVideoCall(caller, receiver);
        } else {
            System.err.println("VideoServer chưa được khởi động");
        }
    }

    public static VideoServer getVideoServer() {
        return videoServer;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ServerConfigUI ui = new ServerConfigUI();
            ui.setVisible(true);
        });
    }
}