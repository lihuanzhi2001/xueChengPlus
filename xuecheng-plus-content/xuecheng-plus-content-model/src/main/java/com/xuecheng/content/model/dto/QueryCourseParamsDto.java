package com.xuecheng.content.model.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 查询体
 */
@Data
public class QueryCourseParamsDto {

    @ApiModelProperty("审核状态")
    //审核状态
    private String auditStatus;
    @ApiModelProperty("课程名称")
    //课程名称
    private String courseName;
    @ApiModelProperty("发布状态")
    //发布状态
//    private String status;
    private String publishStatus;

}
