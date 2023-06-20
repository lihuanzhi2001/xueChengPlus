package com.xuecheng.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.model.PageParams;
import com.xuecheng.base.model.PageResult;
import com.xuecheng.content.mapper.CourseBaseMapper;
import com.xuecheng.content.mapper.CourseCategoryMapper;
import com.xuecheng.content.mapper.CourseMarketMapper;
import com.xuecheng.content.model.dto.AddCourseDto;
import com.xuecheng.content.model.dto.CourseBaseInfoDto;
import com.xuecheng.content.model.dto.EditCourseDto;
import com.xuecheng.content.model.dto.QueryCourseParamsDto;
import com.xuecheng.content.model.po.CourseBase;
import com.xuecheng.content.model.po.CourseCategory;
import com.xuecheng.content.model.po.CourseMarket;
import com.xuecheng.content.service.CourseBaseInfoService;
import com.xuecheng.content.service.CourseMarketService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CourseBaseInfoServiceImpl implements CourseBaseInfoService {

    @Autowired
    private CourseBaseMapper courseBaseMapper;

    @Autowired
    private CourseMarketMapper courseMarketMapper;

    @Autowired
    private CourseCategoryMapper courseCategoryMapper;

    @Autowired
    private CourseMarketService marketService;


    /**
     * 课程查询接口
     *
     * @param pageParams           分页参数
     * @param queryCourseParamsDto 查询条件
     * @return
     */
    @Override
    public PageResult<CourseBase> queryCourseBaseList(PageParams pageParams, QueryCourseParamsDto queryCourseParamsDto) {

        LambdaQueryWrapper<CourseBase> lambdaQueryWrapper = new LambdaQueryWrapper<>();


        //根据课程名称查询
        lambdaQueryWrapper.like(StringUtils.isNotEmpty(queryCourseParamsDto.getCourseName()),
                CourseBase::getName,
                queryCourseParamsDto.getCourseName());

        //根据审核状态查询
        lambdaQueryWrapper.eq(StringUtils.isNotEmpty(queryCourseParamsDto.getAuditStatus()),
                CourseBase::getAuditStatus,
                queryCourseParamsDto.getAuditStatus());

        //根据发布状态查询
        lambdaQueryWrapper.eq(StringUtils.isNotEmpty(queryCourseParamsDto.getPublishStatus()),
                CourseBase::getStatus, queryCourseParamsDto.getPublishStatus());

        //设置分页参数
        Page<CourseBase> page = new Page<>(pageParams.getPageNo(), pageParams.getPageSize());

        Page<CourseBase> courseBasePage = courseBaseMapper.selectPage(page, lambdaQueryWrapper);

        //查询内容
        List<CourseBase> courseBaseList = courseBasePage.getRecords();
        long total = courseBasePage.getTotal();

        //构建返回结果对象
        PageResult<CourseBase> courseBasePageResult = new PageResult<>(courseBaseList, total,
                pageParams.getPageNo(), pageParams.getPageSize());

        return courseBasePageResult;
    }


    /**
     * 新增课程接口
     *
     * @param companyId 机构id
     * @param dto       课程的基本信息和营销信息
     * @return 课程的基本信息和营销信息
     */
    @Transactional
    @Override
    public CourseBaseInfoDto createCourseBase(Long companyId, AddCourseDto dto) {

        //课程基本信息对象
        CourseBase courseBaseNew = new CourseBase();
        BeanUtils.copyProperties(dto, courseBaseNew);
        //设置审核状态和发布状态（默认为 未提交 和 未发布 ）
        courseBaseNew.setAuditStatus("202002");
        courseBaseNew.setStatus("203001");
        //设置机构id
        courseBaseNew.setCompanyId(companyId);
        //添加时间 为当前时间
        courseBaseNew.setCreateDate(LocalDateTime.now());
        //插入课程基本信息
        int insert = courseBaseMapper.insert(courseBaseNew);
        Long baseNewId = courseBaseNew.getId();


        //课程营销信息对象
        CourseMarket courseMarketNew = new CourseMarket();
        BeanUtils.copyProperties(dto, courseMarketNew);
        //课程课程营销信息的id
        courseMarketNew.setId(baseNewId);

        //插入课程营销信息
        int insert1 = saveCourseMarket(courseMarketNew);


        //如果 insert 和 insert1 都 <= 0 则事务回滚
        if (insert <= 0 || insert1 <= 0) {
            throw new XueChengPlusException("添加课程失败");
        }

        //查询课程基本信息和营销信息封装并返回


        return getCourseBaseInfo(baseNewId);
    }


    //根据课程id查询课程基本信息，包括基本信息和营销信息
    public CourseBaseInfoDto getCourseBaseInfo(long courseId) {

        CourseBase courseBase = courseBaseMapper.selectById(courseId);
        CourseMarket courseMarket = courseMarketMapper.selectById(courseId);

        //如果课程不存在直接返回null
        if (courseBase == null) {
            return null;
        }
        CourseBaseInfoDto courseBaseInfoDto = new CourseBaseInfoDto();
        BeanUtils.copyProperties(courseBase, courseBaseInfoDto);

        //如果课程营销信息为null则不就行bean拷贝
        if (courseMarket != null) {
            BeanUtils.copyProperties(courseMarket, courseBaseInfoDto);
        }

        //根据分类编号查询分类名称
        String mt = courseBaseInfoDto.getMt();
        String st = courseBaseInfoDto.getSt();

        CourseCategory mtCourseCategory = courseCategoryMapper.selectById(mt);
        CourseCategory stCourseCategory = courseCategoryMapper.selectById(st);

        courseBaseInfoDto.setMtName(mtCourseCategory.getName());
        courseBaseInfoDto.setStName(stCourseCategory.getName());

        return courseBaseInfoDto;
    }

    /**
     * 修改课程
     *
     * @param companyId
     * @param editCourseDto
     * @return
     */
    @Transactional
    @Override
    public CourseBaseInfoDto updateCourseBase(Long companyId, EditCourseDto editCourseDto) {

        //课程id
        long id = editCourseDto.getId();

        CourseBase courseBase = courseBaseMapper.selectById(id);
        //校验课程是否存在
        if (courseBase == null) {
            XueChengPlusException.cast("课程不存在！");
        }

        //校验是否为本机构课程
        if (!courseBase.getCompanyId().equals(companyId)) {
            XueChengPlusException.cast("本机构只能修改本机构的课程!");
        }

        //设置更新时间
        courseBase.setChangeDate(LocalDateTime.now());
        //更新课程基本信息
        BeanUtils.copyProperties(editCourseDto, courseBase);
        int courseInfoUpdateCode = courseBaseMapper.updateById(courseBase);

        //查询营销信息
        CourseMarket courseMarketUpdate = courseMarketMapper.selectById(id);
        if (courseMarketUpdate == null) {
            courseMarketUpdate = new CourseMarket();
        }
        BeanUtils.copyProperties(editCourseDto, courseMarketUpdate);

        //保存课程营销信息，没有则添加，有则更新
        int courseMarketUpdateCode = saveCourseMarket(courseMarketUpdate);

        //同时修改成功才能成功修改课程信息及其关联的营销信息
        if (courseInfoUpdateCode <= 0 || courseMarketUpdateCode <= 0) {
            XueChengPlusException.cast("修改课程失败，请稍后重试!");
        }


        return getCourseBaseInfo(id);
    }

    public int saveCourseMarket(CourseMarket courseMarket) {
        //校验收费规则是否填写
        String charge = courseMarket.getCharge();
        if (StringUtils.isBlank(charge)) {
            XueChengPlusException.cast("收费规则必须填写");
        }


        //如果课程是收费但未填写价格或价格为0则抛出异常
        if (courseMarket.getCharge().equals("201001")) {
            if (courseMarket.getPrice() == null || courseMarket.getPrice().floatValue() <= 0) {
                throw new XueChengPlusException("课程为收费，但价格填写异常!");
            }
        }

        boolean b = marketService.saveOrUpdate(courseMarket);
        return b?1:0;
    }


}
