package com.xuecheng.media.service;

import com.xuecheng.base.model.PageParams;
import com.xuecheng.base.model.PageResult;
import com.xuecheng.base.model.RestResponse;
import com.xuecheng.media.model.dto.QueryMediaParamsDto;
import com.xuecheng.media.model.dto.UploadFileParamsDto;
import com.xuecheng.media.model.dto.UploadFileResultDto;
import com.xuecheng.media.model.po.MediaFiles;

import java.io.File;

/**
 * @author lihuanzhi
 * @version 1.0
 * @description 媒资文件管理业务类
 * @date 2022/10/10 8:55
 */
public interface MediaFileService {

    /**
     * @param pageParams          分页参数
     * @param queryMediaParamsDto 查询条件
     * @return com.xuecheng.base.model.PageResult<com.xuecheng.media.model.po.MediaFiles>
     * @description 媒资文件查询方法
     * @author lihuanzhi
     * @date 2022/19/10 8:57
     */
    public PageResult<MediaFiles> queryMediaFiels(Long companyId, PageParams pageParams, QueryMediaParamsDto queryMediaParamsDto);

    /**
     * 上传文件
     *
     * @param companyId           上传文件
     * @param bytes               文件字节流数值
     * @param uploadFileParamsDto 文件信息
     * @param folder              文件目录，如果不传则默认当前日期的年、月、日作为目录
     * @param objectName          文件名称 (不传默认使用文件的md5值)
     * @return
     */
    public UploadFileResultDto uploadFile(Long companyId, byte[] bytes, UploadFileParamsDto uploadFileParamsDto, String folder, String objectName);

    /**
     * @param companyId           机构id
     * @param fileId              文件md5值
     * @param uploadFileParamsDto 文件信息
     * @param bucket_files        桶id
     * @param objectName          对象名称
     * @return
     */
    public MediaFiles addMediaFilesToDb(Long companyId, String fileId, UploadFileParamsDto uploadFileParamsDto, String bucket_files, String objectName);


    //将文件上传到minIO，传入文件绝对路径
    public void addMediaFilesToMinIO(String filePath, String bucket, String objectName);


    /**
     * 检查文件是否存在
     *
     * @param fileMd5 文件的md5值
     * @return false 不存在、 true 存在
     */
    public RestResponse<Boolean> checkFile(String fileMd5);


    /**
     * 检查分块是否存在
     *
     * @param fileMd5    文件的md5值
     * @param chunkIndex 分块序号
     * @return false 不存在、 true 存在
     */
    public RestResponse<Boolean> checkChunk(String fileMd5, int chunkIndex);

    /**
     * 上传分块
     *
     * @param fileMd5 文件md5值
     * @param chunk   分块序号
     * @param bytes   文件字节数组
     * @return
     */
    public RestResponse uploadChunk(String fileMd5, int chunk, byte[] bytes);

    /**
     * 合并分块
     *
     * @param companyId           机构id
     * @param fileMd5             文件md5值
     * @param chunkTotal          分块总合
     * @param uploadFileParamsDto 文件信息
     * @return
     */
    public RestResponse mergechunks(Long companyId,
                                    String fileMd5,
                                    int chunkTotal,
                                    UploadFileParamsDto uploadFileParamsDto);


    /**
     * 根据id查询文件信息
     *
     * @param id 文件id
     */
    public MediaFiles getFileById(String id);


    /**
     * 根据桶和文件路径从minio下载文件
     *
     * @param file       临时存储分块文件
     * @param bucket     分块存储桶
     * @param objectName 分块对象名
     * @return 从minio下载的临时文件的File对象
     */
    public File downloadFileFromMinIO(File file, String bucket, String objectName);


}
