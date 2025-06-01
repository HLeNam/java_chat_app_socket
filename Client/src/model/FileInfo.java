package model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

public class FileInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String sender;
    private String receiver;
    private String fileName;
    private long fileSize;
    private String storagePath;
    private Date timestamp;

    public FileInfo() {
        // Default constructor
    }

    public FileInfo(String id, String sender, String receiver, String fileName, long fileSize, String storagePath) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.storagePath = storagePath;
        this.timestamp = new Date();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "FileInfo{" +
                "id='" + id + '\'' +
                ", sender='" + sender + '\'' +
                ", receiver='" + receiver + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", storagePath='" + storagePath + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}