package com.xuecheng.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.content.mapper.CourseBaseMapper;
import com.xuecheng.content.mapper.TeachplanMapper;
import com.xuecheng.content.mapper.TeachplanMediaMapper;
import com.xuecheng.content.model.dto.BindTeachplanMediaDto;
import com.xuecheng.content.model.dto.SaveTeachplanDto;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.CourseBase;
import com.xuecheng.content.model.po.Teachplan;
import com.xuecheng.content.model.po.TeachplanMedia;
import com.xuecheng.content.service.TeachplanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * <p>
 * 课程计划 服务实现类
 * </p>
 *
 * @author itcast
 */
@Slf4j
@Service
public class TeachplanServiceImpl extends ServiceImpl<TeachplanMapper, Teachplan> implements TeachplanService {

    @Autowired
    private TeachplanMapper teachplanMapper;

    @Autowired
    private CourseBaseMapper courseBaseMapper;

    @Autowired
    private TeachplanMediaMapper teachplanMediaMapper;


    @Override
    public List<TeachplanDto> findTeachplanTree(Long courseId) {


        return teachplanMapper.selectTreeNodes(courseId);
    }

    /**
     * 新增或修改课程计划
     *
     * @param teachplanDto
     */
    @Override
    public void saveTeachplan(SaveTeachplanDto teachplanDto) {

        //获取课程计划的id，如果存在则是修改操作，不存在则为新增操作
        Long teachplanId = teachplanDto.getId();
        if (teachplanId != null) {
            Teachplan teachplan = teachplanMapper.selectById(teachplanId);
            BeanUtils.copyProperties(teachplanDto, teachplan);
            teachplanMapper.updateById(teachplan);
        } else {
            //取出现有的课程计划
            int count = getTeachplanCount(teachplanDto.getCourseId(), teachplanDto.getParentid());

            Teachplan teachplanNew = new Teachplan();
            //设置排序号
            teachplanNew.setOrderby(count + 1);
            BeanUtils.copyProperties(teachplanDto, teachplanNew);

            teachplanMapper.insert(teachplanNew);
        }
    }

    /**
     * 删除课程计划
     *
     * @param teachPlanId
     */
    @Transactional
    @Override
    public void removeTeachPlan(Long teachPlanId) {
        //取出课程计划
        Teachplan teachplan = teachplanMapper.selectById(teachPlanId);

        if (teachplan == null) {
            XueChengPlusException.cast("课程不存在！");
        }

        //课程id
        Long courseId = teachplan.getCourseId();

        //课程信息
        CourseBase courseBase = courseBaseMapper.selectById(courseId);
        //获取该课程的审核状态
        String auditStatus = courseBase.getAuditStatus();

        //只有课程是未提交状态时方可删除
        if (!("202002".equals(auditStatus))) {
            XueChengPlusException.cast("删除失败，课程审核状态是未提交时方可删除！");
        }

        //判断该课程计划是否为一级
        if (teachplan.getParentid() == 0) {
            //该一级课程计划没有子课程计划才能删除
            LambdaQueryWrapper<Teachplan> teachplanLambdaQueryWrapper = new LambdaQueryWrapper<>();
            teachplanLambdaQueryWrapper.eq(Teachplan::getParentid, teachPlanId);
            Integer twoTeachplanCountById = teachplanMapper.selectCount(teachplanLambdaQueryWrapper);
            if (twoTeachplanCountById > 0) {
                XueChengPlusException.cast("课程计划信息还有子级信息，无法删除");
            }

        } else {
            //查询课程计划关联的媒资信息表
            LambdaQueryWrapper<TeachplanMedia> teachplanMediaLambdaQueryWrapper = new LambdaQueryWrapper<>();
            teachplanMediaLambdaQueryWrapper.eq(TeachplanMedia::getTeachplanId, teachplan.getId());
            TeachplanMedia teachplanMedia = teachplanMediaMapper.selectOne(teachplanMediaLambdaQueryWrapper);
            if (teachplanMedia != null) {
                //删除课程计划关联的媒资信息
                teachplanMediaMapper.delete(teachplanMediaLambdaQueryWrapper);
            }
        }

        //删除课程计划
        teachplanMapper.deleteById(teachPlanId);


    }

    /**
     * 移动课程计划排序
     *
     * @param teachPlanId 课程计划id
     * @param moveType    上移 或 下移
     */
    @Transactional
    @Override
    public void moveTeachPlan(Long teachPlanId, String moveType) {
        //查询课程计划
        Teachplan teachplan = teachplanMapper.selectById(teachPlanId);

        //查询同级别的课程计划
        LambdaQueryWrapper<Teachplan> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Teachplan::getParentid, teachplan.getParentid());
        queryWrapper.eq(Teachplan::getCourseId, teachplan.getCourseId());

        List<Teachplan> teachplans = teachplanMapper.selectList(queryWrapper);

        //判断同级别的课程计划如果只有一个那么什么也不做
        if (teachplans.size() <= 1) {
            return;
        }

        //根据移动类型进行排序
        /**
         * 原理：
         * 上移：降序排序后，下一个就是他要交换顺序的位置
         * 下移：升序排序后，下一个就是他要交换顺序的位置
         */
        if ("moveup".equals(moveType)) {//上移
            //降序
            Collections.sort(teachplans, (o1, o2) -> o2.getOrderby() - o1.getOrderby());
        } else {
            //升序
            Collections.sort(teachplans, (o1, o2) -> o1.getOrderby() - o2.getOrderby());
        }

        //找到当前课程计划
        Teachplan one = null;
        Teachplan two = null;
        Iterator<Teachplan> iterator = teachplans.iterator();
        while (iterator.hasNext()) {
            Teachplan next = iterator.next();
            boolean b = next.getId().equals(teachplan.getId());
            if (next.getId().equals(teachplan.getId())) {
                one = next;
                //已经找到了当前课程计划，再找当前计划的下一个，如果不存在则不进行任何操作 或者抛出异常
//                if(iterator.hasNext()){
//                    two = iterator.next();
//                }
                try {
                    two = iterator.next();
                } catch (Exception e) {
                    //如果two无法获得则报出无法移动的报错
                    XueChengPlusException.cast("无法移动！");
                }

            }
        }
        swapTeachplan(one, two);


    }


    /**
     * 教学计划绑定媒资信息
     *
     * @param bindTeachplanMediaDto
     */
    @Transactional
    @Override
    public TeachplanMedia associationMedia(BindTeachplanMediaDto bindTeachplanMediaDto) {

        //判断教学计划存不存在
        Long teachplanId = bindTeachplanMediaDto.getTeachplanId();
        Teachplan teachplan = teachplanMapper.selectById(teachplanId);
        if (teachplan == null) {
            log.debug("教学计划不存在，课程计划id: {}", teachplanId);
            XueChengPlusException.cast("教学计划不存在");
        }

        //TODO 要不要判断媒资信息是否存在呢？
        //判断媒资信息存在不存在


        //判断教学计划是不是二级目录，只有二级教学计划可以绑定视频
        Integer grade = teachplan.getGrade();
        if (grade != 2) {
            XueChengPlusException.cast("只允许2级课程计划绑定媒资信息");
        }

        //先删除原有的教学计划绑定的媒资信息
        LambdaQueryWrapper<TeachplanMedia> teachplanMediaLambdaQueryWrapper = new LambdaQueryWrapper<>();
        teachplanMediaLambdaQueryWrapper.eq(TeachplanMedia::getTeachplanId, teachplan.getId());
        teachplanMediaMapper.delete(teachplanMediaLambdaQueryWrapper);


        //在添加教学计划与媒资的绑定信息
        TeachplanMedia teachplanMedia = new TeachplanMedia();
        teachplanMedia.setCourseId(teachplan.getCourseId());
        teachplanMedia.setCreateDate(LocalDateTime.now());
        teachplanMedia.setMediaId(bindTeachplanMediaDto.getMediaId());
        teachplanMedia.setMediaFilename(bindTeachplanMediaDto.getFileName());
        teachplanMedia.setTeachplanId(teachplanId);

        teachplanMediaMapper.insert(teachplanMedia);


        return teachplanMedia;
    }


    /**
     * 删除教学计划和媒资信息的绑定
     *
     * @param teachPlanId
     * @param mediaId
     */
    @Override
    public void delAssociationMedia(Long teachPlanId, String mediaId) {
        LambdaQueryWrapper<TeachplanMedia> teachplanMediaLambdaQueryWrapper = new LambdaQueryWrapper<>();
        teachplanMediaLambdaQueryWrapper.eq(TeachplanMedia::getTeachplanId, teachPlanId);
        teachplanMediaLambdaQueryWrapper.eq(TeachplanMedia::getMediaId, mediaId);
        teachplanMediaMapper.delete(teachplanMediaLambdaQueryWrapper);
    }

    /**
     * 前一个和后一个课程计划互换位置（只需要互换他们的orderby就行了）
     *
     * @param previous 前一个课程计划
     * @param next     后一个课程计划
     */
    public void swapTeachplan(Teachplan previous, Teachplan next) {
        Integer previousOrderby = previous.getOrderby();
        Integer nextOrderby = next.getOrderby();

        previous.setOrderby(nextOrderby);
        next.setOrderby(previousOrderby);

        teachplanMapper.updateById(previous);
        teachplanMapper.updateById(next);
        log.debug("课程计划交换位置，previous:{},next:{}", previous.getId(), next.getId());
    }


    /**
     * 获取最新的排序号
     *
     * @param courseId 课程id
     * @param parentid 父课程计划id
     * @return 最新排序号
     */
    private int getTeachplanCount(Long courseId, Long parentid) {
        LambdaQueryWrapper<Teachplan> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Teachplan::getCourseId, courseId);
        queryWrapper.eq(Teachplan::getParentid, parentid);

        Integer count = teachplanMapper.selectCount(queryWrapper);

        return count;
    }
}
