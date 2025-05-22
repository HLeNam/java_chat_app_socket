package server;

import db.DatabaseManager;
import service.FileService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private static final int PORT = 9999;
    private static final int FILE_PORT = 9998;

    private ServerSocket serverSocket;

    private ServerSocket fileServerSocket;

    private static Map<String, ClientHandler> clientHandlers = new ConcurrentHashMap<>();

    private static Map<String, String> onlineUsers = new ConcurrentHashMap<>();

    public void start() {
        try {
            DatabaseManager.initDatabase();

            serverSocket = new ServerSocket(PORT);

            System.out.println("Chat server đã khởi động ở port " + PORT);

            FileService.startFileServer(FILE_PORT);

            while (true) {
                Socket socket = serverSocket.accept();

                ClientHandler clientHandler = new ClientHandler(socket);
                clientHandlers.put(clientHandler.getId(), clientHandler);
                new Thread(clientHandler).start();

                System.out.println("Client mới đã kết nối: " + socket.getInetAddress());
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi khởi động server: " + e.getMessage());
            e.printStackTrace();
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

//    public static void broadcastToGroup(String groupName, String message) {
//        Set<String> members = GroupManager.getMembers(groupName);
//        for (String member : members) {
//            ClientHandler handler = getClientHandler(member);
//            if (handler != null) {
//                handler.sendMessage(message);
//            }
//        }
//    }

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

    public static void main(String[] args) {
        instance = new ChatServer();
        instance.start();
    }
}