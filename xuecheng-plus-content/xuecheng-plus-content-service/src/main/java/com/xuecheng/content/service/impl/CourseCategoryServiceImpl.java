package com.xuecheng.content.service.impl;

import com.xuecheng.content.mapper.CourseCategoryMapper;
import com.xuecheng.content.model.dto.CourseCategoryTreeDto;
import com.xuecheng.content.service.CourseCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
public class CourseCategoryServiceImpl implements CourseCategoryService {

    @Autowired
    private CourseCategoryMapper courseCategoryMapper;

    /**
     * 查询课程分类
     *
     * @param id 根节点id
     * @return 根节点及其所有下属结点
     */
    @Override
    public List<CourseCategoryTreeDto> selectTreeNodes(String id) {

        // "1" 根节点
        List<CourseCategoryTreeDto> categoryTreeDtos = courseCategoryMapper.selectTreeNodes("1");

        //树化后的课程分类集合
        List<CourseCategoryTreeDto> courseCategoryTreeDtos = new ArrayList<>();

        //存放根节点的map集合
        HashMap<String, CourseCategoryTreeDto> nodeMap = new HashMap<>();

        categoryTreeDtos.stream().forEach(item -> {
            nodeMap.put(item.getId(), item);
            //判断是否为根节点, 如果是则存放到map集合
//            if(item.getParentid() == id){
            if (item.getParentid().equals(id)) {
                courseCategoryTreeDtos.add(item);
            }

            //找到子节点的父节点
            String parentid = item.getParentid();
            CourseCategoryTreeDto parentNode = nodeMap.get(parentid);

            //把子节点放到父节点的children...中
            //先判断parentNode有没有再获取childrenTreeNodes, 否则会报空指针异常
            if (parentNode != null) {
                if (parentNode.getChildrenTreeNodes() == null) {
                    parentNode.setChildrenTreeNodes(new ArrayList<CourseCategoryTreeDto>());
                }
                parentNode.getChildrenTreeNodes().add(item);

            }


        });

        return courseCategoryTreeDtos;
    }
}
