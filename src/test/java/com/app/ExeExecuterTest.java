package com.app;

import com.app.util.EditVideoUtil;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;


/**
 * @Author guofan
 * @Create 2024/8/13
 */
public class ExeExecuterTest {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 生成字母测试
     * @throws Exception 异常
     */
    @Test
    public void testExecute() throws Exception {
        logger.info("测试");
        final String ccFile = "E:\\JavaProject\\TransportTranslationVideo\\temp\\test.ass";
        final String videoFile = "E:\\JavaProject\\TransportTranslationVideo\\temp\\test.mp4";
        final EditVideoUtil editVideoUtil = new EditVideoUtil();
        editVideoUtil.genCCFile(videoFile, ccFile);
    }


}
