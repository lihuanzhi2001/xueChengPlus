package com.xuecheng.media.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.model.PageParams;
import com.xuecheng.base.model.PageResult;
import com.xuecheng.base.model.RestResponse;
import com.xuecheng.media.mapper.MediaFilesMapper;
import com.xuecheng.media.mapper.MediaProcessMapper;
import com.xuecheng.media.model.dto.QueryMediaParamsDto;
import com.xuecheng.media.model.dto.UploadFileParamsDto;
import com.xuecheng.media.model.dto.UploadFileResultDto;
import com.xuecheng.media.model.po.MediaFiles;
import com.xuecheng.media.model.po.MediaProcess;
import com.xuecheng.media.service.MediaFileService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.UploadObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * @author lihuanzhi
 * @version 1.0
 * @description TODO
 * @date 2022/10/10 8:58
 */
@Slf4j
@Service
public class MediaFileServiceImpl implements MediaFileService {

    @Autowired
    MediaFilesMapper mediaFilesMapper;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MediaProcessMapper mediaProcessMapper;

    @Autowired
    private MediaFileService currentProxyMediaFile;

    //普通文件桶
    @Value("${minio.bucket.files}")
    private String bucket_files;

    //视频文件桶
    @Value("${minio.bucket.videofiles}")
    private String bucket_videofiles;

    @Override
    public PageResult<MediaFiles> queryMediaFiels(Long companyId, PageParams pageParams, QueryMediaParamsDto queryMediaParamsDto) {

        //构建查询条件对象
        LambdaQueryWrapper<MediaFiles> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StringUtils.isNotEmpty(queryMediaParamsDto.getAuditStatus()), MediaFiles::getAuditStatus, queryMediaParamsDto.getAuditStatus());
        queryWrapper.eq(StringUtils.isNotEmpty(queryMediaParamsDto.getFileType()), MediaFiles::getFileType, queryMediaParamsDto.getFileType());
        queryWrapper.like(MediaFiles::getFilename, queryMediaParamsDto.getFilename());

        //分页对象
        Page<MediaFiles> page = new Page<>(pageParams.getPageNo(), pageParams.getPageSize());
        // 查询数据内容获得结果
        Page<MediaFiles> pageResult = mediaFilesMapper.selectPage(page, queryWrapper);
        // 获取数据列表
        List<MediaFiles> list = pageResult.getRecords();
        // 获取数据总数
        long total = pageResult.getTotal();
        // 构建结果集
        PageResult<MediaFiles> mediaListResult = new PageResult<>(list, total, pageParams.getPageNo(), pageParams.getPageSize());
        return mediaListResult;

    }

    /**
     * 上传文件
     *
     * @param companyId           机构id
     * @param bytes               文件字节流数值
     * @param uploadFileParamsDto 文件信息
     * @param folder              文件目录，如果不传则默认当前日期的年、月、日作为目录
     * @param objectName          文件名称 (不传默认使用文件的md5值)
     * @return
     */
    @Override
    public UploadFileResultDto uploadFile(Long companyId, byte[] bytes, UploadFileParamsDto uploadFileParamsDto, String folder, String objectName) {
        //文件的md5值
        String fileId = DigestUtils.md5Hex(bytes);
        //文件名称
        String filename = uploadFileParamsDto.getFilename();
        //校验文件名称
        if (StringUtils.isEmpty(objectName)) {
            //如果文件名为空的话就使用文件名的md5值作为文件名称
            //md5Value.jpg / md5Value.mp4
            objectName = fileId + filename.substring(filename.lastIndexOf("."));
        }


        //校验用户是否传输了folder值
        if (StringUtils.isEmpty(folder)) {
            //通过日期构建存储路径
            folder = getFileFolder(new Date(), true, true, true);
        } else if (folder.indexOf("/") < 0) {
            folder = folder + "/";
        }

        //构建对象名称 folder + objectName
        objectName = folder + objectName;
//        MediaFiles mediaFiles = null;
        try {
            addMediaFilesToMinIO(bytes, bucket_files, objectName);

            MediaFiles mediaFilesToDb = currentProxyMediaFile.addMediaFilesToDb(companyId, fileId, uploadFileParamsDto, bucket_files, objectName);
            //数据封装返回
            UploadFileResultDto uploadFileResultDto = new UploadFileResultDto();
            /**如果这个mediaFIles为空的话就会报错，
             * 但什么时候为null呢，
             * 当这个文件的信息存在数据库的时候就为null
             * 所以要判断一下是否为空，为空的话就不进行数据拷贝了
             *
             */
            if (mediaFilesToDb != null) {
                BeanUtils.copyProperties(mediaFilesToDb, uploadFileResultDto);
            }
            return uploadFileResultDto;

        } catch (Exception e) {
            log.debug("上传文件过程中出错：{}", e.getMessage());
            XueChengPlusException.cast("上传文件过程中出错");
        }

        return null;
    }

    /**
     * 代码抽取优化: 上传文件到minIO
     *
     * @param bytes        文件字节流数组
     * @param bucket_files 桶
     * @param objectName   对象名称
     */
    private void addMediaFilesToMinIO(byte[] bytes, String bucket_files, String objectName) {

        //资源的媒体类型
        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;  // 默认未知二进制流

        if (objectName.indexOf(".") >= 0) {
            //取objectName中的拓展名
            String extension = objectName.substring(objectName.lastIndexOf("."));
//            ContentInfo extensionMatch = ContentInfoUtil.findExtensionMatch(extension);
//            // 如果这种类型存在的话 例如：.jpg、.mp4  不存在的就例如.abc
//            if(extensionMatch != null){
//                contentType = extensionMatch.getMimeType();
//            }
            contentType = getMimeTypeByExtenion(extension);
        }


        //字节数组转为流对象
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);

        try {
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(bucket_files)
                    .object(objectName)
                    // InputStream stream,
                    // long objectSize,
                    // long partSize（分片大小，-1表示最小分片 5M，最大不能超过5T，分片数量最大10000份）
                    .stream(byteArrayInputStream, byteArrayInputStream.available(), -1)
                    .contentType(contentType)
                    .build();

            //上传到minio
            minioClient.putObject(putObjectArgs);
        } catch (Exception e) {
            log.debug("上传文件到文件系统出错，{}", e.getMessage());
            XueChengPlusException.cast("上传文件到文件系统出错");
        }
    }

    //将文件上传到minIO，传入文件绝对路径
    public void addMediaFilesToMinIO(String filePath, String bucket, String objectName) {
        try {
            minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .filename(filePath)
                            .build());
        } catch (Exception e) {
            e.printStackTrace();
            XueChengPlusException.cast("上传文件到文件系统出错");
        }
    }


    /**
     * 代码抽取优化：文件信息插入到数据库
     *
     * @param companyId           机构id
     * @param fileId              文件md5值
     * @param uploadFileParamsDto 文件信息
     * @param bucket_files        桶id
     * @param objectName          对象名
     * @return
     */
    @Transactional
    public MediaFiles addMediaFilesToDb(Long companyId, String fileId,
                                        UploadFileParamsDto uploadFileParamsDto,
                                        String bucket_files, String objectName) {
        /**
         * 更新策略：
         *      有一个为null都全部更新
         */


//        //从数据库查询数据
        MediaFiles mediaFiles = mediaFilesMapper.selectById(fileId);
//        //从minio查询文件
//        RestResponse<Boolean> hasFileOfMinIO = checkFile(mediaFiles.getId());
//        RestResponse<Boolean> restResponse = checkFile(fileId);

        //TODO 需要判断文件路径地址是否相同

        if (mediaFiles == null) {
            mediaFiles = mediaFilesMapper.selectById(fileId);
            mediaFiles = new MediaFiles();
            //拷贝基本信息
            BeanUtils.copyProperties(uploadFileParamsDto, mediaFiles);
            mediaFiles.setId(fileId);
            mediaFiles.setFileId(fileId);
            mediaFiles.setCompanyId(companyId);


            //图片、mp4格式的文件才保存到数据库
            String filename = uploadFileParamsDto.getFilename();
            //文件拓展名
            String extension = null;
            //获取该格式的文件
            if (StringUtils.isNotEmpty(filename) && filename.indexOf(".") >= 0) {
                extension = filename.substring(filename.lastIndexOf("."));
            }
            //获得该格式文件的媒体类型
            String mimeType = getMimeTypeByExtenion(extension);
            if (mimeType.indexOf("image") >= 0 || mimeType.indexOf("mp4") >= 0) {
                mediaFiles.setUrl("/" + bucket_files + "/" + objectName);
            }

            mediaFiles.setBucket(bucket_files);
            mediaFiles.setCreateDate(LocalDateTime.now());
            mediaFiles.setStatus("1");
            mediaFiles.setAuditStatus("002003");
            mediaFiles.setFilePath(objectName);


            //保存文件信息到媒资信息表
            int insert = mediaFilesMapper.insert(mediaFiles);
            if (insert < 0) {
                XueChengPlusException.cast("保存文件信息失败");
            }

            //如果文件是avi格式的话就添加到待处理任务表
            if (mimeType.equals("video/x-msvideo")) {
                //待处理任务信息
                MediaProcess mediaProcess = new MediaProcess();
                BeanUtils.copyProperties(mediaFiles, mediaProcess);
                //设置状态 ( "1"未处理 )
                mediaProcess.setStatus("1");
                mediaProcessMapper.insert(mediaProcess);
            }

        }


        return mediaFiles;
    }


    /**
     * 检查文件是否存在
     *
     * @param fileMd5 文件的md5值
     * @return false 不存在、 true 存在
     */
    @Override
    public RestResponse<Boolean> checkFile(String fileMd5) {
        // 在文件系统中存在 且 在数据库中存在该文件返回该文件已存在

        //查询数据库是否已存在
        MediaFiles mediaFiles = mediaFilesMapper.selectById(fileMd5);
        if (mediaFiles == null) {
            //文件不存在
            return RestResponse.success(false);
        }
        //查看文件系统是否已存在该文件
        GetObjectArgs object = GetObjectArgs.builder()
                .bucket(mediaFiles.getBucket())
                .object(mediaFiles.getFilePath())
                .build();

        try (
                InputStream inputStream = minioClient.getObject(object);
        ) {
            //如果不报异常但是该返回的流对象也为空，同样也是不存在
            if (inputStream == null) {
                return RestResponse.success(false);
            }
        } catch (Exception e) {
            return RestResponse.success(false);

            //这里不能再抛异常了，否则会导致无法上传文件
//            XueChengPlusException.cast("上传文件过程中出现问题，请重新上传");
        }

        //文件已存在
        return RestResponse.success(true);
    }


    /**
     * 检查分块是否存在
     *
     * @param fileMd5    文件的md5值
     * @param chunkIndex 分块序号
     * @return false 不存在、 true 存在
     */
    @Override
    public RestResponse<Boolean> checkChunk(String fileMd5, int chunkIndex) {

        //得到分块文件所在目录
        String chunkFileFolderPath = getChunkFileFolderPath(fileMd5);
        //得到分块文件的路径
        String chunkFilePath = chunkFileFolderPath + chunkIndex;

        InputStream inputStream = null;
        try {
            //查看文件系统是否已存在该分块文件
            GetObjectArgs object = GetObjectArgs.builder()
                    .bucket(bucket_videofiles)
                    .object(chunkFilePath)
                    .build();

            inputStream = minioClient.getObject(object);
            //如果不报异常但是该返回的流对象也为空，同样也是不存在
            if (inputStream == null) {
                return RestResponse.success(false);
            }
        } catch (Exception e) {
            return RestResponse.success(false);
        } finally {
            // TODO 关闭流，解决minio上传宕机问题？
            try {
                if (inputStream != null) {
                    inputStream.close();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //文件已存在
        return RestResponse.success(true);

    }


    /**
     * 上传分块
     *
     * @param fileMd5 文件md5值
     * @param chunk   分块序号
     * @param bytes   文件字节数组
     * @return
     */
    @Override
    public RestResponse uploadChunk(String fileMd5, int chunk, byte[] bytes) {
        //得到分块文件所在目录
        String chunkFileFolderPath = getChunkFileFolderPath(fileMd5);
        //得到分块文件的路径
        String chunkFilePath = chunkFileFolderPath + chunk;

        //上传分块
        try {
            addMediaFilesToMinIO(bytes, bucket_videofiles, chunkFilePath);
        } catch (Exception e) {
            log.debug("上传文件分块到文件系统出错: {}", e.getMessage());
//            return RestResponse.success(false);
            XueChengPlusException.cast("上传过程中出现问题，请重试");
        }


//        return RestResponse.success(true);
        return RestResponse.success();
    }


    /**
     * 合并分块
     *
     * @param companyId           机构id
     * @param fileMd5             文件md5值
     * @param chunkTotal          分块总合
     * @param uploadFileParamsDto 文件信息
     * @return
     */
    @Override
    public RestResponse mergechunks(Long companyId, String fileMd5, int chunkTotal, UploadFileParamsDto uploadFileParamsDto) {

        String fileName = uploadFileParamsDto.getFilename();
        // 下载所有分块
        File[] chunkFiles = checkChunkStatus(fileMd5, chunkTotal);
        //拓展名
        String extension = fileName.substring(fileName.lastIndexOf("."));
        //创建临时文件作为合并文件
        File mergeFile = null;
        try {
            mergeFile = File.createTempFile(fileMd5, extension);
        } catch (IOException e) {
            log.debug("创建合并用的临时文件出错, md5值: {}", fileMd5);
            XueChengPlusException.cast("创建合并用的临时文件出错");
        }

        // 合并分块
        try {


            //缓冲区
            byte[] b = new byte[1024];
            try (
                    //使用 RandomAccessFile 来读取文件（这个RandomAccessFile经常被用来多线程下载和断点续传）
                    RandomAccessFile raf_write = new RandomAccessFile(mergeFile, "rw");
            ) {
                //合并文件
                for (File chunkFile : chunkFiles) {
                    try (RandomAccessFile raf_read = new RandomAccessFile(chunkFile, "r");) {
                        int len = -1;
                        while ((len = raf_read.read(b)) != -1) {
                            raf_write.write(b, 0, len);
                        }
                    }
                }
            } catch (IOException e) {
                log.debug("合并文件过程出错");
                XueChengPlusException.cast("合并文件过程出错");
            }

            //文件合并完成
            log.debug("文件合并完成, 文件路径{}", mergeFile.getAbsoluteFile());

            try (InputStream mergeFileInputStream = new FileInputStream(mergeFile)) {

                //对合并文件的md5值进行校验
                String newFileMd5 = DigestUtils.md5Hex(mergeFileInputStream);
                if (!fileMd5.equalsIgnoreCase(newFileMd5)) {
                    //校验失败
                    log.debug("合并文件校验不通过{}", mergeFile.getAbsolutePath());
                    XueChengPlusException.cast("合并文件校验不通过");
                }
                log.debug("合并文件校验通过{}", mergeFile.getAbsolutePath());
            } catch (Exception e) {
                log.debug("合并文件校验异常");
            }

            //将临时文件上传到minio
            String mergeFilePath = getFilePathByMd5(fileMd5, extension);
            try {
                addMediaFilesToMinIO(mergeFile.getAbsolutePath(), bucket_videofiles, mergeFilePath);
                log.debug("合并文件上传MinIO完成{}", mergeFile.getAbsolutePath());
            } catch (Exception e) {
                log.debug("合并文件时上传文件出错");
                XueChengPlusException.cast("合并文件时上传文件出错");
            }

            uploadFileParamsDto.setFileSize(mergeFile.length());
            //文件信息入库
            MediaFiles mediaFiles = addMediaFilesToDb(companyId, fileMd5, uploadFileParamsDto, bucket_videofiles, mergeFilePath);
            if (mediaFiles == null) {
                log.debug("媒资文件入库出错");
                XueChengPlusException.cast("媒资文件入库出错");
            }
            return RestResponse.success();

        } finally {
            //删除所有分块文件
            if (chunkFiles != null) {
                for (File chunkFile : chunkFiles) {
                    try {
                        chunkFile.delete();
                    } catch (Exception e) {

                    }
                }
            }

            //删除合并后的临时文件
            if (mergeFile != null) {
                try {
                    mergeFile.delete();
                } catch (Exception e) {

                }

            }

        }

    }


    /**
     * 根据id查询文件信息
     *
     * @param id 文件id
     * @return
     */
    @Override
    public MediaFiles getFileById(String id) {
        MediaFiles mediaFiles = mediaFilesMapper.selectById(id);
        if (mediaFiles == null) {
            XueChengPlusException.cast("文件不存在");
        }
        String mediaFilesUrl = mediaFiles.getUrl();
        if (StringUtils.isEmpty(mediaFilesUrl)) {
            XueChengPlusException.cast("文件未处理完成，请稍后预览");
        }
        return mediaFiles;
    }


    //根据拓展名匹配对应的媒资类型
    public String getMimeTypeByExtenion(String extension) {
        //资源的媒体类型
        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;  // 默认未知二进制流
        if (StringUtils.isNotEmpty(extension)) {
            ContentInfo extensionMatch = ContentInfoUtil.findExtensionMatch(extension);
            // 如果这种类型存在的话 例如：.jpg、.mp4  不存在的就例如.abc
            if (extensionMatch != null) {
                contentType = extensionMatch.getMimeType();
            }
        }
        return contentType;
    }


    /**
     * 检查分块是否全部上传完毕
     *
     * @param fileMd5    文件md5值
     * @param chunkTotal 分块总数
     * @return 分块文件列表
     */
    private File[] checkChunkStatus(String fileMd5, int chunkTotal) {
        //得到分块文件的目录
        String chunkFileFolderPath = getChunkFileFolderPath(fileMd5);

        File[] files = new File[chunkTotal];

        //检查分块是否上传完毕
        for (int i = 0; i < chunkTotal; i++) {
            String chunkFilePath = chunkFileFolderPath + i;

            //分块文件
            File chunkFile = null;
            //创建一个临时文件
            try {
                chunkFile = File.createTempFile("chunk" + i, null);
            } catch (IOException e) {
                log.debug("下载分块时创建临时文件出错: {}", e.getMessage());
                XueChengPlusException.cast("下载分块时创建临时文件出错");

            }

            //下载分块
            downloadFileFromMinIO(chunkFile, bucket_videofiles, chunkFilePath);
            files[i] = chunkFile;

        }

        return files;
    }

    /**
     * 根据桶和文件路径从minio下载文件
     *
     * @param file       临时存储分块文件
     * @param bucket     分块存储桶
     * @param objectName 分块对象名
     * @return 从minio下载的临时文件的File对象
     */
    public File downloadFileFromMinIO(File file, String bucket, String objectName) {
        InputStream inputStream = null;
        OutputStream outputStream = null;


        try {
            GetObjectArgs objectArgs = GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build();
            try {
                inputStream = minioClient.getObject(objectArgs);
                outputStream = new FileOutputStream(file);
                //拷贝流
                IOUtils.copy(inputStream, outputStream);
            } catch (IOException e) {
                log.debug("下载文件 {} 出错", objectName);
                XueChengPlusException.cast("下载文件 " + objectName + " 出错");
            }

        } catch (Exception e) {
            e.printStackTrace();
            log.debug("文件不存在");
            XueChengPlusException.cast("文件不存在");
        } finally {
            //关闭输出流
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            //关闭输入流
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


        return file;

    }


    //根据日期拼接目录
    private String getFileFolder(Date date, boolean year, boolean month, boolean day) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        //获取当前日期字符串
        String dateString = sdf.format(new Date());
        //取出年、月、日
        String[] dateStringArray = dateString.split("-");
        StringBuffer folderString = new StringBuffer();
        if (year) {
            folderString.append(dateStringArray[0]);
            folderString.append("/");
        }
        if (month) {
            folderString.append(dateStringArray[1]);
            folderString.append("/");
        }
        if (day) {
            folderString.append(dateStringArray[2]);
            folderString.append("/");
        }
        return folderString.toString();
    }


    //根据md5值得到分块文件的目录
    private String getChunkFileFolderPath(String fileMd5) {
        return fileMd5.substring(0, 1) + "/" + fileMd5.substring(1, 2) + "/" + fileMd5 + "/chunk/";
    }

    //根据md5值得到文件的完成路径（md5.mp4）
    private String getFilePathByMd5(String fileMd5, String fileExt) {
        return fileMd5.substring(0, 1) + "/" + fileMd5.substring(1, 2) + "/" + fileMd5 + "/" + fileMd5 + fileExt;
    }

}
