package com.xuecheng.content.api;

import com.xuecheng.content.model.dto.BindTeachplanMediaDto;
import com.xuecheng.content.model.dto.SaveTeachplanDto;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.service.TeachplanService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(value = "课程计划管理相关的接口", tags = "课程计划管理相关的接口")
@RestController
public class TeachPlanController {

    @Autowired
    private TeachplanService teachplanService;


    @ApiOperation("查询课程计划树形结构")
    @ApiImplicitParam(value = "courseId", name = "课程基础Id值",
            required = true, dataType = "Long", paramType = "path")
    @GetMapping("/teachplan/{courseId}/tree-nodes")
    public List<TeachplanDto> getTreeNodes(@PathVariable Long courseId) {
        List<TeachplanDto> teachplanDtoTreeNodes = teachplanService.findTeachplanTree(courseId);
        return teachplanDtoTreeNodes;
    }

    @ApiOperation("新增或修改课程计划")
    @PostMapping("/teachplan")
    public void saveTeachplan(@RequestBody SaveTeachplanDto teachplan) {
        teachplanService.saveTeachplan(teachplan);
    }

    @ApiOperation(value = "删除课程计划")
    @DeleteMapping("/teachplan/{teachplanId}")
    public void removeTeachPlan(@PathVariable Long teachplanId) {
        teachplanService.removeTeachPlan(teachplanId);
    }

    @ApiOperation(value = "移动课程计划")
    @PostMapping("teachplan/{moveType}/{teachplanId}")
    public void moveTeachPlan(@PathVariable String moveType, @PathVariable Long teachplanId) {
        teachplanService.moveTeachPlan(teachplanId, moveType);
    }

    @ApiOperation(value = "课程计划和媒资信息绑定")
    @PostMapping("/teachplan/association/media")
    public void associationMedia(@RequestBody BindTeachplanMediaDto bindTeachplanMediaDto) {
        teachplanService.associationMedia(bindTeachplanMediaDto);
    }

    @ApiOperation(value = "课程计划和媒资信息解除绑定")
    @DeleteMapping("/teachplan/association/media/{teachPlanId}/{mediaId}")
    public void delAssociationMedia(@PathVariable Long teachPlanId, @PathVariable String mediaId) {
        teachplanService.delAssociationMedia(teachPlanId, mediaId);
    }

}
