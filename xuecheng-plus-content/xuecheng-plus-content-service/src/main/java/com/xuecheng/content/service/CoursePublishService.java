package com.xuecheng.content.service;

import com.xuecheng.content.model.dto.CoursePreviewDto;

import java.io.File;

/**
 * 课程预览、发布接口
 */
public interface CoursePublishService {

    /**
     * 获取课程预览信息
     *
     * @param courseId 课程id
     * @return
     */
    public CoursePreviewDto getCoursePreviewInfo(Long courseId);

    /**
     * 提交审核
     *
     * @param companyId 机构id
     * @param courseId  课程id
     * @return
     */
    public void commitAudit(Long companyId, Long courseId);

    //课程发布
    public void publish(Long companyId, Long courseId);


    /**
     * 生成静态课程html页面
     *
     * @param courseId 课程id
     * @return
     */
    public File generateCourseHtml(Long courseId);


    /**
     * 上传课程静态化页面
     *
     * @param file 静态化文件
     */
    public void uploadCourseHtml(Long courseId, File file);

    /**
     * 创建课程信息的索引
     *
     * @param courseId 课程id
     */
    public Boolean saveCourseIndex(Long courseId);

}
