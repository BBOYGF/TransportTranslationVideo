package com.app.util.upload_video;

import com.app.util.EditVideoUtil;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

/**
 * 上传字幕测试类
 *
 * @Author guofan
 * @Create 2022/9/4
 */
class UploadVideoUtilTest {
    /**
     * 下载字幕调试
     */
    @Test
    void uploadTranslateVideo() throws InterruptedException, IOException {
        UploadVideoUtil uploadVideoUtil = new UploadVideoUtil();
        File loadVideo = new File("E:\\pythonProject\\whisper\\test_video\\3_Pieter_Levels_Programming__Viral_AI_Startups__and_Digital_Nomad_Life__Lex_Fridman_Podcast_#440.mp4");
        File ccFile = new File("E:\\pythonProject\\whisper\\test_video\\3_Pieter_Levels_Programming__Viral_AI_Startups__and_Digital_Nomad_Life__Lex_Fridman_Podcast_#440.srt");
//        File ccFile = uploadVideoUtil.uploadTranslateVideo(loadVideo, false);
        // 编辑视频
        // 复制视频到缓存目录下
        EditVideoUtil editVideoUtil = new EditVideoUtil();
        File subtitlesFile = editVideoUtil.encodedSubtitles(loadVideo, ccFile);
//        File subtitlesFile = new File("C:\\视频\\项目\\20220904id Software origin story  John Carmack and Lex Fridman\\Subtitleid Software origin story  John Carmack and Lex Fridman.mp4");
        // 上传平台
//        uploadVideoUtil.uploadDouYinVideo("马克·扎克伯格回应Instagram告密者弗朗西斯·豪根 #访谈 #英语翻译 #大神程序员 #社交媒体 #元宇宙 #马克扎克伯格", subtitlesFile, new File(""));
    }
}
