package client.network;

import client.ChatClient;
import util.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;

public class ServerConnection implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private ChatClient client;
    private boolean running;

    public ServerConnection(String host, int port, ChatClient client) {
        try {
            this.client = client;
            this.socket = new Socket(host, port);
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.running = true;

            System.out.println("Đã kết nối đến server: " + host + ":" + port);
        } catch (IOException e) {
            System.err.println("Lỗi khi kết nối đến server: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            String message;
            while (running && (message = in.readLine()) != null) {
                final String receivedMessage = message;
                client.handleIncomingMessage(receivedMessage);
            }
        } catch (SocketException e) {
            if (running) {
                System.err.println("Mất kết nối đến server: " + e.getMessage());
                client.handleConnectionLost();
            }
        } catch (IOException e) {
            System.err.println("Lỗi khi đọc tin nhắn từ server: " + e.getMessage());
            e.printStackTrace();
        } finally {
            disconnect();
        }
    }

    public void sendMessage(String message) {
        if (out != null && !socket.isClosed()) {
            out.println(message);
        }
    }

    public void sendPrivateMessage(String receiver, String message) {
        String formattedMessage = Protocol.CMD_PRIVATE_MSG + receiver +
                Protocol.PARAM_DELIMITER + message;
        System.out.println("Đang gửi tin nhắn riêng đến " + receiver + ": " + message);
        sendMessage(formattedMessage);
    }

    public void sendGroupMessage(String groupName, String message) {
        String formattedMessage = Protocol.CMD_GROUP_MSG + groupName +
                Protocol.PARAM_DELIMITER + message;
        System.out.println("Đang gửi tin nhắn nhóm đến " + groupName + ": " + message);
        sendMessage(formattedMessage);
    }

    public void login(String username, String password) {
        String message = Protocol.CMD_LOGIN + username +
                Protocol.PARAM_DELIMITER + password;
        System.out.println("Đang gửi tin nhắn đến server: " + message);
        sendMessage(Protocol.CMD_LOGIN + username + Protocol.PARAM_DELIMITER + password);
    }

    public void register(String username, String password, String fullName, String email) {
        sendMessage(Protocol.CMD_REGISTER + username +
                Protocol.PARAM_DELIMITER + password +
                Protocol.PARAM_DELIMITER + fullName +
                Protocol.PARAM_DELIMITER + email);
    }

    public void getChatHistory(String chatPartner, String chatType, int limit) {
        sendMessage(Protocol.CMD_GET_CHAT_HISTORY + chatPartner +
                Protocol.PARAM_DELIMITER + chatType +
                Protocol.PARAM_DELIMITER + limit);
    }

    public void loadMoreMessages(String chatPartner, String chatType, long olderThan, int limit) {
        sendMessage(Protocol.CMD_LOAD_MORE_MESSAGES +
                chatPartner + Protocol.PARAM_DELIMITER +
                chatType + Protocol.PARAM_DELIMITER +
                olderThan + Protocol.PARAM_DELIMITER +
                limit);
    }

    public void sendFileRequest(String receiver, String fileName, long fileSize, String fileId) {
        // Nếu có tham số fileId, sử dụng nó để đảm bảo ID nhất quán
        sendMessage(Protocol.CMD_FILE_SEND + receiver + Protocol.PARAM_DELIMITER +
                fileName + Protocol.PARAM_DELIMITER + fileSize);
    }

    public void acceptFileTransfer(String fileId) {
        sendMessage(Protocol.CMD_FILE_ACCEPT + fileId);
    }

    public void rejectFileTransfer(String fileId) {
        sendMessage(Protocol.CMD_FILE_REJECT + fileId);
    }

    public void getOnlineUsers() {
        sendMessage(Protocol.CMD_ONLINE_USERS);
    }

    public void exit() {
        sendMessage(Protocol.CMD_EXIT);
        disconnect();
    }

    public void disconnect() {
        running = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();

            System.out.println("Đã ngắt kết nối với server.");
        } catch (IOException e) {
            System.err.println("Lỗi khi đóng kết nối: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}
