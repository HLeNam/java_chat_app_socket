package service;

import db.FileDAO;
import db.GroupDAO;
import model.FileInfo;
import server.ChatServer;
import server.ClientHandler;
import util.Protocol;

import javax.xml.crypto.Data;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileService {
    private static final String FILE_STORAGE_DIR = "./file_storage/"; // Thư mục lưu file
    private static ServerSocket fileServerSocket; // Socket cho file server
    private static ExecutorService fileTransferPool = Executors.newFixedThreadPool(10);

    // Lưu trữ thông tin về các file transfer đang diễn ra
    private static Map<String, FileTransferInfo> activeTransfers = new ConcurrentHashMap<>();

    // Class để lưu thông tin về một phiên chuyển file
    public static class FileTransferInfo {
        String fileId;
        String sender;
        String receiver;
        String fileName;
        long fileSize;
        String groupReceiver; // Thêm trường cho nhóm nếu cần

        public FileTransferInfo(String fileId, String sender, String receiver, String fileName, long fileSize) {
            this.fileId = fileId;
            this.sender = sender;
            this.receiver = receiver;
            this.fileName = fileName;
            this.fileSize = fileSize;
        }

        public void setGroupReceiver(String groupReceiver) {
            this.groupReceiver = groupReceiver;
        }

        public String getFileId() {
            return fileId;
        }

        public String getSender() {
            return sender;
        }

        public String getFileName() {
            return fileName;
        }

        public long getFileSize() {
            return fileSize;
        }
    }

    public static void startFileServer(int port) {
        try {
            Files.createDirectories(Paths.get(FILE_STORAGE_DIR)); // Tạo thư mục lưu file nếu chưa tồn tại
        } catch (IOException e) {
            System.err.println("Không thể tạo thư mục lưu trữ file: " + e.getMessage());
            return;
        }

        new Thread(() -> {
            try {
                fileServerSocket = new ServerSocket(port);
                System.out.println("File server đã khởi động ở port " + port);

                while (true) {
                    Socket fileSocket = fileServerSocket.accept();
                    fileTransferPool.execute(() -> handleFileTransfer(fileSocket));
                }
            } catch (IOException e) {
                System.err.println("Lỗi khởi động file server: " + e.getMessage());
            }
        }).start();
    }

    private static void handleFileTransfer(Socket socket) {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            // Đọc thông tin file
            String command = dis.readUTF();
            String fileId = dis.readUTF();

            System.out.println("Nhận yêu cầu chuyển file: " + command + ", fileId: " + fileId);

            FileTransferInfo transferInfo = activeTransfers.get(fileId);
            if (transferInfo == null) {
                dos.writeBoolean(false);
                return;
            }

            if (command.equals("SEND")) {
                System.out.println("Processing file transfer for fileId: " + fileId);
                receiveFileFromClient(dis, dos, transferInfo);
            } else if (command.equals("SEND_GROUP")) {
                System.out.println("Processing group file transfer for fileId: " + fileId);
                receiveFileFromClientGroup(dis, dos, transferInfo);
            } else if (command.equals("RECEIVE")) {
                sendFileToClient(dis, dos, transferInfo);
            }
        }  catch (IOException e) {
            System.err.println("Lỗi trong quá trình xử lý chuyển file: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Lỗi đóng socket file: " + e.getMessage());
            }
        }
    }

    private static void receiveFileFromClient(DataInputStream dis, DataOutputStream dos, FileTransferInfo transferInfo) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String uniqueFileName = timestamp + "_" + transferInfo.fileName;
        String filePath = FILE_STORAGE_DIR + uniqueFileName;

        dos.writeBoolean(true);

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;

            while (totalBytesRead < transferInfo.fileSize &&
                    (bytesRead = dis.read(buffer, 0, (int)Math.min(buffer.length,
                            transferInfo.fileSize - totalBytesRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                int progressPercent = (int) ((totalBytesRead * 100) / transferInfo.fileSize);
                dos.writeInt(progressPercent);
                dos.flush();
            }
        }

        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(transferInfo.fileId);
        fileInfo.setSender(transferInfo.sender);
        fileInfo.setReceiver(transferInfo.receiver);
        fileInfo.setFileName(transferInfo.fileName);
        fileInfo.setFileSize(transferInfo.fileSize);
        fileInfo.setStoragePath(filePath);
        fileInfo.setTimestamp(new Date());

        FileDAO.saveFileInfo(fileInfo);

        ClientHandler receiverHandler = ChatServer.getClientHandler(transferInfo.receiver);
        if (receiverHandler != null) {
            receiverHandler.sendMessage(Protocol.SVR_FILE_ACCEPT + transferInfo.fileId +
                    Protocol.PARAM_DELIMITER + transferInfo.sender +
                    Protocol.PARAM_DELIMITER + transferInfo.fileName +
                    Protocol.PARAM_DELIMITER + transferInfo.fileSize);
        }
    }

    private static void receiveFileFromClientGroup(DataInputStream dis, DataOutputStream dos, FileTransferInfo transferInfo) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String uniqueFileName = timestamp + "_" + transferInfo.fileName;
        String filePath = FILE_STORAGE_DIR + uniqueFileName;

        System.out.println("Receiving file for group: " + transferInfo.receiver);

        dos.writeBoolean(true);

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;

            while (totalBytesRead < transferInfo.fileSize &&
                    (bytesRead = dis.read(buffer, 0, (int)Math.min(buffer.length,
                            transferInfo.fileSize - totalBytesRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                int progressPercent = (int) ((totalBytesRead * 100) / transferInfo.fileSize);
                dos.writeInt(progressPercent);
                dos.flush();
            }
        }

        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(transferInfo.fileId);
        fileInfo.setSender(transferInfo.sender);
        fileInfo.setReceiver(transferInfo.receiver);
        fileInfo.setFileName(transferInfo.fileName);
        fileInfo.setFileSize(transferInfo.fileSize);
        fileInfo.setStoragePath(filePath);
        fileInfo.setTimestamp(new Date());

        FileDAO.saveFileInfo(fileInfo);

        List<String> groupMembers = GroupDAO.getGroupMembers(transferInfo.receiver);
        System.out.println("Group members for " + transferInfo.receiver + ": " + groupMembers);

        for (String member : groupMembers) {
            System.out.println("Sending file accept notification to group member: " + member);
            ClientHandler memberHandler = ChatServer.getClientHandler(member);
            if (memberHandler != null) {
                memberHandler.sendMessage(Protocol.SVR_GROUP_FILE_ACCEPT + transferInfo.fileId +
                        Protocol.PARAM_DELIMITER + transferInfo.sender +
                        Protocol.PARAM_DELIMITER + transferInfo.fileName +
                        Protocol.PARAM_DELIMITER + transferInfo.fileSize);
            }
        }
    }

    private static void sendFileToClient(DataInputStream dis, DataOutputStream dos,
                                         FileTransferInfo transferInfo) throws IOException {
        System.out.println("Processing download request for fileId: " + transferInfo.fileId);

        // Lấy thông tin file từ database
        FileInfo fileInfo = FileDAO.getFileInfoById(transferInfo.fileId);

        if (fileInfo == null) {
            System.out.println("File info not found in database for fileId: " + transferInfo.fileId);
            dos.writeBoolean(false); // Gửi false nếu không tìm thấy file info
            dos.flush();
            return;
        }

        // Kiểm tra file có tồn tại trên disk không
        File file = new File(fileInfo.getStoragePath());
        if (!file.exists()) {
            System.out.println("Physical file not found: " + fileInfo.getStoragePath());
            dos.writeBoolean(false); // Gửi false nếu file không tồn tại
            dos.flush();
            return;
        }

        // Kiểm tra file có thể đọc được không
        if (!file.canRead()) {
            System.out.println("Cannot read file: " + fileInfo.getStoragePath());
            dos.writeBoolean(false);
            dos.flush();
            return;
        }

        long fileSize = file.length();
        System.out.println("Sending file: " + fileInfo.getFileName() + ", size: " + fileSize + " bytes");

        // Gửi thông tin thành công
        dos.writeBoolean(true); // File có thể được download
        dos.writeLong(fileSize); // Gửi kích thước file thực tế
        dos.flush();

        // Gửi dữ liệu file
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesSent = 0;
            int lastProgress = 0;

            System.out.println("Starting file data transmission...");

            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
                totalBytesSent += bytesRead;

                // Tính toán và hiển thị tiến trình
                int progressPercent = (int) ((totalBytesSent * 100) / fileSize);
                if (progressPercent > lastProgress) {
                    System.out.println("Upload progress: " + progressPercent + "% (" + totalBytesSent + "/" + fileSize + " bytes)");
                    lastProgress = progressPercent;
                }

                // Flush để đảm bảo dữ liệu được gửi ngay
                if (totalBytesSent % (buffer.length * 4) == 0) { // Flush mỗi 32KB
                    dos.flush();
                }
            }

            // Flush cuối cùng để đảm bảo tất cả dữ liệu được gửi
            dos.flush();

            System.out.println("File transmission completed successfully. Total bytes sent: " + totalBytesSent);

            // Cập nhật trạng thái file đã được tải
            updateFileDownloadStatus(transferInfo.fileId, transferInfo.receiver);

        } catch (IOException e) {
            System.err.println("Error sending file data: " + e.getMessage());
            throw e; // Re-throw để handleFileTransfer xử lý
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    System.err.println("Error closing file input stream: " + e.getMessage());
                }
            }
        }
    }

    // Helper method để cập nhật trạng thái download
    private static void updateFileDownloadStatus(String fileId, String receiver) {
        try {
            // Thông báo cho người nhận rằng file đã được tải thành công
            ClientHandler receiverHandler = ChatServer.getClientHandler(receiver);
            if (receiverHandler != null) {
                receiverHandler.sendMessage(Protocol.SVR_FILE_DOWNLOAD + fileId);
            }

            // Log thông tin
            System.out.println("File download completed for user: " + receiver + ", fileId: " + fileId);

        } catch (Exception e) {
            System.err.println("Error updating file download status: " + e.getMessage());
        }
    }

    public static String createFileTransferRequest(String sender, String receiver,
                                                   String fileName, long fileSize, String fileId) {

        // Tạo ID duy nhất cho phiên chuyển file này
//        String fileId = UUID.randomUUID().toString();

        // Lưu thông tin chuyển file
        FileTransferInfo transferInfo = new FileTransferInfo(
                fileId, sender, receiver, fileName, fileSize);
        activeTransfers.put(fileId, transferInfo);

        return fileId;
    }

    public static void acceptFileTransfer(String fileId, String username, String groupName) {
        // Thêm log để debug
        System.out.println("File Accept: fileId=" + fileId + ", username=" + username);

        FileTransferInfo transferInfo = activeTransfers.get(fileId);
        if (transferInfo == null) {
            System.out.println("File transfer info not found for fileId: " + fileId);
            return;
        }

        System.out.println("TransferInfo: sender=" + transferInfo.sender +
                ", receiver=" + transferInfo.receiver);

        // Kiểm tra nếu người gọi là người nhận file HOẶC là người gửi file
        if (transferInfo.receiver.equals(username) || transferInfo.sender.equals(username) || GroupDAO.isGroupMember(groupName, username)) {
            // Thông báo cho người gửi rằng file đã được chấp nhận
            ClientHandler senderHandler = ChatServer.getClientHandler(transferInfo.sender);
            if (senderHandler != null) {
                if (groupName != null && !groupName.isEmpty()) {
                    senderHandler.sendMessage(Protocol.SVR_GROUP_FILE_ACCEPT + fileId);
                } else {
                    senderHandler.sendMessage(Protocol.SVR_FILE_ACCEPT + fileId);
                }
            } else {
                System.out.println("Sender handler not found for: " + transferInfo.sender);
            }
        } else {
            System.out.println("Username " + username + " doesn't match receiver or sender");
        }
    }

    public static void rejectFileTransfer(String fileId, String receiver) {
        FileTransferInfo transferInfo = activeTransfers.remove(fileId);
        if (transferInfo != null && transferInfo.receiver.equals(receiver)) {
            // Thông báo cho người gửi rằng file đã bị từ chối
            ClientHandler senderHandler = ChatServer.getClientHandler(transferInfo.sender);
            if (senderHandler != null) {
                senderHandler.sendMessage(Protocol.SVR_FILE_REJECT + fileId);
            }
        }
    }

    // Xóa thông tin chuyển file khi hoàn tất hoặc lỗi
    public static void removeFileTransfer(String fileId) {
        activeTransfers.remove(fileId);
    }

    // Dừng file server
    public static void stopFileServer() {
        if (fileTransferPool != null) {
            fileTransferPool.shutdown();
        }

        if (fileServerSocket != null && !fileServerSocket.isClosed()) {
            try {
                fileServerSocket.close();
            } catch (IOException e) {
                System.err.println("Lỗi khi đóng file server socket: " + e.getMessage());
            }
        }
    }

    public static FileTransferInfo getFileTransferInfo(String fileId) {
        return activeTransfers.get(fileId);
    }
}