package com.xuecheng.content.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xuecheng.content.model.dto.BindTeachplanMediaDto;
import com.xuecheng.content.model.dto.SaveTeachplanDto;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.Teachplan;
import com.xuecheng.content.model.po.TeachplanMedia;

import java.util.List;

/**
 * <p>
 * 课程计划 服务类
 * </p>
 *
 * @author itcast
 * @since 2023-01-16
 */
public interface TeachplanService extends IService<Teachplan> {

    List<TeachplanDto> findTeachplanTree(Long courseId);

    public void saveTeachplan(SaveTeachplanDto teachplan);

    public void removeTeachPlan(Long teachPlanId);

    public void moveTeachPlan(Long teachPlanId, String moveType);

    /**
     * 教学计划绑定媒资信息
     *
     * @param bindTeachplanMediaDto
     */
    public TeachplanMedia associationMedia(BindTeachplanMediaDto bindTeachplanMediaDto);

    /**
     * 删除教学计划和媒资信息的绑定
     *
     * @param teachPlanId
     * @param mediaId
     */
    public void delAssociationMedia(Long teachPlanId, String mediaId);

}
