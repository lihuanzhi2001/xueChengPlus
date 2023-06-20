package com.xuecheng.media.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.media.mapper.MediaFilesMapper;
import com.xuecheng.media.mapper.MediaProcessHistoryMapper;
import com.xuecheng.media.mapper.MediaProcessMapper;
import com.xuecheng.media.model.po.MediaFiles;
import com.xuecheng.media.model.po.MediaProcess;
import com.xuecheng.media.model.po.MediaProcessHistory;
import com.xuecheng.media.service.MediaProcessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class MediaProcessServiceImpl implements MediaProcessService {

    @Autowired
    private MediaProcessMapper mediaProcessMapper;

    @Autowired
    private MediaProcessHistoryMapper mediaProcessHistoryMapper;

    @Autowired
    private MediaFilesMapper mediaFilesMapper;


    /**
     * 获取待处理任务
     *
     * @param shardTotal 分片总数
     * @param shardIndex 分片序号
     * @param count      获取记录数
     */
    @Override
    public List<MediaProcess> getMediaProcessList(int shardTotal, int shardIndex, int count) {

        return mediaProcessMapper.selectListByShardIndex(shardTotal, shardIndex, count);
    }

    /**
     * 将url存储至数据，并更新状态为成功，并将待处理视频记录删除存入历史
     *
     * @param taskId   待处理任务id
     * @param status   处理结果，2:成功3失败
     * @param fileId   文件id
     * @param url      文件访问url
     * @param errorMsg 失败原因
     */
    @Transactional
    @Override
    public void saveProcessFinishStatus(Long taskId, String status, String fileId, String url, String errorMsg) {

        //查询这个任务
        MediaProcess mediaProcess = mediaProcessMapper.selectById(taskId);
        if (mediaProcess == null) {
            log.debug("更新任务的状态时此任务不存在: {}", taskId);
            return;
        }

        //判断任务成功还是失败
        if ("3".equals(status)) {
            //任务失败
            MediaProcess mediaProcess_u = new MediaProcess();
            mediaProcess_u.setStatus("3");

            LambdaQueryWrapper<MediaProcess> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(MediaProcess::getId, taskId);

            mediaProcessMapper.update(mediaProcess_u, lambdaQueryWrapper);
            return;
        }
        if ("2".equals(status)) {
            mediaProcess.setStatus("2");
            mediaProcess.setUrl(url);
            mediaProcess.setFinishDate(LocalDateTime.now());
            mediaProcessMapper.updateById(mediaProcess);

            //更新文件表的url字段
            MediaFiles mediaFiles = mediaFilesMapper.selectById(fileId);
            mediaFiles.setUrl(url);
            mediaFilesMapper.updateById(mediaFiles);
        }


        //如果处理成功将任务添加到历史记录表
        MediaProcessHistory mediaProcessHistory = new MediaProcessHistory();
        BeanUtils.copyProperties(mediaProcess, mediaProcessHistory);
        mediaProcessHistoryMapper.insert(mediaProcessHistory);


        //如果处理成功则将待处理表的记录删除
        mediaProcessMapper.deleteById(taskId);
    }
}
