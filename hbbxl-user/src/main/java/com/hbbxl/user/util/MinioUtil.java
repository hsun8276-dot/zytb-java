package com.hbbxl.user.util;

import com.hbbxl.common.core.exception.BizException;
import io.minio.*;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 文件上传下载工具类
 */
@Component
public class MinioUtil {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.public-endpoint}")
    private String publicEndpoint;

    /**
     * 上传文件，返回可访问的 URL
     *
     * @param dir  目录
     * @param file 上传的文件
     * @return 完整的文件 URL
     */
    public String upload(String dir, MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            String filename = file.getOriginalFilename();
            String ext = "";
            if (filename != null && filename.contains(".")) {
                ext = filename.substring(filename.lastIndexOf('.'));
            }
            String objectName = dir + "/" + java.time.LocalDate.now() + "/"
                    + UUID.randomUUID().toString().replace("-", "") + ext;

            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .contentType(file.getContentType())
                            .stream(input, file.getSize(), 10485760)
                            .build()
            );

            return publicEndpoint + "/" + bucket + "/" + objectName;
        } catch (Exception e) {
            throw new BizException("文件上传失败：" + e.getMessage());
        }
    }

    /**
     * 删除对象
     */
    public void delete(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
        } catch (Exception e) {
            // 忽略删除失败
        }
    }

    /**
     * 生成预签名下载 URL（适用于私有桶）
     */
    public String presignedGetObject(String objectName, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            throw new BizException("生成签名 URL 失败");
        }
    }
}