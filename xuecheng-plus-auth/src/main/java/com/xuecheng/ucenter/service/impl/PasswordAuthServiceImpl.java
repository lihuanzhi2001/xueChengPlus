package com.xuecheng.ucenter.service.impl;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.ucenter.feignclient.CheckCodeClient;
import com.xuecheng.ucenter.mapper.XcUserMapper;
import com.xuecheng.ucenter.model.dto.AuthParamsDto;
import com.xuecheng.ucenter.model.dto.XcUserExt;
import com.xuecheng.ucenter.model.po.XcUser;
import com.xuecheng.ucenter.service.AuthService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 账号密码认证
 */
@Service("password_authservice")
public class PasswordAuthServiceImpl implements AuthService {

    @Autowired
    private XcUserMapper xcUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CheckCodeClient checkCodeClient;

    /**
     * 执行账号密码校验逻辑
     *
     * @param authParamsDto 认证参数
     * @return
     */
    @Override
    public XcUserExt execute(AuthParamsDto authParamsDto) {

        //验证码校验
        String checkcode = authParamsDto.getCheckcode();
        String checkcodekey = authParamsDto.getCheckcodekey();

        if (StringUtils.isBlank(checkcodekey) || StringUtils.isBlank(checkcode)) {
            throw new RuntimeException("验证码为空");

        }
        Boolean verify = checkCodeClient.verify(checkcodekey, checkcode);
        if (!verify) {
            throw new RuntimeException("验证码输入错误");
        }
        //校验验证码服务熔断
        if (verify == null) {
            throw new RuntimeException("服务器出了点小差，请稍后重试");
        }


        //账号密码校验
        String username = authParamsDto.getUsername();

        LambdaQueryWrapper<XcUser> xcUserLambdaQueryWrapper = new LambdaQueryWrapper<>();
        xcUserLambdaQueryWrapper.eq(XcUser::getUsername, username);

        XcUser xcUser = xcUserMapper.selectOne(xcUserLambdaQueryWrapper);
        if (xcUser == null) {
            //账号不存在
            throw new RuntimeException("账号不存在！");
        }

        //校验密码
        String passwordDb = xcUser.getPassword();   //数据库正确密码（已加密）
        String passwordInput = authParamsDto.getPassword(); //传入密码（明文）
        boolean matches = passwordEncoder.matches(passwordInput, passwordDb);

        if (!matches) {
            throw new RuntimeException("账号或密码不正确");
        }

        XcUserExt xcUserExt = new XcUserExt();
        BeanUtils.copyProperties(xcUser, xcUserExt);

        return xcUserExt;
    }
}
