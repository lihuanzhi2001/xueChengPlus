package com.xuecheng.checkcode.service;

import com.xuecheng.checkcode.model.CheckCodeParamsDto;
import com.xuecheng.checkcode.model.CheckCodeResultDto;

/**
 * @author lihuanzhi
 * @version 1.0
 * @description 验证码接口
 * @date 2022/11/01 15:59
 */
public interface CheckCodeService {


    /**
     * @param checkCodeParamsDto 生成验证码参数
     * @return com.xuecheng.checkcode.model.CheckCodeResultDto 验证码结果
     * @description 生成验证码
     * @author lihuanzhi
     * @date 2022/11/01 18:21
     */
    CheckCodeResultDto generate(CheckCodeParamsDto checkCodeParamsDto);

    /**
     * @param key
     * @param code
     * @return boolean
     * @description 校验验证码
     * @author lihuanzhi
     * @date 2022/11/01 18:46
     */
    public boolean verify(String key, String code);


    /**
     * @author lihuanzhi
     * @description 验证码生成器
     * @date 2022/11/01 16:34
     */
    public interface CheckCodeGenerator {
        /**
         * 验证码生成
         *
         * @return 验证码
         */
        String generate(int length);


    }

    /**
     * @author lihuanzhi
     * @description key生成器
     * @date 2022/11/01 16:34
     */
    public interface KeyGenerator {

        /**
         * key生成
         *
         * @return 验证码
         */
        String generate(String prefix);
    }


    /**
     * @author lihuanzhi
     * @description 验证码存储
     * @date 2022/11/01 16:34
     */
    public interface CheckCodeStore {

        /**
         * @param key    key
         * @param value  value
         * @param expire 过期时间,单位秒
         * @return void
         * @description 向缓存设置key
         * @author lihuanzhi
         * @date 2022/11/01 17:15
         */
        void set(String key, String value, Integer expire);

        String get(String key);

        void remove(String key);
    }
}
