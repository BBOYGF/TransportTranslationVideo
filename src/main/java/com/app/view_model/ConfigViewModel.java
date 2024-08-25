package com.app.view_model;


import com.app.AppContext;
import com.app.enums.DownloadStepEnum;
import com.app.pojo.DownloadStep;
import com.app.pojo.DownloadVideo;

import com.app.pojo.LastTime;
import com.app.service.impl.DownloadStepServiceImpl;
import com.app.service.impl.DownloadVideoServiceImpl;

import com.app.service.impl.LastTimeServiceImpl;
import com.app.util.EditVideoUtil;
import com.app.util.downlod_video.DownloadUtil;
import com.app.util.downlod_video.pojo.ParseResultBean;
import com.app.util.upload_video.UploadVideoUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


public class ConfigViewModel {
    /**
     * 日志
     */
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static ConfigViewModel viewModel;


    private final SimpleStringProperty urlProp = new SimpleStringProperty();


    private final SimpleStringProperty titleTextFieldProp = new SimpleStringProperty();


    private final SimpleStringProperty beginProp = new SimpleStringProperty();


    private final SimpleBooleanProperty choiceBeginProp = new SimpleBooleanProperty();


    private final SimpleBooleanProperty uploadProp = new SimpleBooleanProperty();


    private final SimpleStringProperty logProp = new SimpleStringProperty();
    private DownloadVideoServiceImpl videoService;
    private DownloadStepServiceImpl stepService;
    private UploadVideoUtil uploadVideoUtil;
    private DownloadUtil downloadUtil;
    private LastTimeServiceImpl lastTimeService;


    /**
     * 单例模式
     *
     * @return 单例
     */
    public static ConfigViewModel getInstance() {

        if (viewModel == null) {
            viewModel = new ConfigViewModel();
        }
        return viewModel;
    }

    /**
     * 后台上传视频
     *
     * @return 任务
     */
    public Task<Void> uploadVideo() {
        log.info("点击开始上传!");
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                initUtil();
                upload();
                return null;
            }

        };
        Thread thread = new Thread(task);
        thread.setName("上传视频任务线程");
        thread.setDaemon(true);
        thread.start();
        return task;
    }

    /**
     * 初始化服务
     */
    private void initUtil() {
        videoService = AppContext.getBean(DownloadVideoServiceImpl.class);
        stepService = AppContext.getBean(DownloadStepServiceImpl.class);
        uploadVideoUtil = new UploadVideoUtil();
        downloadUtil = new DownloadUtil();
    }

    /**
     * 上传
     */
    private void upload() throws IOException, InterruptedException {
        // 0、先登录微信视频号
        uploadVideoUtil.loginWeChat();
        //1、先查看有没有没有处理完的
        QueryWrapper<DownloadVideo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("succeed", false);
        List<DownloadVideo> downloadVideoList = videoService.list(queryWrapper);
        if (!downloadVideoList.isEmpty()) {
            log.info("执行上次操作！");
            logProp.set(getLogProp() + "\n执行上次操作！");
            List<DownloadVideo> downloadVideoSorted = downloadVideoList.stream().sorted(Comparator.comparingInt(DownloadVideo::getId)).collect(Collectors.toList());
            // 如果没执行完，那么执行每个文件
            for (DownloadVideo downloadVideo : downloadVideoSorted) {
                executeTask(downloadVideo);
            }

        } else {
            QueryWrapper<DownloadVideo> query = new QueryWrapper<>();
            query.like("url", getUrlProp());
            query.and(downloadVideoQueryWrapper -> downloadVideoQueryWrapper.eq("succeed", true));
            List<DownloadVideo> downloadVideoList2 = videoService.list(query);
            if (!CollectionUtils.isEmpty(downloadVideoList2)) {
                log.info("视频已下载完成！");
                logProp.set(getLogProp() + "\n视频已下载完成！");
                return;
            }
            log.info("执行新的操作！");
            logProp.set(getLogProp() + "\n执行新的操作！");
            // 创建主任务
            DownloadVideo downloadVideo = generateTask();
            // 执行查到的历史记录
            executeTask(downloadVideo);
            // 执行上次没有执行完的任务，或者新产生的任务
            upload();
        }

        log.info("插入成功！");

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
            logProp.set(getLogProp() + "\n正在执行步骤" + downloadStep.getStepName());
            ParseResultBean resultBea;
            if (downloadStep.getStepName().equals(DownloadStepEnum.获取下载地址.name())) {
                ParseResultBean resultBean = downloadUtil.parseVideoResource(getUrlProp());
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
                    upload();
                    return;
                }
                videoService.updateById(downloadVideo);
                downloadStep.setSucceed(true);
                // 打开文件夹
                String[] cmdDir = {"explorer.exe", loadVideo.getParent()};
                Runtime.getRuntime().exec(cmdDir);
                stepService.updateById(downloadStep);
            } else if (downloadStep.getStepName().equals(DownloadStepEnum.生成字幕.name())) {
                // 生成字幕
                final EditVideoUtil editVideoUtil = new EditVideoUtil();
                final File videoFile = new File(downloadVideo.getVideoPath());
                final String parentFile = videoFile.getParent();
                final String fileFillName = videoFile.getName();
                final String ccFileName = fileFillName.substring(0, fileFillName.lastIndexOf("."));
                final String ccFilePath = parentFile + "\\" + ccFileName + ".srt";
                editVideoUtil.genCCFile(downloadVideo.getVideoPath(), ccFilePath);
                downloadVideo.setUploadTranslated(true);
                downloadVideo.setCcPath(ccFilePath);
                videoService.updateById(downloadVideo);
                downloadStep.setSucceed(true);
                stepService.updateById(downloadStep);
            } else if (downloadStep.getStepName().equals(DownloadStepEnum.压制字幕.name())) {
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
                String webmToMp4 = editVideoUtil.webmToMp4(getBeginProp());
                String videoAddAudio = editVideoUtil.videoAddAudio(webmToMp4, "./template/m.mp3");
                String toTs = editVideoUtil.mp4ToTs(videoAddAudio);
                File beginVideo = new File(toTs);
                String ccVideo = editVideoUtil.mp4ToTs(downloadVideo.getCcVideoPath());
                File ccVideoFile = new File(ccVideo);
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
     * 生成任务 和步骤
     */
    private DownloadVideo generateTask() {
        DownloadVideo downloadVideo = new DownloadVideo();
        downloadVideo.setUrl(getUrlProp());
        downloadVideo.setTitle(getTitleTextFieldProp());
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
     * 测试读取视频长度
     */
    public boolean isLongVideo(String videoPath, DownloadVideo downloadVideo) throws InterruptedException {
        String[] minute = new String[]{"0:0:0", "1:0:0", "2:0:0", "3:0:0", "4:0:0","5:0:0","6:0:0"};
        EditVideoUtil editVideoUtil = new EditVideoUtil();
        double videoLength = editVideoUtil.getVideoLength(new File(videoPath));
        log.info("视频长度是：{}秒", videoLength);
        logProp.set(getLogProp() + "\n视频长度是：" + videoLength + "秒");
        if (videoLength > 60 * 60) {
            int count = (int) Math.ceil(videoLength / (60 * 60));
            for (int i = 0; i < count; i++) {
                String splitVideo = editVideoUtil.splitVideo(i + 1,
                        videoPath,
                        minute[i],
                        minute[i + 1]);
                log.info("文件地址是：{}", splitVideo);
                logProp.set(getLogProp() + "\n文件地址是：" + splitVideo);
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
     * 设置默认数据
     */
    public void setDefaultData() {
        lastTimeService = AppContext.getBean(LastTimeServiceImpl.class);
        LastTime lastTime = lastTimeService.getById(0);
        if (lastTime == null) {
            return;
        }
        String title = lastTime.getTitle();
        if (StringUtils.isNoneBlank(title)) {
            setTitleTextFieldProp(title);
        }
        String url = lastTime.getUrl();
        if (StringUtils.isNoneBlank(url)) {
            setUrlProp(url);
        }
        String beginVideoPath = lastTime.getBeginVideoPath();
        if (StringUtils.isNoneBlank(beginVideoPath)) {
            setBeginProp(beginVideoPath);
        }
    }

    public void saveCurrentData() {
        if (lastTimeService == null) {
            lastTimeService = AppContext.getBean(LastTimeServiceImpl.class);
        }
        LastTime lastTime = lastTimeService.getById(0);
        if (lastTime == null) {
            lastTime = new LastTime();
            lastTime.setId(0);
        }
        if (StringUtils.isNoneBlank(getTitleTextFieldProp())) {
            lastTime.setTitle(getTitleTextFieldProp());
        }

        if (StringUtils.isNoneBlank(getUrlProp())) {
            lastTime.setUrl(getUrlProp());
        }

        if (StringUtils.isNoneBlank(getBeginProp())) {
            lastTime.setBeginVideoPath(getBeginProp());
        }
        lastTimeService.saveOrUpdate(lastTime);

    }

    public String getUrlProp() {
        return urlProp.get();
    }

    public SimpleStringProperty urlPropProperty() {
        return urlProp;
    }

    public void setUrlProp(String urlProp) {
        this.urlProp.set(urlProp);
    }


    public String getTitleTextFieldProp() {
        return titleTextFieldProp.get();
    }

    public SimpleStringProperty titleTextFieldPropProperty() {
        return titleTextFieldProp;
    }

    public void setTitleTextFieldProp(String titleTextFieldProp) {
        this.titleTextFieldProp.set(titleTextFieldProp);
    }


    public String getBeginProp() {
        return beginProp.get();
    }

    public SimpleStringProperty beginPropProperty() {
        return beginProp;
    }

    public void setBeginProp(String beginProp) {
        this.beginProp.set(beginProp);
    }


    public boolean getChoiceBeginProp() {
        return choiceBeginProp.get();
    }

    public SimpleBooleanProperty choiceBeginPropProperty() {
        return choiceBeginProp;
    }

    public void setChoiceBeginProp(boolean choiceBeginProp) {
        this.choiceBeginProp.set(choiceBeginProp);
    }


    public boolean getUploadProp() {
        return uploadProp.get();
    }

    public SimpleBooleanProperty uploadPropProperty() {
        return uploadProp;
    }

    public void setUploadProp(boolean uploadProp) {
        this.uploadProp.set(uploadProp);
    }


    public String getLogProp() {
        return logProp.get();
    }

    public SimpleStringProperty logPropProperty() {
        return logProp;
    }

    public void setLogProp(String logProp) {
        this.logProp.set(logProp);
    }


}
