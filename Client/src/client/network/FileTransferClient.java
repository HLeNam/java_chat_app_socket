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

    public void uploadFile(String fileId, File file, String receiver,
                           Consumer<Integer> progressCallback) {
        new Thread(() -> {
            try (Socket socket = new Socket(serverIP, filePort);
                 DataInputStream dis = new DataInputStream(socket.getInputStream());
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                // Gửi thông tin phiên chuyển file
                dos.writeUTF("SEND");
                dos.writeUTF(fileId);

                // Kiểm tra phản hồi từ server
                boolean canProceed = dis.readBoolean();
                if (!canProceed) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(null,
                                    "Không thể gửi file: Server từ chối kết nối",
                                    "Lỗi", JOptionPane.ERROR_MESSAGE));
                    return;
                }

                // Gửi file
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytesSent = 0;

                    while ((bytesRead = fis.read(buffer)) != -1) {
                        dos.write(buffer, 0, bytesRead);
                        dos.flush();

                        totalBytesSent += bytesRead;

                        // Nhận progress update từ server
                        int progressPercent = dis.readInt();

                        // Cập nhật progress bar
                        if (progressCallback != null) {
                            SwingUtilities.invokeLater(() ->
                                    progressCallback.accept(progressPercent));
                        }

                        // Khi hoàn thành upload
                        if (progressPercent >= 100) {
                            // Thông báo cho ChatFrame cập nhật trạng thái trong chat
                            client.updateFileStatusInChat(fileId, receiver, "Đã gửi thành công");
                        }
                    }
                }

                // Lưu thông tin file vào local storage
                FileInfo fileInfo = new FileInfo();
                fileInfo.setId(fileId);
                fileInfo.setSender(client.getCurrentUser().getUsername());
                fileInfo.setReceiver(receiver);
                fileInfo.setFileName(file.getName());
                fileInfo.setFileSize(file.length());
                fileInfo.setStoragePath(file.getAbsolutePath());

                client.getLocalStorage().saveFileSent(fileInfo);
            }  catch (IOException e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(null,
                                "Lỗi khi gửi file: " + e.getMessage(),
                                "Lỗi", JOptionPane.ERROR_MESSAGE));
            }
        }).start();
    }

    public void downloadFile(String fileId, String sender, String fileName, long fileSize,
                             String savePath, Consumer<Integer> progressCallback) {
        new Thread(() -> {
            try (Socket socket = new Socket(serverIP, filePort);
                 DataInputStream dis = new DataInputStream(socket.getInputStream());
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
                // Gửi thông tin phiên chuyển file
                dos.writeUTF("RECEIVE");
                dos.writeUTF(fileId);

                boolean fileExists = dis.readBoolean();
                if (!fileExists) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(null,
                                    "Không thể tải file: File không tồn tại trên server",
                                    "Lỗi", JOptionPane.ERROR_MESSAGE));
                    return;
                }

                long actualFileSize = dis.readLong();

                Files.createDirectories(Paths.get(savePath).getParent());

                try (FileOutputStream fos = new FileOutputStream(savePath)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytesRead = 0;

                    while (totalBytesRead < actualFileSize &&
                            (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length,
                                    actualFileSize - totalBytesRead))) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;

                        // Đọc progress update từ server
                        int progressPercent = dis.readInt();

                        // Cập nhật progress bar
                        if (progressCallback != null) {
                            final int percent = progressPercent;
                            SwingUtilities.invokeLater(() -> {
                                progressCallback.accept(percent);

                                // Khi hoàn thành download
                                if (percent >= 100) {
                                    // Thông báo cho ChatFrame cập nhật trạng thái trong chat
                                    client.updateFileStatusInChat(fileId, sender,
                                            "Đã tải về thành công - [" + savePath + "]");
                                }
                            });
                        }
                    }
                }

                // Lưu thông tin file vào local storage
                FileInfo fileInfo = new FileInfo();
                fileInfo.setId(fileId);
                fileInfo.setSender(sender);
                fileInfo.setReceiver(client.getCurrentUser().getUsername());
                fileInfo.setFileName(fileName);
                fileInfo.setFileSize(fileSize);
                fileInfo.setStoragePath(savePath);

                client.getLocalStorage().saveFileReceived(fileInfo);

                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(null,
                                "Tải file thành công!\nLưu tại: " + savePath,
                                "Thành công", JOptionPane.INFORMATION_MESSAGE));
            } catch (IOException e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(null,
                                "Lỗi khi tải file: " + e.getMessage(),
                                "Lỗi", JOptionPane.ERROR_MESSAGE));
            }
        }).start();
    }
}