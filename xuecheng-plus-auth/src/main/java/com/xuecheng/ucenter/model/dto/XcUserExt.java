package com.xuecheng.ucenter.model.dto;

import com.xuecheng.ucenter.model.po.XcUser;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lihuanzhi
 * @version 1.0
 * @description 用户扩展信息
 * @date 2022/10/30 13:56
 */
@Data
public class XcUserExt extends XcUser {
    //用户权限
    List<String> permissions = new ArrayList<>();
}
