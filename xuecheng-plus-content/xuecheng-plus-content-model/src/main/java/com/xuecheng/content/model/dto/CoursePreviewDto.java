package com.xuecheng.content.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class CoursePreviewDto {

    //课程基本信息
    private CourseBaseInfoDto courseBase;

    //课程计划信息
    private List<TeachplanDto> teachplans;

    //TODO 完善师资信息的接口服务
    //师资信息...


}
