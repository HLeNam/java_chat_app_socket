package client;

import client.network.FileTransferClient;
import client.network.ServerConnection;
import db.LocalStorage;
import model.Group;
import model.User;
import ui.ChatFrame;
import ui.LoginFrame;
import util.FileUtil;
import util.Protocol;

import javax.imageio.IIOException;
import javax.swing.*;
import java.io.File;
import java.util.*;
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

    public static String defaultDownloadFolder;
    public static String defaultUploadFolder;

    private Map<String, Object[]> pendingDownloads = new HashMap<>();

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
        String[] parts = content.split("\\|", 8);

        if (parts.length >= 4) {
            pendingHistoryMessages.add(parts);

            long timestamp;
            if (parts.length == 4) {
                timestamp = Long.parseLong(parts[2]);
            } else {
                timestamp = Long.parseLong(parts[4]);
            }
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
    public String sendFileRequest(String receiver, File file) {
        if (file == null || !file.exists()) {
            System.out.println("ERROR: File không tồn tại");
            return null;
        }

        String fileName = file.getName();
        long fileSize = file.length();

        // Generate unique fileId (hoặc bạn có thể sử dụng UUID.randomUUID().toString())
        String fileId = System.currentTimeMillis() + "_" + fileName.hashCode();

        // QUAN TRỌNG: Lưu file vào map TRƯỚC khi gửi request
        addFileToUpload(fileId, file);

        // Gửi yêu cầu chuyển file
        serverConnection.sendFileRequest(receiver, fileName, fileSize, fileId);

        // Hiển thị thông báo trong chat khi gửi yêu cầu
        displayFileMessage(receiver, getCurrentUser().getUsername(), fileName, fileSize, fileId, "Đang gửi file...");

        System.out.println("Sent file request: fileId=" + fileId + ", to=" + receiver + ", filename=" + fileName);

        return fileId;
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
        System.out.println("Added file to upload: fileId=" + fileId + ", filename=" + file.getName() + ", size=" + file.length());
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

    public void uploadFile(String fileId, File file, String receiver, String sender,
                           Consumer<Integer> progressCallback) {
        fileTransferClient.uploadFile(fileId, file, receiver, sender, progressCallback);
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
        } else if (message.startsWith(Protocol.SVR_FILE_DOWNLOAD)) {
            handleFileDownload(message);
        } else if (message.startsWith(Protocol.SVR_GROUP_FILE_REQUEST)) {
            handleGroupFileRequest(message);
        } else if (message.startsWith(Protocol.SVR_GROUP_FILE_ACCEPT)) {
            handleGroupFileAccepted(message);
        } else if (message.startsWith(Protocol.SVR_FILE_DOWNLOAD_REQUEST)) {
            handleDownloadFileRequest(message);
        } else if (message.startsWith(Protocol.SVR_FILE_DOWNLOAD_ACCEPT)) {
            handleDownloadFileAccepted(message);
        }
    }

    private void handleLoginSuccess() {
        // Tạo thư mục Downloads trong thư mục của người dùng
        defaultDownloadFolder = System.getProperty("user.home") + File.separator + "ChatApp"
                + File.separator + currentUser.getUsername() + File.separator + "Downloads";
        File downloadDir = new File(defaultDownloadFolder);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }

        // Tạo thư mục Uploads trong thư mục của người dùng
        defaultUploadFolder = System.getProperty("user.home") + File.separator + "ChatApp"
                + File.separator + currentUser.getUsername() + File.separator + "Uploads";
        File uploadDir = new File(defaultUploadFolder);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

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
        String[] parts = content.split("\\|", 4);

        if (parts.length >= 3) {
            String contactName = parts[0];  // sender hoặc receiver
            String messageContent = parts[1];
            long timestamp = Long.parseLong(parts[2]);
            boolean isSent = parts.length >= 4 && parts[3].equals("SENT");

            // Xác định người gửi tin nhắn
            String sender;
            String chatContext;
            String receiver;

            if (isSent) {
                sender = currentUser.getUsername();
                receiver = contactName;
            } else {
                sender = contactName;
                receiver = currentUser.getUsername();
            }
            chatContext = contactName;

            if (localStorage != null) {
                localStorage.saveTextMessage(sender, receiver, messageContent, false, timestamp);
            }

            if (chatFrame != null) {
                chatFrame.displayMessage(chatContext, sender, messageContent, timestamp);
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
        String[] parts = content.split("\\|", 4);

        if (parts.length == 4) {
            String groupName = parts[0];
            String sender = parts[1];
            String messageContent = parts[2];
            long timestamp = Long.parseLong(parts[3]);

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
        String[] parts = content.split("\\|", 5);

        if (parts.length == 5) {
            String fileId = parts[0];
            String sender = parts[1];
            String fileName = parts[2];
            long fileSize = Long.parseLong(parts[3]);
            long timestamp = Long.parseLong(parts[4]);

            System.out.println("Received file request: fileId=" + fileId + ", sender=" + sender + ", filename=" + fileName);

            if (sender.equals(currentUser.getUsername())) {
                // Người gửi file - chỉ hiển thị MỘT thông báo
//                if (chatFrame != null) {
//                    // Đảm bảo là chỉ hiển thị một dòng thông báo
//                    chatFrame.displayFileMessage(sender, sender, fileName, fileSize, fileId, "Đang chờ gửi...");
//                }
            } else {
                // Người nhận file
                serverConnection.sendMessage(Protocol.CMD_FILE_ACCEPT + fileId);

                // Tạo đường dẫn file để lưu
                String saveFilePath = defaultDownloadFolder + File.separator + fileName;

                // Kiểm tra nếu file đã tồn tại, thêm số vào tên file
                File saveFile = new File(saveFilePath);
                int count = 1;
                String baseFileName = fileName;
                String extension = "";

                // Tách tên file và phần mở rộng
                int lastDotPos = fileName.lastIndexOf(".");
                if (lastDotPos > 0) {
                    baseFileName = fileName.substring(0, lastDotPos);
                    extension = fileName.substring(lastDotPos);
                }

                // Nếu file đã tồn tại, tạo tên mới
                while (saveFile.exists()) {
                    saveFilePath = defaultDownloadFolder + File.separator + baseFileName + "(" + count + ")" + extension;
                    saveFile = new File(saveFilePath);
                    count++;
                }

                // Thêm file vào danh sách chờ download
                addFileToDownload(fileId, sender, fileName, fileSize, saveFilePath);

                // Hiển thị thông báo nhận file
                if (chatFrame != null) {
                    chatFrame.displayFileMessage(sender, sender, fileName, fileSize, fileId, "Đang chờ nhận file...");
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
        System.out.println("File accepted: fileId=" + fileId + ", parts.length=" + parts.length);

        // Liệt kê tất cả các file đang chờ upload để debug
        System.out.println("Files waiting to upload:");
        for (Map.Entry<String, File> entry : filesToUpload.entrySet()) {
            System.out.println("  - " + entry.getKey() + ": " + entry.getValue().getName());
        }

        if (parts.length == 1) {
            // Người gửi nhận được thông báo người nhận đã chấp nhận
            File fileToSend = getFileToUpload(fileId);
            if (fileToSend != null) {
                System.out.println("Starting upload file: " + fileToSend.getName());

                // Cập nhật trạng thái trước khi bắt đầu upload
                if (chatFrame != null) {
                    SwingUtilities.invokeLater(() -> {
                        chatFrame.updateFileStatus(fileId, "Đang gửi file...");
                    });
                }

                // Thực hiện upload
                uploadFile(fileId, fileToSend, "receiver", progress -> {
                    if (chatFrame != null) {
                        if (progress < 100) {
                            SwingUtilities.invokeLater(() -> {
                                chatFrame.updateFileStatus(fileId, "Đang gửi: " + progress + "%");
                                chatFrame.updateUploadProgressBar(fileId, progress);
                            });
                        } else {
                            SwingUtilities.invokeLater(() -> {
                                chatFrame.updateFileStatus(fileId, "Đã gửi thành công");
                                chatFrame.updateUploadProgressBar(fileId, 100);
                            });
                        }
                    }
                });
            } else {
                System.out.println("ERROR: File to upload not found for fileId: " + fileId);
            }
        } else if (parts.length == 4) {
            // Người nhận nhận được thông báo file đã sẵn sàng để tải
            String sender = parts[1];
            String fileName = parts[2];
            long fileSize = Long.parseLong(parts[3]);

            System.out.println("File ready to download from " + sender + ": " + fileName);

            // Lấy đường dẫn đã lưu trước đó
            String[] fileInfo = getFileToDownload(fileId);
            if (fileInfo != null) {
                String savePath = fileInfo[3];
                String actualFileName = fileInfo[1];

                // Tự động bắt đầu download
                downloadFile(fileId, sender, fileName, fileSize, savePath, progress -> {
                    SwingUtilities.invokeLater(() -> {
                        if (chatFrame != null) {
                            if (progress < 100) {
                                chatFrame.updateFileStatus(fileId, "Đang tải: " + progress + "%");
                            } else {
                                chatFrame.updateFileComponent(fileId, savePath);
                                chatFrame.updateFileStatus(fileId, "Đã tải xong");
                                // Thông báo cho người dùng
                                chatFrame.showNotification("Đã tải xong file " + fileName + " từ " + sender);
                                sendMessage(Protocol.CMD_CHANGE_MESSAGE_ACTUAL_FILENAME_SAVE +
                                        fileId + Protocol.PARAM_DELIMITER +
                                        actualFileName);
                            }
                        }
                    });
                });
            } else {
                System.out.println("ERROR: Download info not found for fileId: " + fileId);
            }
        }
    }

    private void handleGroupFileAccepted(String message) {
        String content = message.substring(Protocol.SVR_GROUP_FILE_ACCEPT.length());
        String[] parts = content.split("\\|");

        String fileId = parts[0];
        System.out.println("File accepted: fileId=" + fileId + ", parts.length=" + parts.length);

        // Liệt kê tất cả các file đang chờ upload để debug
        System.out.println("Files waiting to upload:");
        for (Map.Entry<String, File> entry : filesToUpload.entrySet()) {
            System.out.println("  - " + entry.getKey() + ": " + entry.getValue().getName());
        }

        if (parts.length == 1) {
            // Người gửi nhận được thông báo người nhận đã chấp nhận
            File fileToSend = getFileToUpload(fileId);
            if (fileToSend != null) {
                System.out.println("Starting upload file: " + fileToSend.getName());

                // Cập nhật trạng thái trước khi bắt đầu upload
                if (chatFrame != null) {
                    SwingUtilities.invokeLater(() -> {
                        chatFrame.updateFileStatus(fileId, "Đang gửi file...");
                    });
                }

                // Thực hiện upload
                uploadFile(fileId, fileToSend, "receiver", getCurrentUser().getUsername(), progress -> {
                    if (chatFrame != null) {
                        if (progress < 100) {
                            SwingUtilities.invokeLater(() -> {
                                chatFrame.updateFileStatus(fileId, "Đang gửi: " + progress + "%");
                                chatFrame.updateUploadProgressBar(fileId, progress);
                            });
                        } else {
                            SwingUtilities.invokeLater(() -> {
                                chatFrame.updateFileStatus(fileId, "Đã gửi thành công");
                                chatFrame.updateUploadProgressBar(fileId, 100);
                            });
                        }
                    }
                });
            } else {
                System.out.println("ERROR: File to upload not found for fileId: " + fileId);
            }
        }
        else if (parts.length == 4) {
            // Người nhận nhận được thông báo file đã sẵn sàng để tải
            String sender = parts[1];
            String fileName = parts[2];
            long fileSize = Long.parseLong(parts[3]);

            System.out.println("File ready to download from " + sender + ": " + fileName);

            // Lấy đường dẫn đã lưu trước đó
            String[] fileInfo = getFileToDownload(fileId);
            if (fileInfo != null) {
                String savePath = fileInfo[3];
                String actualFileName = fileInfo[1];

                // Tự động bắt đầu download
                downloadFile(fileId, sender, fileName, fileSize, savePath, progress -> {
                    SwingUtilities.invokeLater(() -> {
                        if (chatFrame != null) {
                            if (progress < 100) {
                                chatFrame.updateFileStatus(fileId, "Đang tải: " + progress + "%");
                            } else {
                                chatFrame.updateFileComponent(fileId, savePath);
                                chatFrame.updateFileStatus(fileId, "Đã tải xong");
                                // Thông báo cho người dùng
                                chatFrame.showNotification("Đã tải xong file " + fileName + " từ " + sender);
                                sendMessage(Protocol.CMD_CHANGE_MESSAGE_GROUP_ACTUAL_FILENAME_SAVE +
                                        fileId + Protocol.PARAM_DELIMITER +
                                        actualFileName + Protocol.PARAM_DELIMITER +
                                        getCurrentUser().getUsername());
                            }
                        }
                    });
                });
            } else {
                System.out.println("ERROR: Download info not found for fileId: " + fileId);
            }
        }
    }

    private void handleDownloadFileRequest(String message) {
        String content = message.substring(Protocol.SVR_FILE_DOWNLOAD_REQUEST.length());
        String[] parts = content.split("\\|", 4);

        if (parts.length == 4) {
            String fileId = parts[0];
            String sender = parts[1];
            String fileName = parts[2];
            long fileSize = Long.parseLong(parts[3]);

            // Người nhận file
            serverConnection.sendMessage(Protocol.CMD_FILE_DOWNLOAD_ACCEPT + fileId +
                    Protocol.PARAM_DELIMITER + getCurrentUser().getUsername());

            // Tạo đường dẫn file để lưu
            String saveFilePath = defaultDownloadFolder + File.separator + fileName;

            // Kiểm tra nếu file đã tồn tại, thêm số vào tên file
            File saveFile = new File(saveFilePath);
            int count = 1;
            String baseFileName = fileName;
            String extension = "";

            // Tách tên file và phần mở rộng
            int lastDotPos = fileName.lastIndexOf(".");
            if (lastDotPos > 0) {
                baseFileName = fileName.substring(0, lastDotPos);
                extension = fileName.substring(lastDotPos);
            }

            // Nếu file đã tồn tại, tạo tên mới
            while (saveFile.exists()) {
                saveFilePath = defaultDownloadFolder + File.separator + baseFileName + "(" + count + ")" + extension;
                saveFile = new File(saveFilePath);
                count++;
            }

            // Thêm file vào danh sách chờ download
            addFileToDownload(fileId, sender, fileName, fileSize, saveFilePath);

            // Hiển thị thông báo nhận file
            if (chatFrame != null) {
                chatFrame.updateFileStatus(fileId, "Đang chờ nhận file từ " + sender);
            }
        } else {
            System.out.println("ERROR: Invalid file download request format: " + message);
        }
    }

    private void handleDownloadFileAccepted(String message) {
        String content = message.substring(Protocol.SVR_FILE_DOWNLOAD_ACCEPT.length());
        String[] parts = content.split("\\|", 4);

        if (parts.length == 4) {
            String fileId = parts[0];
            String sender = parts[1];
            String fileName = parts[2];

            // Lấy thông tin file từ danh sách chờ download
            String[] fileInfo = getFileToDownload(fileId);
            if (fileInfo != null) {
                String actualFileName = fileInfo[1];
                long fileSize = Long.parseLong(fileInfo[2]);
                String savePath = fileInfo[3];

                // Tự động bắt đầu download
                downloadFile(fileId, sender, fileName, fileSize, savePath, progress -> {
                    SwingUtilities.invokeLater(() -> {
                        if (chatFrame != null) {
                            if (progress < 100) {
                                chatFrame.updateFileStatus(fileId, "Đang tải: " + progress + "%");
                            } else {
                                chatFrame.updateFileComponent(fileId, savePath);
                                chatFrame.updateFileStatus(fileId, "Đã tải xong");
                                // Thông báo cho người dùng
                                chatFrame.showNotification("Đã tải xong file " + fileName + " từ " + sender);

                                sendMessage(Protocol.CMD_CHANGE_MESSAGE_GROUP_ACTUAL_FILENAME_SAVE +
                                        fileId + Protocol.PARAM_DELIMITER +
                                        actualFileName + Protocol.PARAM_DELIMITER +
                                        getCurrentUser().getUsername());
                            }
                        }
                    });
                });
            } else {
                System.out.println("ERROR: Download info not found for fileId: " + fileId);
            }
        } else {
            System.out.println("ERROR: Invalid file download accept format: " + message);
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

    public void downloadFileFromServer(String fileId, String sender, String fileName, long fileSize) {
        // Gửi yêu cầu tải file từ server storage
        // Protocol: CMD_FILE_DOWNLOAD fileId
        serverConnection.sendMessage(Protocol.CMD_FILE_DOWNLOAD + fileId +
                Protocol.PARAM_DELIMITER + sender +
                Protocol.PARAM_DELIMITER + getCurrentUser().getUsername() +
                Protocol.PARAM_DELIMITER + fileName +
                Protocol.PARAM_DELIMITER + fileSize);
    }

    private void handleFileDownload(String message) {
        try {
            // Format: SVR_FILE_DOWNLOAD fileId|fileName|fileSize
            String[] parts = message.substring(Protocol.SVR_FILE_DOWNLOAD.length()).split("\\|", 3);

            if (parts.length >= 3) {
                String fileId = parts[0];
                String fileName = parts[1];
                long fileSize = Long.parseLong(parts[2]);

                Object[] downloadInfo = pendingDownloads.get(fileId);
                if (downloadInfo != null) {
                    String savePath = (String) downloadInfo[0];
                    Consumer<Integer> progressCallback = (Consumer<Integer>) downloadInfo[1];

                    // Tải file
                    downloadFile(fileId, getCurrentUser().getUsername(), fileName, fileSize, savePath, progressCallback);
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi xử lý yêu cầu tải file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleGroupFileRequest(String message) {
        // Format: /groupfilerequest groupName|fileName|fileSize|fileId
        String content = message.substring(Protocol.SVR_GROUP_FILE_REQUEST.length());
        String[] parts = content.split("\\|", 8);

        if (parts.length >= 7) {
            String groupName = parts[0];
            String sender = parts[1];
            String fileName = parts[2];
            long fileSize = Long.parseLong(parts[3]);
            String fileId = parts[4];
            String receiver = parts[5]; // Người nhận file
            long timestamp = Long.parseLong(parts[6]);

            // Nếu người gửi là chính mình, không cần xử lý
            if (sender.equals(currentUser.getUsername())) {
                boolean hasOnlineReceiver = Boolean.parseBoolean(parts[7]);
                if (!hasOnlineReceiver) {
                    // Vẫn upload file lên server
                    File fileToUpload = getFileToUpload(fileId);
                    if (fileToUpload != null) {
                        System.out.println("Starting upload file: " + fileToUpload.getName());

                        // Cập nhật trạng thái trước khi bắt đầu upload
                        if (chatFrame != null) {
                            SwingUtilities.invokeLater(() -> {
                                chatFrame.updateFileStatus(fileId, "Đang gửi file...");
                            });
                        }

                        // Thực hiện upload
                        uploadFile(fileId, fileToUpload, receiver, currentUser.getUsername(), progress -> {
                            if (chatFrame != null) {
                                if (progress < 100) {
                                    SwingUtilities.invokeLater(() -> {
                                        chatFrame.updateFileStatus(fileId, "Đang gửi: " + progress + "%");
                                        chatFrame.updateUploadProgressBar(fileId, progress);
                                    });
                                } else {
                                    SwingUtilities.invokeLater(() -> {
                                        chatFrame.updateFileStatus(fileId, "Đã gửi thành công");
                                        chatFrame.updateUploadProgressBar(fileId, 100);
                                    });
                                }
                            }
                        });
                    } else {
                        System.out.println("ERROR: File to upload not found for fileId: " + fileId);
                    }
                }
            }
            else {
                // Người nhận file trong nhóm
                serverConnection.sendMessage(Protocol.CMD_GROUP_FILE_ACCEPT + groupName +
                        Protocol.PARAM_DELIMITER + fileId);

                // Tạo đường dẫn lưu file
                String saveFilePath = defaultDownloadFolder + File.separator + fileName;

                // Kiểm tra nếu file đã tồn tại, thêm số vào tên file
                File saveFile = new File(saveFilePath);
                int count = 1;
                String baseFileName = fileName;
                String extension = "";

                // Tách tên file và phần mở rộng
                int lastDotPos = fileName.lastIndexOf(".");
                if (lastDotPos > 0) {
                    baseFileName = fileName.substring(0, lastDotPos);
                    extension = fileName.substring(lastDotPos);
                }

                // Nếu file đã tồn tại, tạo tên mới
                while (saveFile.exists()) {
                    saveFilePath = defaultDownloadFolder + File.separator + baseFileName + "(" + count + ")" + extension;
                    saveFile = new File(saveFilePath);
                    count++;
                }

                // Thêm vào danh sách chờ download
                addFileToDownload(fileId, sender, fileName, fileSize, saveFilePath);

                // Hiển thị thông báo trong nhóm
                if (chatFrame != null) {
                    chatFrame.displayFileMessageInGroup(groupName, sender, fileName, fileSize, fileId,
                            "Đang chờ nhận file...", saveFilePath, timestamp);
                }
            }
        }
    }

    public String sendGroupFileRequest(String groupName, File file) {
        String fileId = UUID.randomUUID().toString();
        addFileToUpload(fileId, file);

        // Gửi yêu cầu file đến tất cả thành viên trong nhóm
        serverConnection.sendGroupFileRequest(groupName, file.getName(), file.length(), fileId);

        // Hiển thị thông tin file trong chat
        chatFrame.displayFileMessageInGroup(groupName, currentUser.getUsername(),
                file.getName(), file.length(), fileId,
                "Đang gửi cho nhóm...", null, System.currentTimeMillis());

        // Lưu vào local storage
        if (localStorage != null) {
            localStorage.saveFileMessage(currentUser.getUsername(), groupName, file.getName(),
                    file.length(), fileId, null, System.currentTimeMillis(), true);
        }

        return fileId;
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

    public void updateFileMessageComponentPath(String fileId, String filePath) {
        if (chatFrame != null) {
            chatFrame.updateFilePath(fileId, filePath);
        }
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
