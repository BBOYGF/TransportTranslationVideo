package com.app.util;

import com.app.AppContext;
import com.app.enums.DownloadStepEnum;
import com.app.pojo.DownloadStep;
import com.app.pojo.DownloadVideo;
import com.app.service.impl.DownloadStepServiceImpl;
import com.app.service.impl.DownloadVideoServiceImpl;
import com.app.util.downlod_video.DownloadUtil;
import com.app.util.downlod_video.pojo.ParseResultBean;
import com.app.util.upload_video.UploadVideoUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author guofan
 * @Create 2022/5/28
 */
public class DownloadUtilTest {
    DownloadUtil downloadUtil = new DownloadUtil();
    Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 解析功能测试
     */
//    @Test
    public void loadVideoByUrl() {
        ParseResultBean resultBean = downloadUtil.parseVideoResource("https://www.youtube.com/watch?v=6VCdldE-IC8");
        log.info("执行完成！{}", resultBean);
    }

    /**
     * 下载功能测试
     */
//    @Test
    public void loadVideo() throws IOException {
        ParseResultBean resultBean = downloadUtil.parseVideoResource("https://www.youtube.com/watch?v=udlMSe5-zP8");
        assert (resultBean != null);
        File loadVideo = downloadUtil.loadVideo(resultBean.getData().getVideo(), downloadUtil.rename(resultBean.getData().getText()));
        log.info("文件地址是：{}", loadVideo.getAbsolutePath());
        // 打开文件夹
        String[] cmdDir = {"explorer.exe", loadVideo.getParent()};
        Runtime.getRuntime().exec(cmdDir);
    }

    public String url = "https://www.youtube.com/watch?v=13CZPWmke6A";
    public String title = "OpenAI 的联合创始人首席科学家Sutskever播客" +
            "#Sutskever  #Open AI #人工智能 #伊利亚·苏茨凯弗 #GPT ";
    public String beginPath = "C:\\Users\\fan\\Downloads\\heginning (14).webm";

    /**
     * 下载视频完整测试
     * 下载时要使用科学上网，不然会请求异常
     * 增加了判断是否运行ssr软件的判断 自动判断是否使用代理
     */
//    @Test
    public void fullTest() throws IOException, InterruptedException {
        ParseResultBean resultBean = downloadUtil.parseVideoResource(url);
        assert (resultBean != null);
        File loadVideo = downloadUtil.loadVideo(resultBean.getData().getVideo(), downloadUtil.rename(resultBean.getData().getText()));
        assert (loadVideo.exists());
        log.info("文件地址是：{}", loadVideo.getAbsolutePath());
        // 打开文件夹
        String[] cmdDir = {"explorer.exe", loadVideo.getParent()};
        Runtime.getRuntime().exec(cmdDir);
        // 上传字幕
        UploadVideoUtil uploadVideoUtil = new UploadVideoUtil();
        File ccFile = uploadVideoUtil.uploadTranslateVideo(loadVideo, true);
        Thread.sleep(1000);
        // 编辑视频
        // 复制视频到缓存目录下
        EditVideoUtil editVideoUtil = new EditVideoUtil();
        File subtitlesFile = editVideoUtil.encodedSubtitles(loadVideo, ccFile);
        // 添加片头
        File beginVideo = new File("C:\\ffmpeg\\bin\\2.ts");
        File mergeVideos = editVideoUtil.mergeVideos(beginVideo, subtitlesFile);
        // 上传平台
        uploadVideoUtil.uploadDouYinVideo(title, mergeVideos, new File(""));
        uploadVideoUtil.uploadWeChatVideo(title, mergeVideos);

    }

    /**
     * 下载字幕调试
     */
//    @Test
    public void uploadTranslateVideo() throws InterruptedException, IOException {
        UploadVideoUtil uploadVideoUtil = new UploadVideoUtil();
        File loadVideo = new File("C:\\视频\\项目\\20221012Andrew_Ng_Deep_Learning,_Education,_and_RealWorld_AI__Lex_Fridman_Podcast_#73\\a5.mp4");
        File ccFile = uploadVideoUtil.uploadTranslateVideo(loadVideo, true);
//        // 编辑视频
//        // 复制视频到缓存目录下
        EditVideoUtil editVideoUtil = new EditVideoUtil();
        File subtitlesFile = editVideoUtil.encodedSubtitles(loadVideo, ccFile);
//        // 添加片头
        File beginVideo = new File("C:\\ffmpeg\\bin\\1.ts");
        File mergeVideos = editVideoUtil.mergeVideos(beginVideo, subtitlesFile);
//        File mergeVideos = new File("C:\\视频\\项目\\20221010Cordless_Tesla\\Subtitle_Begin_SubtitleCordless_Tesla.mp4");
        // 上传平台
        uploadVideoUtil.uploadDouYinVideo(title, mergeVideos, new File(""));
        uploadVideoUtil.uploadWeChatVideo(title, mergeVideos);
    }

    /**
     * 判断是否可以被点击
     */
//    @Test
    public void isClick() throws InterruptedException {
        UploadVideoUtil uploadVideoUtil = new UploadVideoUtil();
//        uploadVideoUtil.uploadTranslateVideo(new File("C:\\视频\\项目\\20220903Advice for young peopleLearn things deeply  John Carmack and Lex Fridman\\Advice for young peopleLearn things deeply  John Carmack and Lex Fridman.mp4"));
        uploadVideoUtil.uploadDouYinVideo(title
                , new File("C:\\视频\\项目\\20220912Vectors__Chapter_1,_Essence_of_linear_algebra\\SubtitleVectors__Chapter_1,_Essence_of_linear_algebra.mp4")
                , new File(""));
    }

    /**
     * 自动登录测试
     */
//    @Test
    public void autoLogin() throws InterruptedException {
        UploadVideoUtil uploadVideoUtil = new UploadVideoUtil();
        uploadVideoUtil.uploadDouYinVideo("马克·扎克伯格：元宇宙何时会取代现实生活 #访谈 #英语翻译 #大神程序员 #社交媒体 #元宇宙 #马克扎克伯格", null, new File(""));
    }

    DownloadVideoServiceImpl videoService = AppContext.getBean(DownloadVideoServiceImpl.class);
    DownloadStepServiceImpl stepService = AppContext.getBean(DownloadStepServiceImpl.class);
    UploadVideoUtil uploadVideoUtil = new UploadVideoUtil();

    /**
     * 用数据库的方式记录执行历史方便每次执行
     *
     * @throws IOException
     * @throws InterruptedException
     */
//    @Test
    public void dataBaseTest() throws IOException, InterruptedException {
        // 0、先登录微信视频号
        uploadVideoUtil.loginWeChat();
        //1、先查看有没有没有处理完的
        QueryWrapper<DownloadVideo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("succeed", false);
        List<DownloadVideo> downloadVideoList = videoService.list(queryWrapper);
        if (downloadVideoList.size() > 0) {
            log.info("执行上次操作！");
            List<DownloadVideo> downloadVideoSorted = downloadVideoList.stream().sorted(Comparator.comparingInt(DownloadVideo::getId)).collect(Collectors.toList());
            // 如果没执行完，那么执行每个文件
            for (int i = 0; i < downloadVideoSorted.size(); i++) {
                DownloadVideo downloadVideo = downloadVideoSorted.get(i);
                executeTask(downloadVideo);
            }

        } else {
            QueryWrapper<DownloadVideo> query = new QueryWrapper<>();
            query.like("url", url);
            query.and(downloadVideoQueryWrapper -> downloadVideoQueryWrapper.eq("succeed", true));
            List<DownloadVideo> downloadVideoList2 = videoService.list(query);
            if (!CollectionUtils.isEmpty(downloadVideoList2)) {
                log.info("视频已下载完成！");
                return;
            }
            log.info("执行新的操作！");
            // 创建主任务
            DownloadVideo downloadVideo = generateTask();
            // 执行查到的历史记录
            executeTask(downloadVideo);
            // 执行上次没有执行完的任务，或者新产生的任务
            dataBaseTest();
        }

        log.info("插入成功！");
    }

    /**
     * 生成任务 和步骤
     */
    private DownloadVideo generateTask() {
        DownloadVideo downloadVideo = new DownloadVideo();
        downloadVideo.setUrl(url);
        downloadVideo.setTitle(title);
        downloadVideo.setSucceed(false);
        videoService.saveOrUpdate(downloadVideo);
        //2、创建子步骤
        DownloadStepEnum[] values = DownloadStepEnum.values();
        List<DownloadStep> downloadStepList = new ArrayList<>();
        int orderId = 0;
        for (DownloadStepEnum stepEnum : values) {
            orderId++;
            DownloadStep downloadStep = new DownloadStep();
            downloadStep.setStepName(stepEnum.name());
            downloadStep.setMainId(downloadVideo.getId());
            downloadStep.setOrderId(orderId);
            downloadStep.setSucceed(false);
            stepService.save(downloadStep);
            downloadStepList.add(downloadStep);
        }
        return downloadVideo;
    }

    /**
     * 生成长视频任务和步骤
     */
    private DownloadVideo generateLongVideoTask(int i, int count, DownloadVideo downloadVideo, String videoPath) {
        DownloadVideo subDownloadVideo = new DownloadVideo();
        subDownloadVideo.setUrl(downloadVideo.getUrl());
        subDownloadVideo.setTitle("(" + i + "-" + count + ")" + downloadVideo.getTitle());
        subDownloadVideo.setVideoUrl(downloadVideo.getVideoUrl());
        subDownloadVideo.setVideoPath(videoPath);
        subDownloadVideo.setSucceed(false);
        videoService.saveOrUpdate(subDownloadVideo);
        //2、创建子步骤
        DownloadStepEnum[] values = DownloadStepEnum.values();
        List<DownloadStep> downloadStepList = new ArrayList<>();
        int orderId = 0;
        for (DownloadStepEnum stepEnum : values) {
            orderId++;
            DownloadStep downloadStep = new DownloadStep();
            downloadStep.setStepName(stepEnum.name());
            downloadStep.setMainId(subDownloadVideo.getId());
            downloadStep.setOrderId(orderId);
            downloadStep.setSucceed(false);
            if (stepEnum.equals(DownloadStepEnum.获取下载地址) || stepEnum.equals(DownloadStepEnum.下载视频)) {
                downloadStep.setSucceed(true);
            }
            stepService.save(downloadStep);
            downloadStepList.add(downloadStep);
        }
        return subDownloadVideo;
    }

    /**
     * 执行一次完整的任务
     */
    public void executeTask(DownloadVideo downloadVideo) throws IOException, InterruptedException {
        QueryWrapper<DownloadStep> stepQueryWrapper = new QueryWrapper<>();
        stepQueryWrapper.eq("main_id", downloadVideo.getId());
        List<DownloadStep> downloadStepList = stepService.list(stepQueryWrapper);
        List<DownloadStep> stepOrderList = downloadStepList.stream().filter(downloadStep -> !downloadStep.getSucceed()).sorted(Comparator.comparingInt(DownloadStep::getOrderId)).collect(Collectors.toList());
        //3、执行每一个步骤
        for (DownloadStep downloadStep : stepOrderList) {
            log.info("正在执行步骤：{}。。。", downloadStep.getStepName());
            ParseResultBean resultBea;
            if (downloadStep.getStepName().equals(DownloadStepEnum.获取下载地址.name())) {
                ParseResultBean resultBean = downloadUtil.parseVideoResource(url);
                downloadVideo.setVideoUrl(resultBean.getData().getVideo());
                downloadVideo.setVideoTitle(downloadUtil.rename(resultBean.getData().getText()));
                videoService.updateById(downloadVideo);
                downloadStep.setSucceed(true);
                stepService.updateById(downloadStep);
            } else if (downloadStep.getStepName().equals(DownloadStepEnum.下载视频.name())) {
                File loadVideo = downloadUtil.loadVideo(downloadVideo.getVideoUrl(), downloadVideo.getVideoTitle());
                downloadVideo.setVideoPath(loadVideo.getAbsolutePath());
                // todo 如果视频太长那么需要截取视频
                if (isLongVideo(loadVideo.getAbsolutePath(), downloadVideo)) {
                    dataBaseTest();
                    return;
                }
                videoService.updateById(downloadVideo);
                downloadStep.setSucceed(true);
                // 打开文件夹
                String[] cmdDir = {"explorer.exe", loadVideo.getParent()};
                Runtime.getRuntime().exec(cmdDir);
                stepService.updateById(downloadStep);

//            } else if (downloadStep.getStepName().equals(DownloadStepEnum.上传翻译.name())) {
//                // 上传翻译
//                File videoFile = new File(downloadVideo.getVideoPath());
//                uploadVideoUtil.uploadTranslateVideo(videoFile, true);
//                downloadVideo.setUploadTranslated(true);
//                videoService.updateById(downloadVideo);
//                Thread.sleep(1000);
//                downloadStep.setSucceed(true);
//                stepService.updateById(downloadStep);
//            } else if (downloadStep.getStepName().equals(DownloadStepEnum.下载字幕.name())) {
//                File videoFile = new File(downloadVideo.getVideoPath());
//                File ccFile = uploadVideoUtil.uploadTranslateVideo(videoFile, false);
//                downloadVideo.setCcPath(ccFile.getAbsolutePath());
//                videoService.updateById(downloadVideo);
//                Thread.sleep(1000);
//                downloadStep.setSucceed(true);
//                stepService.updateById(downloadStep);
//            } else if (downloadStep.getStepName().equals(DownloadStepEnum.压制字幕.name())) {
                EditVideoUtil editVideoUtil = new EditVideoUtil();
                File videoFile = new File(downloadVideo.getVideoPath());
                File ccFile = new File(downloadVideo.getCcPath());
                File subtitlesFile = editVideoUtil.encodedSubtitles(videoFile, ccFile);
                downloadVideo.setCcVideoPath(subtitlesFile.getAbsolutePath());
                videoService.updateById(downloadVideo);
                downloadStep.setSucceed(true);
                stepService.updateById(downloadStep);
            } else if (downloadStep.getStepName().equals(DownloadStepEnum.合并开头.name())) {
                // 添加片头
                EditVideoUtil editVideoUtil = new EditVideoUtil();
                String webmToMp4 = editVideoUtil.webmToMp4(beginPath);
                String videoAddAudio = editVideoUtil.videoAddAudio(webmToMp4, "C:\\ffmpeg\\bin\\m.mp3");
                String toTs = editVideoUtil.mp4ToTs(videoAddAudio);
                File beginVideo = new File(toTs);
                File ccVideoFile = new File(downloadVideo.getCcVideoPath());
                File mergeVideos = editVideoUtil.mergeVideos(beginVideo, ccVideoFile);
                downloadVideo.setMergeVideoPath(mergeVideos.getAbsolutePath());
                videoService.updateById(downloadVideo);
                downloadStep.setSucceed(true);
                stepService.updateById(downloadStep);
            } else if (downloadStep.getStepName().equals(DownloadStepEnum.上传抖音.name())) {
                File mergeFile = new File(downloadVideo.getMergeVideoPath());
                uploadVideoUtil.uploadDouYinVideo(downloadVideo.getTitle(), mergeFile, new File(""));
                downloadVideo.setUploadDy(true);
                videoService.updateById(downloadVideo);
                downloadStep.setSucceed(true);
                stepService.updateById(downloadStep);
            } else if (downloadStep.getStepName().equals(DownloadStepEnum.上传视频号.name())) {
                File mergeFile = new File(downloadVideo.getMergeVideoPath());
                uploadVideoUtil.uploadWeChatVideo(downloadVideo.getTitle(), mergeFile);
                downloadVideo.setUploadWx(true);
                videoService.updateById(downloadVideo);
                downloadStep.setSucceed(true);
                stepService.updateById(downloadStep);
                downloadVideo.setSucceed(true);
                videoService.saveOrUpdate(downloadVideo);
            }
            int count = (int) stepOrderList.stream().filter(downloadStep1 -> !downloadStep1.getSucceed()).count();
            if (count == 0) {
                downloadVideo.setSucceed(true);
                videoService.saveOrUpdate(downloadVideo);
            }
        }
    }

    /**
     * 测试读取视频长度
     */
    public boolean isLongVideo(String videoPath, DownloadVideo downloadVideo) throws InterruptedException {
        String[] minute = new String[]{"0:0:0", "0:20:0", "0:40:0", "1:0:0", "1:20:0", "1:40:0", "2:0:0", "2:20:0", "2:40:0", "3:0:0", "3:20:0", "3:40:0"};
        EditVideoUtil editVideoUtil = new EditVideoUtil();
        double videoLength = editVideoUtil.getVideoLength(new File(videoPath));
        log.info("视频长度是：{}秒", videoLength);
        if (videoLength > 28 * 60) {
            int count = (int) Math.ceil(videoLength / (20 * 60));
            for (int i = 0; i < count; i++) {
                String splitVideo = editVideoUtil.splitVideo(i + 1,
                        videoPath,
                        minute[i],
                        minute[i + 1]);
                log.info("文件地址是：{}", splitVideo);
                // 生成对应的任务
                generateLongVideoTask(i + 1, count, downloadVideo, splitVideo);
                // 将本次任务都设置为完成状态
                downloadVideo.setSucceed(true);
                videoService.updateById(downloadVideo);
                List<DownloadStep> currentStep = stepService.list(new QueryWrapper<DownloadStep>().eq("main_id", downloadVideo.getId()));
                for (DownloadStep step : currentStep) {
                    step.setSucceed(true);
                }
                stepService.saveBatch(currentStep);
            }
            return true;
        } else {
            return false;
        }
    }

}
