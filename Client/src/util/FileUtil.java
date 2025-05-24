package util;

import javax.imageio.IIOException;
import java.io.File;

public class FileUtil {
    public static String[] copyFile(File source, String destinationPath) throws IIOException {
        String savePath = destinationPath + File.separator + source.getName();
        File destination = new File(savePath);

        int count = 1;
        while (destination.exists()) {
            String newName = source.getName();
            String extension = "";

            int dotIndex = newName.lastIndexOf('.');
            if (dotIndex > 0) {
                extension = newName.substring(dotIndex);
                newName = newName.substring(0, dotIndex);
            }

            destination = new File(destinationPath + File.separator + newName + " (" + count + ")" + extension);
            count++;
        }

        // Use Files.copy() to copy the file
        try {
            java.nio.file.Files.copy(source.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException e) {
            throw new IIOException("Failed to copy file: " + source.getAbsolutePath(), e);
        }

        return new String[]{destination.getAbsolutePath(), destination.getName()};
    }
}
