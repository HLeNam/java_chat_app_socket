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

                    // Kết thúc upload
                    progressCallback.accept(100);

                    System.out.println("File upload completed: fileId=" + fileId);
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
        // Tạo thread mới để download file
        new Thread(() -> {
            try {
                Socket socket = new Socket(serverIP, filePort);

                // Gửi yêu cầu tải file
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                dos.writeUTF("DOWNLOAD:" + fileId + ":" + sender);

                // Đọc dữ liệu file
                DataInputStream dis = new DataInputStream(socket.getInputStream());

                // Trạng thái response
                String response = dis.readUTF();
                if (response.startsWith("OK")) {
                    // Tạo file output stream với đường dẫn đã chỉ định
                    FileOutputStream fos = new FileOutputStream(savePath);

                    // Đọc dữ liệu từ socket và ghi vào file
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytesRead = 0;
                    int lastProgress = 0;

                    while (totalBytesRead < fileSize && (bytesRead = dis.read(buffer, 0, buffer.length)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;

                        // Cập nhật tiến trình
                        int progress = (int) ((totalBytesRead * 100) / fileSize);
                        if (progress > lastProgress) {
                            progressCallback.accept(progress);
                            lastProgress = progress;
                        }
                    }

                    fos.close();

                    // Đã tải xong
                    progressCallback.accept(100);

                    // Thông báo cho client rằng đã tải xong
                    client.updateFileStatusInChat(fileId, sender, "Đã tải xong");
                } else {
                    // Lỗi
                    client.updateFileStatusInChat(fileId, sender, "Lỗi: " + response);
                }

                socket.close();

            } catch (Exception e) {
                e.printStackTrace();
                client.updateFileStatusInChat(fileId, sender, "Lỗi: " + e.getMessage());
            }
        }).start();
    }
}