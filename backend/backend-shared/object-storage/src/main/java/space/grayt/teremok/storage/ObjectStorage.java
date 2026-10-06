package space.grayt.teremok.storage;

import java.io.InputStream;
import java.nio.file.Path;

public interface ObjectStorage {

    void put(String objectKey, InputStream input, long size, String contentType);

    void copy(String sourceKey, String targetKey);

    void delete(String objectKey);

    void download(String objectKey, Path target);

    void upload(Path source, String objectKey, String contentType);
}
