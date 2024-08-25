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


    @Test
    public void testExecute() {
        logger.info("测试");
        final String ccFile = "C:\\视频\\项目\\20231219Elon_Musk_Opens_Up_in_an_Interview,_Leaves_The_Audience_Speechless\\2_Elon_Musk_Opens_Up_in_an_Interview__Leaves_The_Audience_Speechless.srt";
        final String videoFile = "C:\\视频\\项目\\20231219Elon_Musk_Opens_Up_in_an_Interview,_Leaves_The_Audience_Speechless\\2_Elon_Musk_Opens_Up_in_an_Interview__Leaves_The_Audience_Speechless.mp4";
        final EditVideoUtil editVideoUtil = new EditVideoUtil();
        editVideoUtil.genCCFile(videoFile, ccFile);
    }


}
