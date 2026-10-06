package space.grayt.teremok.storage;

import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SourceObject;
import io.minio.UploadObjectArgs;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class MinioObjectStorage implements ObjectStorage {

    private final MinioClient client;
    private final String bucket;

    public MinioObjectStorage(String endpoint, String accessKey, String secretKey, String bucket) {
        client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
        ensureBucket();
    }

    @Override
    public void put(String objectKey, InputStream input, long size, String contentType) {
        run(() -> client.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .stream(input, size, -1L)
                .contentType(contentType)
                .build()));
    }

    @Override
    public void copy(String sourceKey, String targetKey) {
        run(() -> client.copyObject(CopyObjectArgs.builder()
                .bucket(bucket)
                .object(targetKey)
                .source(SourceObject.builder().bucket(bucket).object(sourceKey).build())
                .build()));
    }

    @Override
    public void delete(String objectKey) {
        run(() -> {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return null;
        });
    }

    @Override
    public void download(String objectKey, Path target) {
        run(() -> {
            try (var input = client.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return null;
        });
    }

    @Override
    public void upload(Path source, String objectKey, String contentType) {
        run(() -> client.uploadObject(UploadObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .filename(source.toString())
                .contentType(contentType)
                .build()));
    }

    private void ensureBucket() {
        try {
            if (bucketExists()) {
                return;
            }
            try {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            } catch (Exception creationException) {
                // Several services can start simultaneously and race to create the shared bucket.
                if (!bucketExists()) {
                    throw creationException;
                }
            }
        } catch (Exception exception) {
            throw new ObjectStorageException(exception);
        }
    }

    private boolean bucketExists() throws Exception {
        return client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
    }

    private static <T> T run(StorageCall<T> call) {
        try {
            return call.execute();
        } catch (Exception exception) {
            throw new ObjectStorageException(exception);
        }
    }

    @FunctionalInterface
    private interface StorageCall<T> {
        T execute() throws Exception;
    }
}
