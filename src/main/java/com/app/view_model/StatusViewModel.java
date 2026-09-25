package com.app.view_model;

import com.app.AppContext;
import com.app.pojo.DownloadStep;
import com.app.pojo.DownloadVideo;
import com.app.service.impl.DownloadStepServiceImpl;
import com.app.service.impl.DownloadVideoServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 状态页面的数据访问层。
 *
 * <p>数据量很小（任务几十条、步骤几百条），所以一次性全量查出来，
 * 由界面按 url 归并成「总视频 → 分段」的树。</p>
 *
 * @Author OverCode
 */
public class StatusViewModel {

    /**
     * 分段子任务的标题形如 "(1-5)真正的标题"
     */
    public static final Pattern SEGMENT_TITLE_PATTERN = Pattern.compile("^\\((\\d+)-(\\d+)\\).*");

    private static StatusViewModel viewModel;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private DownloadVideoServiceImpl videoService;

    private DownloadStepServiceImpl stepService;

    private StatusViewModel() {
    }

    /**
     * 单例模式
     */
    public static synchronized StatusViewModel getInstance() {
        if (viewModel == null) {
            viewModel = new StatusViewModel();
        }
        return viewModel;
    }

    private void initService() {
        if (videoService == null) {
            videoService = AppContext.getBean(DownloadVideoServiceImpl.class);
        }
        if (stepService == null) {
            stepService = AppContext.getBean(DownloadStepServiceImpl.class);
        }
    }

    /**
     * 加载全部任务和步骤
     */
    public StatusData load() {
        initService();
        List<DownloadVideo> videos = videoService.list();
        List<DownloadStep> steps = stepService.list();
        log.debug("加载状态：任务 {} 条，步骤 {} 条", videos.size(), steps.size());
        return new StatusData(videos, steps);
    }

    /**
     * 修改某个步骤的完成状态，并同步任务整体状态
     */
    public void markStep(DownloadStep step, boolean succeed) {
        initService();
        step.setSucceed(succeed);
        stepService.updateById(step);
        refreshVideoSucceed(step.getMainId());
        log.info("步骤 id={} 已改为：{}", step.getId(), succeed ? "已完成" : "未完成");
    }

    /**
     * 重置整条任务：所有步骤置为未完成，并清掉该视频的上传标记
     *
     * @return 重置后的步骤列表（按 order 排序）
     */
    public List<DownloadStep> resetVideo(DownloadVideo video) {
        initService();
        List<DownloadStep> steps = stepService.list(
                new QueryWrapper<DownloadStep>().eq("main_id", video.getId()));
        for (DownloadStep step : steps) {
            step.setSucceed(false);
        }
        if (!steps.isEmpty()) {
            stepService.saveOrUpdateBatch(steps);
        }

        DownloadVideo current = videoService.getById(video.getId());
        if (current != null) {
            current.setSucceed(false);
            current.setUploadDy(false);
            current.setUploadWx(false);
            videoService.updateById(current);
        }
        log.info("任务 id={} 已重置为未完成", video.getId());
        return steps;
    }

    /**
     * 根据步骤完成情况同步任务整体的 succeed 标记
     */
    public void refreshVideoSucceed(Integer videoId) {
        initService();
        List<DownloadStep> steps = stepService.list(
                new QueryWrapper<DownloadStep>().eq("main_id", videoId));
        boolean allDone = !steps.isEmpty()
                && steps.stream().allMatch(step -> Boolean.TRUE.equals(step.getSucceed()));
        DownloadVideo video = videoService.getById(videoId);
        if (video != null && !Objects.equals(allDone, Boolean.TRUE.equals(video.getSucceed()))) {
            video.setSucceed(allDone);
            videoService.updateById(video);
        }
    }

    /**
     * 状态页面的数据载体
     */
    public static class StatusData {
        private final List<DownloadVideo> videos;
        private final List<DownloadStep> steps;

        public StatusData(List<DownloadVideo> videos, List<DownloadStep> steps) {
            this.videos = videos == null ? new ArrayList<>() : videos;
            this.steps = steps == null ? new ArrayList<>() : steps;
        }

        public List<DownloadVideo> getVideos() {
            return videos;
        }

        public List<DownloadStep> getSteps() {
            return steps;
        }

        /**
         * 取某个任务的步骤（按 order 排序）
         */
        public List<DownloadStep> stepsOf(Integer videoId) {
            List<DownloadStep> result = new ArrayList<>();
            for (DownloadStep step : steps) {
                if (Objects.equals(step.getMainId(), videoId)) {
                    result.add(step);
                }
            }
            result.sort((a, b) -> Integer.compare(
                    a.getOrderId() == null ? 0 : a.getOrderId(),
                    b.getOrderId() == null ? 0 : b.getOrderId()));
            return result;
        }

        /**
         * 已完成步骤数
         */
        public long finishedCountOf(Integer videoId) {
            return stepsOf(videoId).stream().filter(step -> Boolean.TRUE.equals(step.getSucceed())).count();
        }
    }
}
