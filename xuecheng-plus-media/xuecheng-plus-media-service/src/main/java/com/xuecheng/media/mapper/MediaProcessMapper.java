package com.xuecheng.media.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuecheng.media.model.po.MediaProcess;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author lihuanzhi
 */
public interface MediaProcessMapper extends BaseMapper<MediaProcess> {
    /**
     * 根据分片参数获取待处理任务
     *
     * @param shardTotal 分片总数
     * @param shardindex 分片序号
     * @param count      任务数
     */
    List<MediaProcess> selectListByShardIndex(@Param("shardTotal") int shardTotal,
                                              @Param("shardindex") int shardindex,
                                              @Param("count") int count);
}
