package com.xuecheng.media;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.UploadObjectArgs;
import io.minio.errors.MinioException;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * @author Mr.M
 * @version 1.0
 * @description 测试MinIO
 * @date 2022/9/11 21:24
 */
public class MinIOTest {

    static MinioClient minioClient =
            MinioClient.builder()
                    .endpoint("http://192.168.202.129:9000")
                    .credentials("minio", "minio123")
                    .build();


    //上传文件
    public static void upload() throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket("testbucket").build());
            //检查testbucket桶是否创建，没有创建自动创建
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket("testbucket").build());
            } else {
                System.out.println("Bucket 'testbucket' already exists.");
            }
            //上传1.mp4
            minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket("testbucket")
                            .object("1.jpg")
                            .filename("C:\\Users\\lihuanzhi\\Pictures\\Saved Pictures\\1.jpg")
                            .build());
            //上传1.jpg,上传到当前日期文件夹的子目录
            minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket("testbucket")
                            .object("2023/1/22/1.jpg")
                            .filename("C:\\Users\\lihuanzhi\\Pictures\\Saved Pictures\\1.jpg")
                            .build());
            System.out.println("上传成功");
        } catch (MinioException e) {
            System.out.println("Error occurred: " + e);
            System.out.println("HTTP trace: " + e.httpTrace());
        }

    }

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        upload();
    }


}
