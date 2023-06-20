package com.xuecheng.content.feignclient;

import feign.hystrix.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
public class MediaServiceClientFallbackFactory implements FallbackFactory<MediaServiceClient> {

    //使用FallbackFactory可用获取异常信息
    @Override
    public MediaServiceClient create(Throwable throwable) {
        return new MediaServiceClient() {
            @Override
            public String upload(MultipartFile upload, String folder, String objectName) {
                //降级方法
                log.debug("使用媒资管理服务上传文件时异常，{}", throwable.getMessage());
                return null;
            }
        };
    }
}
