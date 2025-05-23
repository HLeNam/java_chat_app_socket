package client.network;

import client.ChatClient;
import model.FileInfo;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Consumer;

public class FileTransferClient {
    private String serverIP;
    private int filePort;
    private ChatClient client;

    public FileTransferClient(String serverIP, int filePort, ChatClient client) {
        this.serverIP = serverIP;
        this.filePort = filePort;
        this.client = client;
    }

    public void uploadFile(String fileId, File file, String receiver, Consumer<Integer> progressCallback) {
        if (file == null || !file.exists()) {
            System.out.println("Error: File is null or doesn't exist: " + (file != null ? file.getAbsolutePath() : "null"));
            return;
        }

        System.out.println("Starting upload: fileId=" + fileId + ", file=" + file.getName() + ", size=" + file.length());

        // Tạo thread mới để upload file
        new Thread(() -> {
            try {
                Socket socket = new Socket(serverIP, filePort);
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                DataInputStream dis = new DataInputStream(socket.getInputStream());

                // Gửi yêu cầu upload
                dos.writeUTF("SEND");
                dos.writeUTF(fileId);
                dos.flush();

                // Đọc phản hồi từ server
                boolean canProceed = dis.readBoolean();
                if (!canProceed) {
                    progressCallback.accept(-1); // Lỗi
                    System.out.println("Server refused file upload: fileId=" + fileId);
                    return;
                }

                // Đọc file và gửi dữ liệu
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytesSent = 0;
                    long fileSize = file.length();

                    while ((bytesRead = fis.read(buffer)) != -1) {
                        dos.write(buffer, 0, bytesRead);
                        totalBytesSent += bytesRead;

                        // Cập nhật tiến trình
                        int progressPercent = (int)((totalBytesSent * 100) / fileSize);
                        progressCallback.accept(progressPercent);

                        // Đọc tiến trình từ server
                        dis.readInt(); // Bỏ qua giá trị tiến trình từ server
                    }

                    dos.flush();

                    // Kết thúc upload - đợi thêm thời gian để đảm bảo dữ liệu được truyền đi
                    try { Thread.sleep(500); } catch (InterruptedException e) {}

                    // Gửi thông báo đã hoàn thành 100%
                    progressCallback.accept(100);

                    System.out.println("File upload completed: fileId=" + fileId);

                    // Đảm bảo client biết rằng file đã được gửi thành công
                    if (client != null) {
                        SwingUtilities.invokeLater(() -> {
                            client.updateFileStatusInChat(fileId, receiver, "Đã gửi thành công");
                        });
                    }
                }

                socket.close();

            } catch (Exception e) {
                e.printStackTrace();
                progressCallback.accept(-1); // Lỗi
                System.out.println("Error uploading file: " + e.getMessage());
            }
        }).start();
    }

    public void downloadFile(String fileId, String sender, String fileName, long fileSize,
                             String savePath, Consumer<Integer> progressCallback) {
        System.out.println("Starting download: fileId=" + fileId + ", fileName=" + fileName + ", savePath=" + savePath);

        // Tạo thread mới để download file
        new Thread(() -> {
            Socket socket = null;
            FileOutputStream fos = null;
            DataInputStream dis = null;
            DataOutputStream dos = null;

            try {
                socket = new Socket(serverIP, filePort);
                dos = new DataOutputStream(socket.getOutputStream());
                dis = new DataInputStream(socket.getInputStream());

                // Gửi yêu cầu tải file
                dos.writeUTF("RECEIVE");
                dos.writeUTF(fileId);
                dos.flush();

                System.out.println("Sent download request for fileId: " + fileId);

                // Đọc trạng thái response từ server
                boolean canDownload = dis.readBoolean();
                System.out.println("Server response - canDownload: " + canDownload);

                if (canDownload) {
                    // Đọc kích thước file thực tế từ server
                    long actualFileSize = dis.readLong();
                    System.out.println("Actual file size from server: " + actualFileSize);

                    // Tạo file output stream với đường dẫn đã chỉ định
                    fos = new FileOutputStream(savePath);

                    // Đọc dữ liệu từ socket và ghi vào file
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytesRead = 0;
                    int lastProgress = 0;

                    System.out.println("Starting file data transfer...");

                    while (totalBytesRead < actualFileSize) {
                        bytesRead = dis.read(buffer, 0, (int)Math.min(buffer.length, actualFileSize - totalBytesRead));

                        if (bytesRead == -1) {
                            System.out.println("Unexpected end of stream. Downloaded: " + totalBytesRead + "/" + actualFileSize);
                            break;
                        }

                        fos.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;

                        // Cập nhật tiến trình
                        int progress = (int) ((totalBytesRead * 100) / actualFileSize);
                        if (progress > lastProgress) {
                            progressCallback.accept(progress);
                            lastProgress = progress;
                            System.out.println("Download progress: " + progress + "% (" + totalBytesRead + "/" + actualFileSize + " bytes)");
                        }
                    }

                    // Đảm bảo tiến trình 100% khi hoàn thành
                    if (totalBytesRead >= actualFileSize) {
                        progressCallback.accept(100);
                        System.out.println("File download completed successfully: " + fileName);

                        // Thông báo cho client rằng đã tải xong
                        if (client != null) {
                            client.updateFileStatusInChat(fileId, sender, "Đã tải xong");
                        }
                    } else {
                        System.out.println("File download incomplete. Expected: " + actualFileSize + ", Got: " + totalBytesRead);
                        progressCallback.accept(-1);
                        if (client != null) {
                            client.updateFileStatusInChat(fileId, sender, "Lỗi: Tải file không hoàn chỉnh");
                        }
                    }

                } else {
                    // Server không thể cung cấp file
                    System.out.println("Server cannot provide file for fileId: " + fileId);
                    progressCallback.accept(-1);
                    if (client != null) {
                        client.updateFileStatusInChat(fileId, sender, "Lỗi: Server không thể cung cấp file");
                    }
                }

            } catch (Exception e) {
                System.err.println("Error downloading file: " + e.getMessage());
                e.printStackTrace();
                progressCallback.accept(-1);
                if (client != null) {
                    client.updateFileStatusInChat(fileId, sender, "Lỗi: " + e.getMessage());
                }
            } finally {
                // Đóng tất cả resources
                try {
                    if (fos != null) {
                        fos.close();
                    }
                    if (dis != null) {
                        dis.close();
                    }
                    if (dos != null) {
                        dos.close();
                    }
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException e) {
                    System.err.println("Error closing resources: " + e.getMessage());
                }
            }
        }).start();
    }
}