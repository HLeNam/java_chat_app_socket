package client;

import client.network.FileTransferClient;
import client.network.ServerConnection;
import db.LocalStorage;
import model.Group;
import model.User;
import ui.ChatFrame;
import ui.LoginFrame;
import util.Protocol;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ChatClient {
    private User currentUser;
    private ServerConnection serverConnection;
    private String serverHost;
    private int serverPort;
    private int filePort;
    private ChatFrame chatFrame;
    private LoginFrame loginFrame;
    private LoginFrame registerFrame;

    private String currentHistoryContext = null;
    private List<String[]> pendingHistoryMessages = new ArrayList<>();

    private String loadMoreContext = null;
    private List<String[]> pendingOlderMessages = new ArrayList<>();
    private Map<String, Long> oldestMessageTimestamp = new HashMap<>();

    private Map<String, Group> groups = new HashMap<>();

    private Map<String, File> filesToUpload = new HashMap<>();
    private Map<String, String[]> filesToDownload = new HashMap<>();
    private LocalStorage localStorage;
    private FileTransferClient fileTransferClient;

    public ChatClient() {
        this("localhost", 9999, 9998);
    }

    public ChatClient(String host, int port, int filePort) {
        this.serverHost = host;
        this.serverPort = port;
        this.filePort = filePort;
    }

    public boolean connect() {
        serverConnection = new ServerConnection(serverHost, serverPort, this);
        new Thread(serverConnection).start();
        return serverConnection.isConnected();
    }

    public void initialize(String username) {
        this.currentUser = new User(username);

        this.localStorage = new LocalStorage(username);

        this.fileTransferClient = new FileTransferClient(serverHost, filePort, this);
    }

    public void sendMessage(String message) {
        if (serverConnection != null) {
            serverConnection.sendMessage(message);
        }
    }

    public void sendPrivateMessage(String receiver, String message) {
        if (serverConnection != null) {
            serverConnection.sendPrivateMessage(receiver, message);
        }
    }

    public void createGroup(String groupName) {
        serverConnection.sendMessage(Protocol.CMD_CREATE_GROUP + groupName);
    }

    public void addToGroup(String groupName, String username) {
        serverConnection.sendMessage(Protocol.CMD_ADD_TO_GROUP +
                groupName + Protocol.PARAM_DELIMITER + username);
    }

    public void leaveGroup(String groupName) {
        serverConnection.sendMessage(Protocol.CMD_LEAVE_GROUP + groupName);
    }

    public void getGroups() {
        serverConnection.sendMessage(Protocol.CMD_GET_GROUPS);
    }

    public void sendGroupMessage(String groupName, String message) {
        serverConnection.sendMessage(Protocol.CMD_GROUP_MSG +
                groupName + Protocol.PARAM_DELIMITER + message);
    }

    public void removeFromGroup(String groupName, String username) {
        serverConnection.sendMessage(Protocol.CMD_REMOVE_FROM_GROUP +
                groupName + Protocol.PARAM_DELIMITER + username);
    }

    public void getChatHistory(String chatPartner, boolean isGroup) {
        String chatType = isGroup ? "group" : "private";
        serverConnection.getChatHistory(chatPartner, chatType, 20);  // Lấy 20 tin nhắn gần nhất
        currentHistoryContext = chatPartner;
    }

    public void loadMoreMessages(String chatPartner, boolean isGroup) {
        String chatType = isGroup ? "group" : "private";
        Long oldestTimestamp = oldestMessageTimestamp.get(chatPartner);

        if (oldestTimestamp == null) {
            oldestTimestamp = System.currentTimeMillis();
            oldestMessageTimestamp.put(chatPartner, oldestTimestamp);
        }

        serverConnection.loadMoreMessages(chatPartner, chatType, oldestTimestamp, 10);
        loadMoreContext = chatPartner;
    }

    private void handleChatHistoryStart() {
        pendingHistoryMessages.clear();
    }

    private void handleChatHistoryItem(String message) {
        String content = message.substring(Protocol.SVR_CHAT_HISTORY_ITEM.length());
        String[] parts = content.split("\\|", 3);

        if (parts.length >= 3) {
            pendingHistoryMessages.add(parts);

            long timestamp = Long.parseLong(parts[2]);
            Long currentOldest = oldestMessageTimestamp.get(currentHistoryContext);

            if (currentOldest == null || timestamp < currentOldest) {
                oldestMessageTimestamp.put(currentHistoryContext, timestamp);
            }
        }
    }

    private void handleChatHistoryEnd() {
        String chatContext = currentHistoryContext;
        List<String[]> messages = new ArrayList<>(pendingHistoryMessages);

        if (chatFrame != null && chatContext != null && !messages.isEmpty()) {
            chatFrame.displayChatHistory(chatContext, messages);
        }

        currentHistoryContext = null;
        pendingHistoryMessages.clear();
    }

    private void handleLoadMoreStart() {
        pendingOlderMessages.clear();
    }

    private void handleLoadMoreItem(String message) {
        String content = message.substring(Protocol.SVR_LOAD_MORE_ITEM.length());
        String[] parts = content.split("\\|", 3);

        if (parts.length >= 3) {
            pendingOlderMessages.add(parts);

            // Cập nhật timestamp của tin nhắn cũ nhất
            long timestamp = Long.parseLong(parts[2]);
            Long currentOldest = oldestMessageTimestamp.get(loadMoreContext);

            if (currentOldest == null || timestamp < currentOldest) {
                oldestMessageTimestamp.put(loadMoreContext, timestamp);
            }
        }
    }

    private void handleLoadMoreEnd() {
        if (chatFrame != null && loadMoreContext != null && !pendingOlderMessages.isEmpty()) {
            chatFrame.displayOlderMessages(loadMoreContext, pendingOlderMessages);
        }

        loadMoreContext = null;
        pendingOlderMessages.clear();
    }

    public void showLoginFrame() {
        SwingUtilities.invokeLater(() -> {
            loginFrame = new LoginFrame(this);
            loginFrame.setVisible(true);
        });
    }

    public void showRegisterFrame() {
        SwingUtilities.invokeLater(() -> {
            registerFrame = new LoginFrame(this);
            registerFrame.setVisible(true);
        });
    }

    public void showChatFrame() {
        SwingUtilities.invokeLater(() -> {
            chatFrame = new ChatFrame(this);
            chatFrame.setVisible(true);

            serverConnection.getOnlineUsers();

            loginFrame.dispose();
        });
    }

    public void login(String username, String password) {
        serverConnection.login(username, password);
    }

    public void register(String username, String password, String fullName, String email) {
        serverConnection.register(username, password, fullName, email);
    }

    // Gửi yêu cầu chuyển file
    public void sendFileRequest(String receiver, String fileName, long fileSize) {
        serverConnection.sendFileRequest(receiver, fileName, fileSize);
    }

    // Chấp nhận file
    public void acceptFileTransfer(String fileId) {
        serverConnection.acceptFileTransfer(fileId);
    }

    // Từ chối file
    public void rejectFileTransfer(String fileId) {
        serverConnection.rejectFileTransfer(fileId);
    }

    // Thêm file vào danh sách chờ upload
    public void addFileToUpload(String fileId, File file) {
        filesToUpload.put(fileId, file);
    }

    // Lấy file cần upload theo ID
    public File getFileToUpload(String fileId) {
        return filesToUpload.get(fileId);
    }

    // Thêm file vào danh sách chờ download
    public void addFileToDownload(String fileId, String sender, String fileName,
                                  long fileSize, String savePath) {
        String[] fileInfo = {sender, fileName, String.valueOf(fileSize), savePath};
        filesToDownload.put(fileId, fileInfo);
    }

    // Lấy thông tin file cần download theo ID
    public String[] getFileToDownload(String fileId) {
        return filesToDownload.get(fileId);
    }

    // Upload file
    public void uploadFile(String fileId, File file, String receiver,
                           Consumer<Integer> progressCallback) {
        fileTransferClient.uploadFile(fileId, file, receiver, progressCallback);
    }

    // Download file
    public void downloadFile(String fileId, String sender, String fileName,
                             long fileSize, String savePath, Consumer<Integer> progressCallback) {
        fileTransferClient.downloadFile(fileId, sender, fileName, fileSize,
                savePath, progressCallback);
    }

    // Hiển thị tin nhắn file trong chat
    public void displayFileMessage(String chatContext, String sender, String fileName,
                                   long fileSize, String fileId, String status) {
        if (chatFrame != null) {
            chatFrame.displayFileMessage(chatContext, sender, fileName, fileSize, fileId, status);
        }
    }

    // Cập nhật trạng thái file trong chat
    public void updateFileStatusInChat(String fileId, String chatContext, String status) {
        if (chatFrame != null) {
            chatFrame.updateFileStatus(fileId, status);
        }
    }

    public void handleIncomingMessage(String message) {
        if (message.startsWith(Protocol.SVR_LOGIN_SUCCESS)) {
            handleLoginSuccess();
        } else if (message.startsWith(Protocol.SVR_LOGIN_FAIL)) {
            handleLoginFail(message);
        } else if (message.startsWith(Protocol.SVR_REGISTER_SUCCESS)) {
            handleRegisterSuccess();
        } else if (message.startsWith(Protocol.SVR_REGISTER_FAIL)) {
            handleRegisterFail(message);
        } else if (message.startsWith(Protocol.SVR_ONLINE_USERS)) {
            handleOnlineUsers(message);
        } else if (message.startsWith(Protocol.SVR_USER_JOINED)) {
            handleUserJoined(message);
        } else if (message.startsWith(Protocol.SVR_USER_LEFT)) {
            handleUserLeft(message);
        } else if (message.startsWith(Protocol.SVR_PRIVATE_MSG)) {
            handlePrivateMessage(message);
        } else if (message.startsWith(Protocol.SVR_GLOBAL_MSG)) {
            handleGlobalMessage(message);
        } else if (message.startsWith(Protocol.SVR_CHAT_HISTORY_START)) {
            handleChatHistoryStart();
        } else if (message.startsWith(Protocol.SVR_CHAT_HISTORY_ITEM)) {
            handleChatHistoryItem(message);
        } else if (message.startsWith(Protocol.SVR_CHAT_HISTORY_END)) {
            handleChatHistoryEnd();
        } else if (message.startsWith(Protocol.SVR_LOAD_MORE_START)) {
            handleLoadMoreStart();
        } else if (message.startsWith(Protocol.SVR_LOAD_MORE_ITEM)) {
            handleLoadMoreItem(message);
        } else if (message.startsWith(Protocol.SVR_LOAD_MORE_END)) {
            handleLoadMoreEnd();
        } else if (message.startsWith(Protocol.SVR_CREATE_GROUP_SUCCESS)) {
            handleCreateGroupSuccess(message);
        } else if (message.startsWith(Protocol.SVR_ADD_TO_GROUP_SUCCESS)) {
            handleAddToGroupSuccess(message);
        } else if (message.startsWith(Protocol.SVR_GROUP_USER_ADDED)) {
            handleGroupUserAdded(message);
        } else if (message.startsWith(Protocol.SVR_LEFT_GROUP)) {
            handleLeftGroup(message);
        } else if (message.startsWith(Protocol.SVR_GROUP_USER_LEFT)) {
            handleGroupUserLeft(message);
        } else if (message.startsWith(Protocol.SVR_REMOVED_FROM_GROUP)) {
            handleRemovedFromGroup(message);
        } else if (message.startsWith(Protocol.SVR_REMOVE_FROM_GROUP_SUCCESS)) {
            handleRemoveFromGroupSuccess(message);
        } else if (message.startsWith(Protocol.SVR_GROUP_LIST)) {
            handleGroupList(message);
        } else if (message.startsWith(Protocol.SVR_GROUP_MSG)) {
            handleGroupMessage(message);
        } else if (message.startsWith(Protocol.SVR_ERROR)) {
            handleError(message);
        } else if (message.startsWith(Protocol.SVR_ADDED_TO_GROUP)) {
            handleAddedToGroup(message);
        } else if (message.startsWith(Protocol.SVR_FILE_REQUEST)) {
            handleFileRequest(message);
        } else if (message.startsWith(Protocol.SVR_FILE_ACCEPT)) {
            handleFileAccepted(message);
        } else if (message.startsWith(Protocol.SVR_FILE_REJECT)) {
            handleFileRejected(message);
        }
    }

    private void handleLoginSuccess() {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, "Đăng nhập thành công!");
            showChatFrame();
        });
    }

    private void handleLoginFail(String message) {
        String reason = message.substring(Protocol.SVR_LOGIN_FAIL.length());
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, "Đăng nhập thất bại: " + reason,
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void handleRegisterSuccess() {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, "Đăng ký thành công! Bạn có thể đăng nhập ngay.");
        });
    }

    private void handleRegisterFail(String message) {
        String reason = message.substring(Protocol.SVR_REGISTER_FAIL.length());
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, "Đăng ký thất bại: " + reason,
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void handleOnlineUsers(String message) {
        String userList = message.substring(Protocol.SVR_ONLINE_USERS.length());
        String[] users = userList.isEmpty() ? new String[0] : userList.split(",");

        if (chatFrame != null) {
            chatFrame.updateOnlineUsers(users);
        }
    }

    private void handleUserJoined(String message) {
        String username = message.substring(Protocol.SVR_USER_JOINED.length());

        if (chatFrame != null) {
            chatFrame.addOnlineUser(username);
        }
    }

    private void handleUserLeft(String message) {
        String username = message.substring(Protocol.SVR_USER_LEFT.length());

        if (chatFrame != null) {
            chatFrame.removeOnlineUser(username);
        }
    }

    private void handlePrivateMessage(String message) {
        // Format: /privatemsg sender|message hoặc /privatemsg receiver|message
        String content = message.substring(Protocol.SVR_PRIVATE_MSG.length());
        String[] parts = content.split("\\|", 3);

        if (parts.length >= 2) {
            String contactName = parts[0];  // sender hoặc receiver
            String messageContent = parts[1];
            boolean isSent = parts.length >= 3 && parts[2].equals("SENT");

            // Xác định người gửi tin nhắn
            String sender;
            String chatContext;

            if (isSent) {
                sender = currentUser.getUsername();
                chatContext = contactName;
            } else {
                sender = contactName;
                chatContext = contactName;
            }

            if (chatFrame != null) {
                chatFrame.displayMessage(chatContext, sender, messageContent);
            }
        }
    }

    private void handleGlobalMessage(String message) {
        // Format: /globalmsg sender|message
        String content = message.substring(Protocol.SVR_GLOBAL_MSG.length());
        String[] parts = content.split("\\|", 2);

        if (parts.length == 2) {
            String sender = parts[0];
            String messageContent = parts[1];

            if (chatFrame != null) {
                chatFrame.displayGlobalMessage(sender, messageContent);
            }
        }
    }

    private void handleAddToGroupSuccess(String message) {
        // Format: /addtogroup_success groupName|username
        String content = message.substring(Protocol.SVR_ADD_TO_GROUP_SUCCESS.length());
        String[] parts = content.split("\\|", 2);

        if (parts.length == 2) {
            String groupName = parts[0];
            String addedUser = parts[1];

            // Cập nhật thông tin nhóm
            Group group = groups.get(groupName);
            if (group != null) {
                group.addMember(addedUser);

                if (chatFrame != null) {
                    chatFrame.updateGroupMembers(group);
                    chatFrame.addGroup(group);
                }
            }
        }
    }

    private void handleAddedToGroup(String message) {
        // Format: /addedtogroup groupName|adder
        String content = message.substring(Protocol.SVR_ADDED_TO_GROUP.length());
        String[] parts = content.split("\\|", 2);

        if (parts.length == 2) {
            String groupName = parts[0];
            String adder = parts[1];

            getGroups();

            // Hiển thị thông báo
            if (chatFrame != null) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(chatFrame,
                            "Bạn đã được " + adder + " thêm vào nhóm " + groupName,
                            "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                });
            }
        }
    }
//
//    private void handleNewGroup(String message) {
//        // Format: /newgroup groupName|creator
//        String content = message.substring(Protocol.SVR_NEW_GROUP.length());
//        String[] parts = content.split("\\|", 2);
//
//        if (parts.length == 2) {
//            String groupName = parts[0];
//            String creator = parts[1];
//
//            // Kiểm tra nếu không phải là nhóm của mình
//            if (!creator.equals(currentUser.getUsername())) {
//                // Thông báo có nhóm mới tạo
//                if (chatFrame != null) {
//                    chatFrame.notifyNewGroup(groupName, creator);
//                }
//            }
//        }
//    }

    //    private void handleCreateGroupSuccess(String message) {
//        String content = message.substring(Protocol.SVR_CREATE_GROUP_SUCCESS.length());
//        String[] parts = content.split("\\|", 2);
//
//        if (parts.length == 2) {
//            String groupName = parts[0];
//            String targetUser = parts[1];
//
//            Group group = groups.get(groupName);
//            if (group != null) {
//                group.addMember(targetUser);
//
//                if (chatFrame != null) {
//                    chatFrame.updateGroupMembers(group);
//                    chatFrame.displayGroupSystemMessage(groupName,
//                            targetUser + " đã được thêm vào nhóm bởi " + currentUser.getUsername());
//                }
//            }
//        }
//    }
    private void handleCreateGroupSuccess(String message) {
        String groupName = message.substring(Protocol.SVR_CREATE_GROUP_SUCCESS.length());

        // Tạo nhóm mới
        Group group = new Group(groupName, currentUser.getUsername());
        group.addMember(currentUser.getUsername());
        groups.put(groupName, group);

        // Hiển thị trên giao diện
        if (chatFrame != null) {
            chatFrame.addGroup(group);
        }
    }

    private void handleGroupUserAdded(String message) {
        String content = message.substring(Protocol.SVR_GROUP_USER_ADDED.length());
        String[] parts = content.split("\\|", 3);

        if (parts.length == 3) {
            String groupName = parts[0];
            String targetUser = parts[1];
            String adder = parts[2];

            // Cập nhật thông tin nhóm
            Group group = groups.get(groupName);
            if (group != null) {
                group.addMember(targetUser);

                // Cập nhật giao diện
                if (chatFrame != null) {
                    chatFrame.updateGroupMembers(group);
                    chatFrame.displayGroupSystemMessage(groupName,
                            targetUser + " đã được thêm vào nhóm bởi " + adder);
                }
            }
        }
    }

    private void handleLeftGroup(String message) {
        String groupName = message.substring(Protocol.SVR_LEFT_GROUP.length());

        groups.remove(groupName);

        // Cập nhật giao diện
        if (chatFrame != null) {
            chatFrame.removeGroup(groupName);
        }
    }

    private void handleGroupUserLeft(String message) {
        String content = message.substring(Protocol.SVR_GROUP_USER_LEFT.length());
        String[] parts = content.split("\\|", 3);

        if (parts.length >= 2) {
            String groupName = parts[0];
            String username = parts[1];
            boolean isRemoved = parts.length > 2 && "removed".equals(parts[2]);

            // Cập nhật thông tin nhóm
            Group group = groups.get(groupName);
            if (group != null) {
                group.removeMember(username);

                // Hiển thị thông báo trong chat
                if (chatFrame != null) {
                    String notification = isRemoved ?
                            username + " đã bị xóa khỏi nhóm" :
                            username + " đã rời khỏi nhóm";

                    chatFrame.displayGroupSystemMessage(groupName, notification);
                    chatFrame.updateGroupMembers(group);
                }
            }
        }
    }

    private void handleRemovedFromGroup(String message) {
        String content = message.substring(Protocol.SVR_REMOVED_FROM_GROUP.length());
        String[] parts = content.split("\\|", 2);

        if (parts.length == 2) {
            String groupName = parts[0];
            String remover = parts[1];

            // Xóa nhóm khỏi danh sách nhóm của người dùng
            groups.remove(groupName);

            // Thông báo cho người dùng
            if (chatFrame != null) {
                chatFrame.removeGroup(groupName);
                JOptionPane.showMessageDialog(chatFrame,
                        "Bạn đã bị " + remover + " xóa khỏi nhóm " + groupName,
                        "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private void handleRemoveFromGroupSuccess(String message) {
        String content = message.substring(Protocol.SVR_REMOVE_FROM_GROUP_SUCCESS.length());
        String[] parts = content.split("\\|", 2);

        if (parts.length == 2) {
            String groupName = parts[0];
            String targetUser = parts[1];

            // Cập nhật thông tin nhóm
            Group group = groups.get(groupName);
            if (group != null) {
                group.removeMember(targetUser);

                // Cập nhật giao diện
                if (chatFrame != null) {
                    chatFrame.updateGroupMembers(group);
                    chatFrame.displayGroupSystemMessage(groupName,
                            targetUser + " đã bị xóa khỏi nhóm bởi " + currentUser.getUsername());
                }
            }
        }
    }

    private void handleGroupList(String message) {
        String content = message.substring(Protocol.SVR_GROUP_LIST.length());

        if (content.isEmpty()) {
            // Không có nhóm nào
            return;
        }

        // Xóa tất cả nhóm hiện tại
        groups.clear();

        // Phân tích danh sách nhóm
        String[] groupEntries = content.split("\\|");
        for (String entry : groupEntries) {
            String[] parts = entry.split(":", 3);
            if (parts.length >= 3) {
                String groupName = parts[0];
                String creator = parts[1];
                String memberList = parts[2];

                // Tạo nhóm mới
                Group group = new Group(groupName, creator);

                // Thêm thành viên
                String[] members = memberList.split(",");
                for (String member : members) {
                    group.addMember(member);
                }

                // Thêm vào danh sách nhóm
                groups.put(groupName, group);

                // Cập nhật giao diện
                if (chatFrame != null) {
                    chatFrame.addGroup(group);
                }
            }
        }
    }

    private void handleGroupMessage(String message) {
        String content = message.substring(Protocol.SVR_GROUP_MSG.length());
        String[] parts = content.split("\\|", 3);

        if (parts.length == 3) {
            String groupName = parts[0];
            String sender = parts[1];
            String messageContent = parts[2];

            // Hiển thị tin nhắn
            if (chatFrame != null) {
                chatFrame.displayGroupMessage(groupName, sender, messageContent);
            }
        }
    }

    // Xử lý yêu cầu file
    private void handleFileRequest(String message) {
        // Format: /filerequest fileId|sender|fileName|fileSize
        String content = message.substring(Protocol.SVR_FILE_REQUEST.length());
        String[] parts = content.split("\\|", 4);

        if (parts.length == 4) {
            String fileId = parts[0];
            String sender = parts[1];
            String fileName = parts[2];
            long fileSize = Long.parseLong(parts[3]);

            // Nếu là người nhận file
            if (!sender.equals(currentUser.getUsername())) {
                if (chatFrame != null) {
                    chatFrame.handleFileRequest(fileId, sender, fileName, fileSize);
                }
            } else {
                // Nếu là người gửi file (nhận lại thông báo xác nhận đã gửi yêu cầu)
                String receiver = parts[1];
                if (chatFrame != null) {
                    chatFrame.displayFileMessage(receiver, sender, fileName, fileSize,
                            fileId, "Đang chờ xác nhận...");
                }
            }
        }
    }

    // Xử lý file được chấp nhận
    private void handleFileAccepted(String message) {
        // Format: /fileaccepted fileId hoặc /fileaccepted fileId|sender|fileName|fileSize
        String content = message.substring(Protocol.SVR_FILE_ACCEPT.length());
        String[] parts = content.split("\\|");

        String fileId = parts[0];

        if (parts.length == 1) {
            // Người gửi nhận được thông báo người nhận đã chấp nhận
            if (chatFrame != null) {
                File fileToSend = getFileToUpload(fileId);
                if (fileToSend != null) {
                    // Thực hiện upload
                    uploadFile(fileId, fileToSend, "receiver", progress -> {
                        if (chatFrame != null) {
                            chatFrame.updateFileUploadProgress(fileId, progress);
                        }
                    });
                }
            }
        } else if (parts.length == 4) {
            // Người nhận nhận được thông báo file đã sẵn sàng để tải
            String sender = parts[1];
            String fileName = parts[2];
            long fileSize = Long.parseLong(parts[3]);

            SwingUtilities.invokeLater(() -> {
                if (chatFrame != null) {
                    chatFrame.handleFileReady(fileId, sender, fileName, fileSize);
                }
            });
        }
    }

    // Xử lý file bị từ chối
    private void handleFileRejected(String message) {
        String fileId = message.substring(Protocol.SVR_FILE_REJECT.length());

        // Xóa file khỏi danh sách
        filesToUpload.remove(fileId);

        if (chatFrame != null) {
            chatFrame.updateFileStatus(fileId, "Đã bị từ chối");
        }
    }

    private void handleError(String message) {
        String error = message.substring(Protocol.SVR_ERROR.length());

        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(chatFrame, error, "Lỗi", JOptionPane.ERROR_MESSAGE);
        });
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public LocalStorage getLocalStorage() {
        return localStorage;
    }

    public Group getGroup(String groupName) {
        return groups.get(groupName);
    }

    public void setCurrentHistoryContext(String context) {
        this.currentHistoryContext = context;
    }

    public boolean connectToServer(String host, int port) {
        this.serverHost = host;
        this.serverPort = port;
        return connect();
    }

    public void handleConnectionLost() {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(chatFrame,
                    "Mất kết nối đến server. Ứng dụng sẽ đóng.",
                    "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        });
    }

    public void shutdown() {
        if (serverConnection != null) {
            serverConnection.exit();
        }

        if (localStorage != null) {
            localStorage.closeConnection();
        }
    }
}
