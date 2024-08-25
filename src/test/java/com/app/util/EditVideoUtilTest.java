package com.app.util;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * 视频编辑测试文件
 *
 * @Author guofan
 * @Create 2022/8/28
 */
class EditVideoUtilTest {
    Logger logger = LoggerFactory.getLogger(getClass());
    public EditVideoUtil editVideoUtil = new EditVideoUtil();

    /**
     * 压制字幕视频
     */
//    @Test
    void editSubtitleVideo() {
        File videoFile = new File("c:/视频/项目/20220809密码/秘密.mp4");
        File ccFile = new File("c:/视频/项目/20220809密码/秘密.srt");
        File subtitles = editVideoUtil.encodedSubtitles(videoFile, ccFile);
//        File subtitles = editVideoUtil.encodedSubtitles("/视频/项目/20220809密码/秘密.mp4", "/视频/项目/20220809密码/秘密.srt");

    }

    /**
     * 播放视频测试
     */
//    @Test
    void testPlayVideo() {
        editVideoUtil.playVideo("C:\\ffmpeg\\bin\\heginning.webm");
    }

//    @Test
    void mergeVideosTest() {
        File beginVideo = new File("C:\\ffmpeg\\bin\\1.ts");
        File contentVideo = new File("C:\\ffmpeg\\bin\\content.mp4");
        File resultVideo = editVideoUtil.mergeVideos(beginVideo, contentVideo);
    }

    /**
     * webm转换MP4
     */
//    @Test
    void transitionTest() {
        String webmToMp4 = editVideoUtil.webmToMp4("C:\\Users\\fan\\Downloads\\heginning (3).webm");
        String videoAddAudio = editVideoUtil.videoAddAudio(webmToMp4, "C:\\ffmpeg\\bin\\m.mp3");
        String toTs = editVideoUtil.mp4ToTs(videoAddAudio);
        logger.info(toTs);
    }
}
