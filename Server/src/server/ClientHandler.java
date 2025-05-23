package server;

import db.GroupDAO;
import db.MessageDAO;
import db.UserDAO;
import service.FileService;
import util.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private String id;
    private String username;
    private boolean authenticated = false;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
        this.id = UUID.randomUUID().toString();

        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);
        } catch (IOException e) {
            System.err.println("Lỗi khi tạo ClientHandler: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("Nhận tin nhắn từ client: " + message);
                processMessage(message);
            }
        } catch (IOException e) {
            System.err.println("Lỗi khi đọc tin nhắn từ client: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processMessage(String message) {
        if (message.startsWith(Protocol.CMD_LOGIN)) {
            handleLogin(message);
        } else if (message.startsWith(Protocol.CMD_REGISTER)) {
            handleRegister(message);
        } else if (!authenticated) {
            out.println(Protocol.SVR_ERROR + "Bạn chưa đăng nhập.");
            return;
        } else if (message.startsWith(Protocol.CMD_EXIT)) {
            handleExit();
        } else if (message.startsWith(Protocol.CMD_PRIVATE_MSG)) {
            handlePrivateMessage(message);
        } else if (message.startsWith(Protocol.CMD_GROUP_MSG)) {
            handleGroupMessage(message);
        } else if (message.startsWith(Protocol.CMD_ONLINE_USERS)) {
            handleGetOnlineUsers();
        } else if (message.startsWith(Protocol.CMD_GET_CHAT_HISTORY)) {
            handleGetChatHistory(message);
        }
        else if (message.startsWith(Protocol.CMD_LOAD_MORE_MESSAGES)) {
            handleLoadMoreMessages(message);
        } else if (message.startsWith(Protocol.CMD_CREATE_GROUP)) {
            handleCreateGroup(message);
        } else if (message.startsWith(Protocol.CMD_ADD_TO_GROUP)) {
            handleAddToGroup(message);
        } else if (message.startsWith(Protocol.CMD_GET_GROUPS)) {
            handleGetGroups();
        } else if (message.startsWith(Protocol.CMD_LEAVE_GROUP)) {
            handleLeaveGroup(message);
        } else if (message.startsWith(Protocol.CMD_REMOVE_FROM_GROUP)) {
            handleRemoveFromGroup(message);
        } else if (message.startsWith(Protocol.CMD_FILE_SEND)) {
            handleFileSendRequest(message);
        } else if (message.startsWith(Protocol.CMD_FILE_ACCEPT)) {
            handleFileAccept(message);
        } else if (message.startsWith(Protocol.CMD_FILE_REJECT)) {
            handleFileReject(message);
        }
    }

    private void handleLogin(String message) {
        String[] parts = message.substring(Protocol.CMD_LOGIN.length()).split("\\|");
        if (parts.length != 2) {
            out.println(Protocol.SVR_LOGIN_FAIL + "Định dạng đăng nhập không hợp lệ.");
            return;
        }

        String username = parts[0];
        String password = parts[1];

        if (UserDAO.validateLogin(username, password)) {
            if (ChatServer.isUserOnline(username)) {
                out.println(Protocol.SVR_LOGIN_FAIL + "Tài khoản đang được sử dụng ở nơi khác.");
                return;
            }

            this.username = username;
            this.authenticated = true;
            ChatServer.addOnlineUser(username, id);

            out.println(Protocol.SVR_LOGIN_SUCCESS);

            String joinMessage = Protocol.SVR_USER_JOINED + username;
            ChatServer.broadcastToAllClients(joinMessage);
        } else if (!UserDAO.usernameExists(username)) {
            out.println(Protocol.SVR_LOGIN_FAIL + "Tài khoản không tồn tại.");
        } else {
            out.println(Protocol.SVR_LOGIN_FAIL + "Tên đăng nhập hoặc mật khẩu không đúng.");
        }
    }

    private void handleRegister(String message) {
        String[] parts = message.substring(Protocol.CMD_REGISTER.length()).split("\\|");
        if (parts.length < 2) {
            out.println(Protocol.SVR_REGISTER_FAIL + "Định dạng đăng ký không hợp lệ.");
            return;
        }

        String username = parts[0];
        String password = parts[1];
        String fullName = (parts.length > 2) ? parts[2] : "";
        String email = (parts.length > 3) ? parts[3] : "";

        // Kiểm tra xem tên đăng nhập đã tồn tại chưa
        if (UserDAO.usernameExists(username)) {
            out.println(Protocol.SVR_REGISTER_FAIL + "Tên đăng nhập đã tồn tại.");
            return;
        }

        // Thực hiện đăng ký
        if (UserDAO.registerUser(username, password, fullName, email)) {
            out.println(Protocol.SVR_REGISTER_SUCCESS);
        } else {
            out.println(Protocol.SVR_REGISTER_FAIL + "Đăng ký thất bại. Vui lòng thử lại sau.");
        }
    }

    private void handlePrivateMessage(String message) {
        String content = message.substring(Protocol.CMD_PRIVATE_MSG.length());
        String[] parts = content.split("\\|", 2);

        if (parts.length != 2) {
            out.println(Protocol.SVR_ERROR + "Định dạng tin nhắn riêng không hợp lệ.");
            return;
        }

        String receiver = parts[0];
        String messageContent = parts[1];

        System.out.println("Người nhận: " + receiver);
        System.out.println("Người gửi: " + username);
        System.out.println("Nội dung tin nhắn: " + messageContent);

        if (!UserDAO.usernameExists(receiver)) {
            out.println(Protocol.SVR_ERROR + "Người nhận không tồn tại.");
            return;
        }

        // Lưu tin nhắn vào database
        MessageDAO.saveMessage(username, receiver, messageContent, "private");

        String privateMessage = Protocol.SVR_PRIVATE_MSG + username + Protocol.PARAM_DELIMITER + messageContent;

        ClientHandler receiverHandler = ChatServer.getClientHandler(receiver);
        if (receiverHandler != null) {
            receiverHandler.sendMessage(privateMessage);
        }

        // Gửi bản sao cho người gửi (để hiển thị trong chat)
        out.println(Protocol.SVR_PRIVATE_MSG + receiver + Protocol.PARAM_DELIMITER + messageContent
                + Protocol.PARAM_DELIMITER + "SENT");
    }

    private void handleGroupMessage(String message) {
        String content = message.substring(Protocol.CMD_GROUP_MSG.length());
        String[] parts = content.split("\\|", 2);

        if (parts.length != 2) {
            out.println(Protocol.SVR_ERROR + "Định dạng tin nhắn nhóm không hợp lệ.");
            return;
        }

        String groupName = parts[0].trim();
        String messageContent = parts[1];

        if (!GroupDAO.groupExists(groupName)) {
            out.println(Protocol.SVR_ERROR + "Nhóm không tồn tại");
            return;
        }

        if (!GroupDAO.isGroupMember(groupName, username)) {
            out.println(Protocol.SVR_ERROR + "Bạn không phải là thành viên của nhóm này");
            return;
        }

        MessageDAO.saveMessage(username, groupName, messageContent, "group");

        List<String> members = GroupDAO.getGroupMembers(groupName);

        for (String member : members) {
            ClientHandler memberHandler = ChatServer.getClientHandler(member);
            if (memberHandler != null) {
                memberHandler.sendMessage(Protocol.SVR_GROUP_MSG +
                        groupName + Protocol.PARAM_DELIMITER +
                        username + Protocol.PARAM_DELIMITER +
                        messageContent);
            }
        }
    }

    private void handleGetChatHistory(String message) {
        String content = message.substring(Protocol.CMD_GET_CHAT_HISTORY.length());
        String[] parts = content.split("\\|", 3);

        if (parts.length < 2) {
            out.println(Protocol.SVR_ERROR + "Định dạng lệnh không hợp lệ");
            return;
        }

        String chatPartner = parts[0].trim();
        String chatType = parts[1].trim(); // "private", "group", "global"
        int limit = parts.length > 2 ? Integer.parseInt(parts[2]) : 20; // Mặc định 20 tin nhắn

        List<Map<String, Object>> messages = null;

        if ("private".equals(chatType)) {
            messages = MessageDAO.getPrivateMessages(username, chatPartner, limit);
        } else if ("group".equals(chatType)) {
            messages = MessageDAO.getGroupMessages(chatPartner, limit);
        }

        // Gửi kết quả về client
        out.println(Protocol.SVR_CHAT_HISTORY_START);

        if (messages != null && !messages.isEmpty()) {
            for (Map<String, Object> msg : messages) {
                String sender = (String) msg.get("sender");
                String msgContent = (String) msg.get("content");
                long timestamp = (Long) msg.get("timestamp");

                out.println(Protocol.SVR_CHAT_HISTORY_ITEM +
                        sender + Protocol.PARAM_DELIMITER +
                        msgContent + Protocol.PARAM_DELIMITER +
                        timestamp);
            }
        }

        out.println(Protocol.SVR_CHAT_HISTORY_END);
    }

    private void handleLoadMoreMessages(String message) {
        String content = message.substring(Protocol.CMD_LOAD_MORE_MESSAGES.length());
        String[] parts = content.split("\\|", 4);

        if (parts.length < 3) {
            out.println(Protocol.SVR_ERROR + "Định dạng lệnh tải thêm tin nhắn không hợp lệ");
            return;
        }

        String chatPartner = parts[0].trim();
        String chatType = parts[1].trim();
        long olderThan = Long.parseLong(parts[2].trim());
        int limit = parts.length > 3 ? Integer.parseInt(parts[3].trim()) : 10; // Mặc định 10 tin nhắn

        List<Map<String, Object>> messages = null;

        if ("private".equals(chatType)) {
            messages = MessageDAO.getOlderPrivateMessages(username, chatPartner, olderThan, limit);
        } else if ("group".equals(chatType)) {
            messages = MessageDAO.getOlderGroupMessages(chatPartner, olderThan, limit);
        }

        // Gửi kết quả về client
        out.println(Protocol.SVR_LOAD_MORE_START);

        if (messages != null && !messages.isEmpty()) {
            for (Map<String, Object> msg : messages) {
                String sender = (String) msg.get("sender");
                String msgContent = (String) msg.get("content");
                long timestamp = (Long) msg.get("timestamp");

                out.println(Protocol.SVR_LOAD_MORE_ITEM +
                        sender + Protocol.PARAM_DELIMITER +
                        msgContent + Protocol.PARAM_DELIMITER +
                        timestamp);
            }
        }

        out.println(Protocol.SVR_LOAD_MORE_END);
    }

    private void handleGetOnlineUsers() {
        List<String> users = ChatServer.getOnlineUsers();
        StringBuilder sb = new StringBuilder(Protocol.SVR_ONLINE_USERS);

        for (String user : users) {
            sb.append(user).append(",");
        }

        if (sb.length() > Protocol.SVR_ONLINE_USERS.length()) {
            sb.deleteCharAt(sb.length() - 1);
        }

        out.println(sb.toString());
    }

    private void handleCreateGroup(String message) {
        String groupName = message.substring(Protocol.CMD_CREATE_GROUP.length()).trim();

        if (groupName.isEmpty()) {
            out.println(Protocol.SVR_ERROR + "Tên nhóm không được để trống");
            return;
        }

        if (GroupDAO.groupExists(groupName)) {
            out.println(Protocol.SVR_ERROR + "Nhóm này đã tồn tại");
            return;
        }

        boolean success = GroupDAO.createGroup(groupName, username);

        if (success) {
            out.println(Protocol.SVR_CREATE_GROUP_SUCCESS + groupName);

            ChatServer.broadcastMessage(Protocol.SVR_NEW_GROUP + groupName +
                    Protocol.PARAM_DELIMITER + username);
        } else {
            out.println(Protocol.SVR_ERROR + "Không thể tạo nhóm");
        }
    }

    private void handleAddToGroup(String message) {
        String content = message.substring(Protocol.CMD_ADD_TO_GROUP.length());
        String[] parts = content.split("\\|", 2);

        if (parts.length != 2) {
            out.println(Protocol.SVR_ERROR + "Định dạng lệnh không hợp lệ");
            return;
        }

        String groupName = parts[0].trim();
        String targetUser = parts[1].trim();

        if (!GroupDAO.groupExists(groupName)) {
            out.println(Protocol.SVR_ERROR + "Nhóm không tồn tại");
            return;
        }

        if (!GroupDAO.isGroupMember(groupName, username)) {
            out.println(Protocol.SVR_ERROR + "Bạn không phải là thành viên của nhóm này");
            return;
        }

        if (!UserDAO.usernameExists(targetUser)) {
            out.println(Protocol.SVR_ERROR + "Người dùng không tồn tại");
            return;
        }

        if (GroupDAO.isGroupMember(groupName, targetUser)) {
            out.println(Protocol.SVR_ERROR + targetUser + " đã là thành viên của nhóm");
            return;
        }

        boolean success = GroupDAO.addMemberToGroup(groupName, targetUser);

        if (success) {
            out.println(Protocol.SVR_ADD_TO_GROUP_SUCCESS + groupName +
                    Protocol.PARAM_DELIMITER + targetUser);

            List<String> members = GroupDAO.getGroupMembers(groupName);
            for (String member : members) {
                ClientHandler memberHandler = ChatServer.getClientHandler(member);
                if (memberHandler != null && !member.equals(username)) {
                    memberHandler.sendMessage(Protocol.SVR_GROUP_USER_ADDED +
                            groupName + Protocol.PARAM_DELIMITER +
                            targetUser + Protocol.PARAM_DELIMITER +
                            username);
                }
            }

            ClientHandler targetHandler = ChatServer.getClientHandler(targetUser);
            if (targetHandler != null) {
                targetHandler.sendMessage(Protocol.SVR_ADDED_TO_GROUP +
                        groupName + Protocol.PARAM_DELIMITER +
                        username);
            }
        } else {
            out.println(Protocol.SVR_ERROR + "Không thể thêm người dùng vào nhóm");
        }
    }

    private void handleLeaveGroup(String message) {
        String groupName = message.substring(Protocol.CMD_LEAVE_GROUP.length()).trim();

        // Kiểm tra xem nhóm có tồn tại không
        if (!GroupDAO.groupExists(groupName)) {
            out.println(Protocol.SVR_ERROR + "Nhóm không tồn tại");
            return;
        }

        // Kiểm tra xem người dùng có phải là thành viên của nhóm không
        if (!GroupDAO.isGroupMember(groupName, username)) {
            out.println(Protocol.SVR_ERROR + "Bạn không phải là thành viên của nhóm này");
            return;
        }

        // Xóa người dùng khỏi nhóm
        boolean success = GroupDAO.removeMemberFromGroup(groupName, username);

        if (success) {
            // Thông báo cho người rời nhóm
            out.println(Protocol.SVR_LEFT_GROUP + groupName);

            // Thông báo cho tất cả thành viên còn lại trong nhóm
            List<String> members = GroupDAO.getGroupMembers(groupName);
            for (String member : members) {
                ClientHandler memberHandler = ChatServer.getClientHandler(member);
                if (memberHandler != null) {
                    memberHandler.sendMessage(Protocol.SVR_GROUP_USER_LEFT +
                            groupName + Protocol.PARAM_DELIMITER +
                            username);
                }
            }

            if (members.isEmpty()) {
                GroupDAO.deleteGroup(groupName);
            }
        } else {
            out.println(Protocol.SVR_ERROR + "Không thể rời khỏi nhóm");
        }
    }

    private void handleGetGroups() {
        // Lấy danh sách nhóm mà người dùng tham gia
        List<Map<String, Object>> userGroups = GroupDAO.getUserGroups(username);

        if (userGroups.isEmpty()) {
            out.println(Protocol.SVR_GROUP_LIST);
            return;
        }

        StringBuilder groupList = new StringBuilder();

        for (Map<String, Object> group : userGroups) {
            String groupName = (String) group.get("name");
            String creator = (String) group.get("creator");

            // Lấy danh sách thành viên
            List<String> members = GroupDAO.getGroupMembers(groupName);

            groupList.append(groupName).append(":")
                    .append(creator).append(":");

            // Thêm danh sách thành viên
            for (int i = 0; i < members.size(); i++) {
                groupList.append(members.get(i));
                if (i < members.size() - 1) {
                    groupList.append(",");
                }
            }

            groupList.append("|");
        }

        // Xóa dấu | cuối cùng nếu có
        if (!groupList.isEmpty() && groupList.charAt(groupList.length() - 1) == '|') {
            groupList.deleteCharAt(groupList.length() - 1);
        }

        out.println(Protocol.SVR_GROUP_LIST + groupList.toString());
    }

    private void handleRemoveFromGroup(String message) {
        String content = message.substring(Protocol.CMD_REMOVE_FROM_GROUP.length());
        String[] parts = content.split("\\|", 2);

        if (parts.length != 2) {
            out.println(Protocol.SVR_ERROR + "Định dạng lệnh không hợp lệ");
            return;
        }

        String groupName = parts[0].trim();
        String targetUser = parts[1].trim();

        // Kiểm tra xem nhóm có tồn tại không
        if (!GroupDAO.groupExists(groupName)) {
            out.println(Protocol.SVR_ERROR + "Nhóm không tồn tại");
            return;
        }

        // Lấy thông tin nhóm
        Map<String, Object> groupInfo = GroupDAO.getGroupInfo(groupName);
        if (groupInfo == null) {
            out.println(Protocol.SVR_ERROR + "Không thể lấy thông tin nhóm");
            return;
        }

        // Kiểm tra xem người dùng có phải là người tạo nhóm không
        String creator = (String) groupInfo.get("creator");
        if (!username.equals(creator)) {
            out.println(Protocol.SVR_ERROR + "Bạn không có quyền xóa thành viên khỏi nhóm");
            return;
        }

        // Kiểm tra xem người bị xóa có phải là thành viên của nhóm không
        if (!GroupDAO.isGroupMember(groupName, targetUser)) {
            out.println(Protocol.SVR_ERROR + targetUser + " không phải là thành viên của nhóm");
            return;
        }

        // Xóa người dùng khỏi nhóm
        boolean success = GroupDAO.removeMemberFromGroup(groupName, targetUser);

        if (success) {
            // Thông báo cho người xóa
            out.println(Protocol.SVR_REMOVE_FROM_GROUP_SUCCESS +
                    groupName + Protocol.PARAM_DELIMITER + targetUser);

            // Thông báo cho người bị xóa
            ClientHandler targetHandler = ChatServer.getClientHandler(targetUser);
            if (targetHandler != null) {
                targetHandler.sendMessage(Protocol.SVR_REMOVED_FROM_GROUP +
                        groupName + Protocol.PARAM_DELIMITER + username);
            }

            // Thông báo cho tất cả thành viên còn lại trong nhóm
            List<String> members = GroupDAO.getGroupMembers(groupName);
            for (String member : members) {
                ClientHandler memberHandler = ChatServer.getClientHandler(member);
                if (memberHandler != null && !member.equals(username)) {
                    memberHandler.sendMessage(Protocol.SVR_GROUP_USER_LEFT +
                            groupName + Protocol.PARAM_DELIMITER +
                            targetUser + Protocol.PARAM_DELIMITER +
                            "removed");
                }
            }
        } else {
            out.println(Protocol.SVR_ERROR + "Không thể xóa người dùng khỏi nhóm");
        }
    }

    private void handleFileSendRequest(String message) {
        String content = message.substring(Protocol.CMD_FILE_SEND.length());
        String[] parts = content.split("\\|", 4);

        if (parts.length != 4) {
            out.println(Protocol.SVR_ERROR + "Định dạng yêu cầu file không hợp lệ.");
            return;
        }

        String receiver = parts[0].trim();
        String fileName = parts[1];
        long fileSize;

        try {
            fileSize = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            out.println(Protocol.SVR_ERROR + "Kích thước file không hợp lệ.");
            return;
        }

        String fileId = parts[3].trim();

        if (!UserDAO.usernameExists(receiver)) {
            out.println(Protocol.SVR_ERROR + "Người dùng không tồn tại.");
            return;
        }

        String _fileId = FileService.createFileTransferRequest(username, receiver, fileName, fileSize, fileId);
        if (_fileId == null) {
            out.println(Protocol.SVR_ERROR + "Không thể tạo yêu cầu chuyển file.");
            return;
        }

        ClientHandler receiverHandler = ChatServer.getClientHandler(receiver);
        if (receiverHandler != null) {
            // Thông báo cho người nhận file
            receiverHandler.sendMessage(Protocol.SVR_FILE_REQUEST + fileId +
                    Protocol.PARAM_DELIMITER + username +
                    Protocol.PARAM_DELIMITER + fileName +
                    Protocol.PARAM_DELIMITER + fileSize);

            // Thông báo cho người gửi biết đã gửi yêu cầu
            out.println(Protocol.SVR_FILE_REQUEST + fileId +
                    Protocol.PARAM_DELIMITER + receiver +
                    Protocol.PARAM_DELIMITER + fileName +
                    Protocol.PARAM_DELIMITER + fileSize);
        } else {
            out.println(Protocol.SVR_ERROR + "Người nhận không online.");
        }
    }

    private void handleFileAccept(String message) {
        String fileId = message.substring(Protocol.CMD_FILE_ACCEPT.length()).trim();

        System.out.println("Client accepts file: fileId=" + fileId + ", username=" + username);

        FileService.acceptFileTransfer(fileId, username);
    }

    private void handleFileReject(String message) {
        String fileId = message.substring(Protocol.CMD_FILE_REJECT.length()).trim();

        // Xử lý từ chối file
        FileService.rejectFileTransfer(fileId, username);
    }

    private void handleExit() {
        closeConnection();
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    public String getId() {
        return id;
    }

    public void closeConnection() {
        try {
            if (authenticated && username != null) {
                ChatServer.removeOnlineUser(username);

                String leaveMessage = Protocol.SVR_USER_LEFT + username;
                ChatServer.broadcastToAllClients(leaveMessage);
            }

            ChatServer instance = ChatServer.getInstance();
            if (instance != null) {
                instance.removeClientHandler(this.id);
            }

            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }

            System.out.println("Client đã ngắt kết nối: " + (username != null ? username : Objects.requireNonNull(clientSocket).getInetAddress()));
        }  catch (IOException e) {
            System.err.println("Lỗi khi đóng kết nối: " + e.getMessage());
            e.printStackTrace();
        }
    }
}