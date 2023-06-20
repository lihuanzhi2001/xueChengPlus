package com.xuecheng.content.model.dto;

import com.xuecheng.content.model.po.CourseCategory;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 课程分类树型结点dto
 */
@Data
public class CourseCategoryTreeDto extends CourseCategory implements Serializable {

    List childrenTreeNodes;
}