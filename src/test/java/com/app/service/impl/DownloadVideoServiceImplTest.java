package com.app.service.impl;

import com.app.AppContext;
import com.app.Main;
import com.app.pojo.DownloadVideo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

/**
 * @Author guofan
 * @Create 2022/10/15
 */
public class DownloadVideoServiceImplTest {
//    @Test
    public void serviceTest() {

        DownloadVideoServiceImpl bean = AppContext.getBean(DownloadVideoServiceImpl.class);
        List<DownloadVideo> list = bean.list();
        Logger logger = LoggerFactory.getLogger(getClass());
        logger.info("测试");
    }

}
