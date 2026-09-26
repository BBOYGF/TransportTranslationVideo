package com.app.view_model;


import com.app.AppContext;
import com.app.enums.DownloadStepEnum;
import com.app.pojo.DownloadStep;
import com.app.pojo.DownloadVideo;

import com.app.pojo.LastTime;
import com.app.service.impl.DownloadStepServiceImpl;
import com.app.service.impl.DownloadVideoServiceImpl;

import com.app.service.impl.LastTimeServiceImpl;
import com.app.util.BeginningVideoGenerator;
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
import java.util.Optional;
import java.util.stream.Collectors;

import static com.app.util.FileNameUtils.sanitizeFileName;


public class ConfigViewModel {
    /**
     * 日志
     */
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static ConfigViewModel viewModel;


    private final SimpleStringProperty urlProp = new SimpleStringProperty();


    private final SimpleStringProperty titleTextFieldProp = new SimpleStringProperty();


    private final SimpleStringProperty beginProp = new SimpleStringProperty();

    /**
     * 开头视频主标题
     */
    private final SimpleStringProperty beginTitle1Prop = new SimpleStringProperty(BeginningVideoGenerator.DEFAULT_TEXT1);

    /**
     * 开头视频副标题
     */
    private final SimpleStringProperty beginTitle2Prop = new SimpleStringProperty(BeginningVideoGenerator.DEFAULT_TEXT2);

    /**
     * 开头视频用的图片（可选）
     */
    private final SimpleStringProperty beginImageProp = new SimpleStringProperty();


    private final SimpleBooleanProperty choiceBeginProp = new SimpleBooleanProperty();


    private final SimpleBooleanProperty uploadProp = new SimpleBooleanProperty();


    private final SimpleStringProperty logProp = new SimpleStringProperty("");
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
     * 根据图片在后台自动生成开头视频
     *
     * @param image 图片文件
     * @return 任务，执行结果为新生成的开头视频文件
     */
    public Task<File> generateBeginningVideo(File image) {
        log.info("点击根据图片生成开头视频：{}", image.getAbsolutePath());
        Task<File> task = new Task<File>() {
            @Override
            protected File call() throws Exception {
                logProp.set(getLogProp() + "\n开始根据图片生成开头视频：" + image.getName());
                BeginningVideoGenerator generator = new BeginningVideoGenerator();
                File webm = generator.generate(image, getBeginTitle1Prop(), getBeginTitle2Prop());
                logProp.set(getLogProp() + "\n开头视频生成完成：" + webm.getAbsolutePath());
                return webm;
            }
        };
        Thread thread = new Thread(task);
        thread.setName("生成开头视频任务线程");
        thread.setDaemon(true);
        thread.start();
        return task;
    }

    /**
     * 重新执行某一条任务的所有未完成步骤（供状态页面调用）。
     *
     * <p>注意：如果该任务是长视频父任务，执行「下载视频」时会重新切片并生成新的分段任务，
     * 所以状态页在重置父任务时会给出提示。</p>
     *
     * @param downloadVideo 要重新执行的任务
     * @return 执行任务
     */
    public Task<Void> reExecute(DownloadVideo downloadVideo) {
        log.info("重新执行任务，id={}", downloadVideo.getId());
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                initUtil();
                executeTask(downloadVideo);
                return null;
            }
        };
        Thread thread = new Thread(task);
        thread.setName("重新执行任务线程");
        thread.setDaemon(true);
        thread.start();
        return task;
    }

    /**
     * 打开浏览器等待人工登录抖音。
     * 登录态会保存到固定用户目录（C:\tool\chromedriver\UserData），只需登录一次。
     *
     * @return 登录任务
     */
    public Task<Void> loginDouYin() {
        log.info("开始登录抖音");
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                logProp.set(getLogProp() + "\n请在浏览器中完成抖音登录（扫码或手机号验证码）");
                UploadVideoUtil util = new UploadVideoUtil();
                util.loginDouYin();
                logProp.set(getLogProp() + "\n抖音登录成功，登录态已保存");
                return null;
            }
        };
        Thread thread = new Thread(task);
        thread.setName("抖音登录任务线程");
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
    private void upload() throws Exception {
        // 0、先登录微信视频号
//        uploadVideoUtil.loginWeChat();
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
     * 取开头短视频的路径。
     *
     * <p>「开头短视频」和「开头图片 + 主/副标题」是二选一（互斥）的：</p>
     * <ul>
     *     <li>填了「开头短视频」→ 直接用那个现成的 webm</li>
     *     <li>没填（或文件已不存在）→ 用「开头图片 + 开头主标题/副标题」现合成一个，
     *         合成后会写回「开头短视频」并保存，这样多个分段只合成一次、复用同一个片头</li>
     * </ul>
     *
     * @return 可用的开头视频路径
     */
    private String resolveBeginVideoPath() throws Exception {
        String beginPath = getBeginProp();
        if (StringUtils.isNotBlank(beginPath) && new File(beginPath).exists()) {
            return beginPath;
        }

        String bgImagePath = getBeginImageProp();
        if (StringUtils.isBlank(bgImagePath) || !new File(bgImagePath).exists()) {
            throw new IllegalStateException("没有可用的开头视频：请在「开头短视频」里选一个现成的 webm，"
                    + "或者填好「开头图片」和「开头主标题/副标题」，程序会自动合成一个");
        }

        logProp.set(getLogProp() + "\n未指定现成的开头短视频，开始用「开头图片 + 标题」合成……");
        File webm = new BeginningVideoGenerator().generate(new File(bgImagePath),
                getBeginTitle1Prop(), getBeginTitle2Prop());
        logProp.set(getLogProp() + "\n开头短视频已合成：" + webm.getAbsolutePath());
        // 写回并保存，后续分段直接复用
        setBeginProp(webm.getAbsolutePath());
        try {
            saveCurrentData();
        } catch (Exception e) {
            log.warn("保存开头视频路径失败（不影响本次合并）：{}", e.getMessage());
        }
        return webm.getAbsolutePath();
    }

    /**
     * 执行一次完整的任务
     */
    public void executeTask(DownloadVideo downloadVideo) throws Exception {
        QueryWrapper<DownloadStep> stepQueryWrapper = new QueryWrapper<>();
        stepQueryWrapper.eq("main_id", downloadVideo.getId());
        List<DownloadStep> downloadStepList = stepService.list(stepQueryWrapper);
        List<DownloadStep> stepOrderList = downloadStepList.stream().filter(downloadStep -> !downloadStep.getSucceed()).sorted(Comparator.comparingInt(DownloadStep::getOrderId)).collect(Collectors.toList());
        //3、执行每一个步骤
        for (DownloadStep downloadStep : stepOrderList) {
            log.info("正在执行步骤：{}。。。", downloadStep.getStepName());
            logProp.set(getLogProp() + "\n正在执行步骤" + downloadStep.getStepName());
            if (downloadStep.getStepName().equals(DownloadStepEnum.下载视频.name())) {
                File loadVideo = downloadUtil.downloadVideo(downloadVideo.getVideoUrl(), downloadVideo.getVideoTitle());
                if (loadVideo == null || !loadVideo.exists()) {
                    String message = "视频下载失败：" + downloadVideo.getVideoUrl()
                            + "，请检查代理(127.0.0.1:10808)是否开启、./lib/yt-dlp.exe 是否为最新版";
                    log.error(message);
                    logProp.set(getLogProp() + "\n" + message);
                    throw new IOException(message);
                }
                downloadVideo.setVideoPath(loadVideo.getAbsolutePath());
                // todo 如果视频太长那么需要截取视频
                if (isLongVideo(downloadVideo.getVideoPath(), downloadVideo)) {
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
                final String ccFilePath = parentFile + "\\" + ccFileName + ".ass";
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
                // 添加片头。
                // 开头短视频来源二选一（互斥）：
                //   1) 直接选了现成的 webm（「开头短视频」字段）
                //   2) 没选 → 用「开头图片 + 开头主标题/副标题」现合成一个
                String beginPath = resolveBeginVideoPath();
                EditVideoUtil editVideoUtil = new EditVideoUtil();
                String webmToMp4 = editVideoUtil.webmToMp4(beginPath);
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
            }
//            else if (downloadStep.getStepName().equals(DownloadStepEnum.上传视频号.name())) {
//                File mergeFile = new File(downloadVideo.getMergeVideoPath());
//                uploadVideoUtil.uploadWeChatVideo(downloadVideo.getTitle(), mergeFile);
//                downloadVideo.setUploadWx(true);
//                videoService.updateById(downloadVideo);
//                downloadStep.setSucceed(true);
//                stepService.updateById(downloadStep);
//                downloadVideo.setSucceed(true);
//                videoService.saveOrUpdate(downloadVideo);
//            }
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
        downloadVideo.setVideoUrl(getUrlProp());
        downloadVideo.setTitle(getTitleTextFieldProp());
        String[] split = getTitleTextFieldProp().split("#");
        String fileName;
        if (split.length < 1) {
            fileName = getBeginProp();
        } else {
            fileName = split[0];
        }
        downloadVideo.setVideoTitle(sanitizeFileName(fileName));
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
        String[] minute = new String[]{"0:0:0", "0:30:0", "1:0:0", "1:30:0", "2:0:0", "2:30:0", "3:0:0", "3:30:0", "4:0:0"};
        EditVideoUtil editVideoUtil = new EditVideoUtil();
        double videoLength = editVideoUtil.getVideoLength(new File(videoPath));
        log.info("视频长度是：{}秒", videoLength);
        logProp.set(getLogProp() + "\n视频长度是：" + videoLength + "秒");
        if (videoLength > 60 * 30) {
            int count = (int) Math.ceil(videoLength / (60 * 30));
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
            }
            List<DownloadStep> currentStep = stepService.list(new QueryWrapper<DownloadStep>().eq("main_id", downloadVideo.getId()));
            for (DownloadStep step : currentStep) {
                // 父任务只是"切片容器"，本身并不会上传。
                // 其它步骤标记完成避免重复处理，但「上传抖音」必须保持未完成，
                // 否则数据库里会显示"已上传"，实际却从没发布过（真正上传的是各个分段子任务）。
                if (!DownloadStepEnum.上传抖音.name().equals(step.getStepName())) {
                    step.setSucceed(true);
                }
            }
            stepService.saveOrUpdateBatch(currentStep);
            log.info("视频已切分为 {} 段，父任务(id={})不再重复处理，「上传抖音」保持未完成状态，真正发布会由子任务完成",
                    count, downloadVideo.getId());
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
        String subTitle = "(" + i + "-" + count + ")" + downloadVideo.getTitle();
        subDownloadVideo.setUrl(downloadVideo.getUrl());
        subDownloadVideo.setTitle(subTitle);
        subDownloadVideo.setVideoUrl(downloadVideo.getVideoUrl());
        // 补上 videoTitle，避免该字段为 null（下载步骤一旦被重置会拼出 null.mp4）
        subDownloadVideo.setVideoTitle(sanitizeFileName(subTitle.split("#")[0]));
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
            if (stepEnum.equals(DownloadStepEnum.下载视频)) {
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
        String beginTitle1 = lastTime.getBeginTitle1();
        if (StringUtils.isNoneBlank(beginTitle1)) {
            setBeginTitle1Prop(beginTitle1);
        }
        String beginTitle2 = lastTime.getBeginTitle2();
        if (StringUtils.isNoneBlank(beginTitle2)) {
            setBeginTitle2Prop(beginTitle2);
        }
        String beginImagePath = lastTime.getBeginImagePath();
        if (StringUtils.isNoneBlank(beginImagePath)) {
            setBeginImageProp(beginImagePath);
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

        if (StringUtils.isNoneBlank(getBeginTitle1Prop())) {
            lastTime.setBeginTitle1(getBeginTitle1Prop());
        }

        if (StringUtils.isNoneBlank(getBeginTitle2Prop())) {
            lastTime.setBeginTitle2(getBeginTitle2Prop());
        }

        if (StringUtils.isNoneBlank(getBeginImageProp())) {
            lastTime.setBeginImagePath(getBeginImageProp());
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


    public String getBeginTitle1Prop() {
        return beginTitle1Prop.get();
    }

    public SimpleStringProperty beginTitle1PropProperty() {
        return beginTitle1Prop;
    }

    public void setBeginTitle1Prop(String beginTitle1Prop) {
        this.beginTitle1Prop.set(beginTitle1Prop);
    }


    public String getBeginTitle2Prop() {
        return beginTitle2Prop.get();
    }

    public SimpleStringProperty beginTitle2PropProperty() {
        return beginTitle2Prop;
    }

    public void setBeginTitle2Prop(String beginTitle2Prop) {
        this.beginTitle2Prop.set(beginTitle2Prop);
    }


    public String getBeginImageProp() {
        return beginImageProp.get();
    }

    public SimpleStringProperty beginImagePropProperty() {
        return beginImageProp;
    }

    public void setBeginImageProp(String beginImageProp) {
        this.beginImageProp.set(beginImageProp);
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
        // null 兜底：防止日志拼接出 "null\n..." 显示在界面第一行
        return logProp.get() == null ? "" : logProp.get();
    }

    public SimpleStringProperty logPropProperty() {
        return logProp;
    }

    public void setLogProp(String logProp) {
        this.logProp.set(logProp);
    }


}
