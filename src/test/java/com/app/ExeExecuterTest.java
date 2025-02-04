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
        final String ccFile = "E:\\JavaProject\\TransportTranslationVideo\\temp\\1_DeepSeek_Panic__US_vs_China__OpenAI__40B__and_Doge_Delivers_with_Travis_Kalanick_and_David_Sacks.srt";
        final String videoFile = "E:\\JavaProject\\TransportTranslationVideo\\temp\\1_DeepSeek_Panic__US_vs_China__OpenAI__40B__and_Doge_Delivers_with_Travis_Kalanick_and_David_Sacks.mp4";
        final EditVideoUtil editVideoUtil = new EditVideoUtil();
        editVideoUtil.genCCFile(videoFile, ccFile);
    }


}
