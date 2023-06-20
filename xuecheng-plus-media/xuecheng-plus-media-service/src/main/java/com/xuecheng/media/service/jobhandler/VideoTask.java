package com.xuecheng.media.service.jobhandler;

import com.xuecheng.base.utils.Mp4VideoUtil;
import com.xuecheng.media.model.po.MediaProcess;
import com.xuecheng.media.service.MediaFileService;
import com.xuecheng.media.service.MediaProcessService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * XxlJob开发示例（Bean模式）
 * <p>
 * 开发步骤：
 * 1、任务开发：在Spring Bean实例中，开发Job方法；
 * 2、注解配置：为Job方法添加注解 "@XxlJob(value="自定义jobhandler名称", init = "JobHandler初始化方法", destroy = "JobHandler销毁方法")"，注解value值对应的是调度中心新建任务的JobHandler属性的值。
 * 3、执行日志：需要通过 "XxlJobHelper.log" 打印执行日志；
 * 4、任务结果：默认任务结果为 "成功" 状态，不需要主动设置；如有诉求，比如设置任务结果为失败，可以通过 "XxlJobHelper.handleFail/handleSuccess" 自主设置任务结果；
 *
 * @author lihuanzhi
 */
@Slf4j
@Component
public class VideoTask {

    @Autowired
    private MediaProcessService mediaProcessService;

    @Autowired
    private MediaFileService mediaFileService;

    //ffmpeg文件路径
    @Value("${videoprocess.ffmpegpath}")
    private String ffmpeg_path;


    /**
     * 视频处理任务
     */
    @XxlJob("videoJobHandler")
    public void videoJobHandler() throws Exception {

        // 分片参数
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();

        //查询待处理任务
        List<MediaProcess> mediaProcessList = mediaProcessService.getMediaProcessList(shardTotal, shardIndex, 2);
        if (mediaProcessList == null || mediaProcessList.size() <= 0) {
            log.debug("查询到的待处理任务数量为0");
            return;
        }

        //要处理的任务数
        int size = mediaProcessList.size();
        //创建size数量个线程的线程池
        ExecutorService threadPool = Executors.newFixedThreadPool(size);
        //计数器
        CountDownLatch countDownLatch = new CountDownLatch(size);


        //遍历mediaProcessList，将任务放入线程池
        mediaProcessList.forEach(mediaProcess -> {
            threadPool.execute(() -> {
                //任务的执行逻辑
                String status = mediaProcess.getStatus();
                if ("2".equals(status)) {
                    log.debug("视频已经处理不用再次处理，视频信息: {}", mediaProcess);
                    countDownLatch.countDown();     //计数器-1
                    return;
                }

                //桶
                String bucket = mediaProcess.getBucket();
                //存储路径
                String filePath = mediaProcess.getFilePath();
                //原始视频md5值
                String fileId = mediaProcess.getFileId();
                //原始文件名称
                String filename = mediaProcess.getFilename();

                //创建临时文件夹保存minIO下载待处理文件
                File orginalFile = null;
                //处理结束文件
                File mp4File = null;
                try {
                    orginalFile = File.createTempFile("original", null);
                    mp4File = File.createTempFile("mp4", ".mp4");
                } catch (IOException e) {
                    log.debug("处理视频文件前创建临时文件出错");
                    countDownLatch.countDown();     //计数器-1
                    return;
                }

                //将原始视频下载到本地
                try {
                    mediaFileService.downloadFileFromMinIO(orginalFile, bucket, filePath);
                } catch (Exception e) {
                    log.debug("下载原始视频到本地出错: {}, 文件信息: {}", e.getMessage(), mediaProcess);
                    countDownLatch.countDown();     //计数器-1
                    return;
                }

                //调用工具将avi视频转成mp4
                //源avi视频的路径
                String video_path = orginalFile.getAbsolutePath();
                //转换后mp4文件的名称
                String mp4_name = fileId + ".mp4";
                //转换后mp4文件的路径
                String mp4_path = mp4File.getAbsolutePath();
                //创建工具类对象
                Mp4VideoUtil videoUtil = new Mp4VideoUtil(ffmpeg_path, video_path, mp4_name, mp4_path);

                //开始视频转换，成功将返回success, （可能会出错？）
                /**
                 * TODO Bug未处理
                 * 这个方法在处理任务的时候如果传过来的文件只是后缀为视频格式 (如: MP4,avi)
                 * 但并不是视频就会报错，需要对这个方法进行异常捕获处理。
                 * 可以删除这个异常待处理任务，如果不删除下次执行的时候还是会报错
                 */
                String result = videoUtil.generateMp4();

                String statusNew = "3";
                String url = null;

                if ("success".equals(result)) {
                    //转换成功

                    //将转码后的视频上传到minIO
                    String objectName = getFilePathByMd5(fileId, ".mp4");
                    try {
                        mediaFileService.addMediaFilesToMinIO(mp4_path, bucket, objectName);
                    } catch (Exception e) {
                        log.debug("上传文件出错: {}", e.getMessage());
                        countDownLatch.countDown();     //计数器-1
                        return;
                    }

                    //“2” 成功
                    statusNew = "2";
                    url = "/" + bucket + "/" + objectName;

                }

                try {
                    //记录处理结果
                    mediaProcessService.saveProcessFinishStatus(mediaProcess.getId(), statusNew, fileId, url, result);
                } catch (Exception e) {
                    log.debug("保存任务处理结果出错: {}", e.getMessage());
                    countDownLatch.countDown();     //计数器-1
                    return;
                }

                //处理完了一个任务, 计数器-1
                countDownLatch.countDown();     //计数器-1

            });
        });

        //阻塞到任务执行完成（为什么？）
        //当计数器归零，整个阻塞解除
        countDownLatch.await(4, TimeUnit.MINUTES);


    }


    //根据md5值得到文件的完成路径（md5.mp4）
    private String getFilePathByMd5(String fileMd5, String fileExt) {
        return fileMd5.substring(0, 1) + "/" + fileMd5.substring(1, 2) + "/" + fileMd5 + "/" + fileMd5 + fileExt;
    }


}
