package com.app.view;

import com.app.pojo.DownloadStep;
import com.app.pojo.DownloadVideo;
import com.app.util.AlertUtil;
import com.app.view_model.ConfigViewModel;
import com.app.view_model.StatusViewModel;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.cell.ComboBoxTableCell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * 状态页面：把数据库里的任务/步骤状态展示成
 * 「总视频 → 分段 → 5 个步骤」的树，并支持改状态、重置、重跑。
 *
 * @Author OverCode
 */
public class StatusView {

    /**
     * 步骤状态文案（同时也是「状态」列下拉框的两个选项）
     */
    private static final String STEP_DONE = "✅ 已完成";

    private static final String STEP_UNDONE = "❌ 未完成";

    /**
     * 任务列表（总视频 / 分段）
     */
    @FXML
    public TreeTableView<DownloadVideo> taskTree;

    @FXML
    public TreeTableColumn<DownloadVideo, String> typeCol;

    @FXML
    public TreeTableColumn<DownloadVideo, String> idCol;

    @FXML
    public TreeTableColumn<DownloadVideo, String> titleCol;

    @FXML
    public TreeTableColumn<DownloadVideo, String> progressCol;

    @FXML
    public TreeTableColumn<DownloadVideo, String> artifactCol;

    @FXML
    public TreeTableColumn<DownloadVideo, String> stateCol;

    /**
     * 选中任务的步骤明细
     */
    @FXML
    public TableView<DownloadStep> stepTable;

    @FXML
    public TableColumn<DownloadStep, String> stepOrderCol;

    @FXML
    public TableColumn<DownloadStep, String> stepNameCol;

    @FXML
    public TableColumn<DownloadStep, String> stepStateCol;

    @FXML
    public TextArea logTextArea;

    @FXML
    public Label summaryLabel;

    @FXML
    public Button refreshButton;

    @FXML
    public Button continueButton;

    @FXML
    public Button resetAndRunButton;

    @FXML
    public Button markDoneButton;

    @FXML
    public Button markUndoneButton;

    @FXML
    public Button openFolderButton;

    private Logger logger;

    private StatusViewModel viewModel;

    private StatusViewModel.StatusData currentData;

    /**
     * 有父任务的分段（用来说明"总视频"）
     */
    private Set<Integer> splitParentIds = new HashSet<>();

    /**
     * 是否有任务正在执行，避免并发开浏览器
     */
    private boolean running;

    /**
     * 已经同步到日志区的字符数，用于把「一键搬运」的日志实时同步过来
     */
    private int syncedLogLength;

    @FXML
    public void initialize() {
        logger = LoggerFactory.getLogger(getClass());
        viewModel = StatusViewModel.getInstance();
        initColumns();

        taskTree.setShowRoot(false);
        taskTree.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> showSteps(newValue == null ? null : newValue.getValue()));
        // 双击左边任务 = 展示它的步骤
        taskTree.setRowFactory(view -> {
            javafx.scene.control.TreeTableRow<DownloadVideo> row = new javafx.scene.control.TreeTableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showSteps(row.getItem());
                }
            });
            return row;
        });

        // 把「一键搬运」页面的日志实时同步一份过来，方便在这里看执行进度
        ConfigViewModel.getInstance().logPropProperty().addListener((observable, oldValue, newValue) -> appendDelta(newValue));
        appendDelta(ConfigViewModel.getInstance().getLogProp());

        refresh();
    }

    /**
     * 初始化表格列
     */
    private void initColumns() {
        typeCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(describeType(param.getValue().getValue())));
        idCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(
                param.getValue().getValue() == null ? "" : String.valueOf(param.getValue().getValue().getId())));
        titleCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(shortTitle(param.getValue().getValue())));
        progressCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(describeProgress(param.getValue().getValue())));
        artifactCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(describeArtifact(param.getValue().getValue())));
        stateCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(describeState(param.getValue().getValue())));

        stepOrderCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(
                param.getValue().getOrderId() == null ? "" : String.valueOf(param.getValue().getOrderId())));
        stepNameCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getStepName()));
        stepStateCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(
                Boolean.TRUE.equals(param.getValue().getSucceed()) ? STEP_DONE : STEP_UNDONE));

        // 「状态」列直接做成下拉框：点一下就能改，改完立刻落库
        stepTable.setEditable(true);
        stepStateCol.setCellFactory(ComboBoxTableCell.forTableColumn(STEP_DONE, STEP_UNDONE));
        stepStateCol.setOnEditCommit(event -> applyStepState(event.getRowValue(), STEP_DONE.equals(event.getNewValue())));
    }

    // ==================== 展示相关 ====================

    private boolean isSegment(DownloadVideo video) {
        return video != null && video.getTitle() != null
                && StatusViewModel.SEGMENT_TITLE_PATTERN.matcher(video.getTitle()).matches();
    }

    private String describeType(DownloadVideo video) {
        if (video == null) {
            return "";
        }
        if (isSegment(video)) {
            Matcher matcher = StatusViewModel.SEGMENT_TITLE_PATTERN.matcher(video.getTitle());
            if (matcher.matches()) {
                return "第 " + matcher.group(1) + "/" + matcher.group(2) + " 段";
            }
            return "分段";
        }
        return splitParentIds.contains(video.getId()) ? "总视频(父任务)" : "单条视频";
    }

    private String shortTitle(DownloadVideo video) {
        if (video == null || video.getTitle() == null) {
            return "";
        }
        String title = video.getTitle();
        // 分段标题去掉 "(1-5)" 前缀，只留正文
        Matcher matcher = StatusViewModel.SEGMENT_TITLE_PATTERN.matcher(title);
        if (matcher.matches()) {
            title = title.substring(matcher.group(0).indexOf(')') + 1);
        }
        return title.length() > 40 ? title.substring(0, 40) + "…" : title;
    }

    private String describeProgress(DownloadVideo video) {
        if (video == null || currentData == null) {
            return "";
        }
        long finished = currentData.finishedCountOf(video.getId());
        int total = currentData.stepsOf(video.getId()).size();
        return finished + "/" + total;
    }

    private String describeArtifact(DownloadVideo video) {
        if (video == null) {
            return "";
        }
        return video.getMergeVideoPath() == null ? "—" : "已合并";
    }

    private String describeState(DownloadVideo video) {
        if (video == null) {
            return "";
        }
        return Boolean.TRUE.equals(video.getSucceed()) ? "已完成" : "未完成";
    }

    // ==================== 数据加载 ====================

    /**
     * 从数据库刷新状态（后台线程查）
     */
    public void refresh() {
        Integer keepVideoId = selectedVideoId();
        Integer keepStepId = selectedStepId();
        Task<StatusViewModel.StatusData> task = new Task<StatusViewModel.StatusData>() {
            @Override
            protected StatusViewModel.StatusData call() {
                return viewModel.load();
            }
        };
        task.setOnSucceeded(event -> {
            rebuildTree(task.getValue());
            selectVideoById(keepVideoId);
            selectStepById(keepStepId);
        });
        task.setOnFailed(event -> {
            logger.error("加载状态失败", task.getException());
            appendLog("加载状态失败：" + message(task.getException()));
        });
        startThread(task, "状态刷新线程");
    }

    /**
     * 按 url 把任务归并成「总视频 → 分段」
     */
    private void rebuildTree(StatusViewModel.StatusData data) {
        currentData = data;
        List<DownloadVideo> sorted = data.getVideos().stream()
                .sorted(Comparator.comparingInt(video -> video.getId() == null ? 0 : video.getId()))
                .collect(Collectors.toList());

        Map<String, List<DownloadVideo>> grouped = new LinkedHashMap<>();
        for (DownloadVideo video : sorted) {
            String key = video.getUrl() == null ? "id:" + video.getId() : video.getUrl();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(video);
        }

        TreeItem<DownloadVideo> root = new TreeItem<>();
        Set<Integer> parents = new HashSet<>();
        for (List<DownloadVideo> group : grouped.values()) {
            // 父任务 = 标题不是 "(i-n)" 开头的那条（一般就是最早创建的那条）
            DownloadVideo parent = group.stream().filter(video -> !isSegment(video)).findFirst().orElse(group.get(0));
            TreeItem<DownloadVideo> parentItem = new TreeItem<>(parent);

            List<DownloadVideo> segments = group.stream().filter(this::isSegment).collect(Collectors.toList());
            if (!segments.isEmpty()) {
                parents.add(parent.getId());
                for (DownloadVideo segment : segments) {
                    parentItem.getChildren().add(new TreeItem<>(segment));
                }
                parentItem.setExpanded(true);
            }
            root.getChildren().add(parentItem);

            // 同一 url 下既不是父任务也不是分段的，单独作为根节点
            for (DownloadVideo other : group) {
                if (other != parent && !isSegment(other)) {
                    root.getChildren().add(new TreeItem<>(other));
                }
            }
        }
        splitParentIds = parents;
        taskTree.setRoot(root);

        long unfinished = data.getVideos().stream().filter(video -> !Boolean.TRUE.equals(video.getSucceed())).count();
        summaryLabel.setText("  共 " + data.getVideos().size() + " 条任务，未完成 " + unfinished + " 条");
    }

    private void showSteps(DownloadVideo video) {
        if (video == null || currentData == null) {
            stepTable.setItems(FXCollections.observableArrayList());
            return;
        }
        List<DownloadStep> steps = currentData.stepsOf(video.getId());
        stepTable.setItems(FXCollections.observableArrayList(steps));
        if (!steps.isEmpty()) {
            // 默认选中第一个未完成的步骤，这样点上方按钮就能直接改，不用先在表格里点一行
            DownloadStep target = steps.stream()
                    .filter(step -> !Boolean.TRUE.equals(step.getSucceed()))
                    .findFirst().orElse(steps.get(0));
            stepTable.getSelectionModel().select(target);
        }
    }

    /**
     * 修改完状态后，直接用内存里的对象刷新界面，不依赖重新查库
     * （这样即使数据库读取失败，用户也能立刻看到改动生效）
     */
    private void syncInMemoryState(Integer videoId) {
        if (currentData == null || videoId == null) {
            return;
        }
        List<DownloadStep> steps = currentData.stepsOf(videoId);
        boolean allDone = !steps.isEmpty()
                && steps.stream().allMatch(step -> Boolean.TRUE.equals(step.getSucceed()));
        for (DownloadVideo video : currentData.getVideos()) {
            if (Objects.equals(video.getId(), videoId)) {
                video.setSucceed(allDone);
                break;
            }
        }
        stepTable.refresh();
        taskTree.refresh();
        summaryLabel.setText("  共 " + currentData.getVideos().size() + " 条任务，未完成 "
                + currentData.getVideos().stream().filter(v -> !Boolean.TRUE.equals(v.getSucceed())).count() + " 条");
    }

    // ==================== 交互 ====================

    /**
     * 刷新按钮
     */
    public void onRefreshClick() {
        try {
            refresh();
        } catch (Exception e) {
            logger.error("刷新失败", e);
            appendLog("刷新失败：" + message(e));
            AlertUtil.show("提示", "刷新失败：" + message(e));
        }
    }

    /**
     * 继续执行选中任务的未完成步骤
     */
    public void onContinueClick() {
        runVideo(selectedVideo());
    }

    /**
     * 全部重置并重跑
     */
    public void onResetAndRunClick() {
        DownloadVideo video = selectedVideo();
        if (video == null) {
            AlertUtil.show("提示", "请先在左边的列表里选中一条任务");
            return;
        }
        StringBuilder tips = new StringBuilder("确定要把该任务的所有步骤重置为未完成并重新执行吗？\n\n");
        tips.append("任务：id=").append(video.getId()).append("  ").append(shortTitle(video)).append("\n");
        if (splitParentIds.contains(video.getId())) {
            tips.append("\n⚠ 这是父任务（总视频）：重新执行「下载视频」会把视频再切一次并生成新的分段任务，\n")
                    .append("建议改为对每个分段（第 i/n 段）单独重置重跑。\n");
        }
        if (!confirm("全部重置并重跑", tips.toString())) {
            return;
        }
        try {
            viewModel.resetVideo(video);
            appendLog("已重置任务 id=" + video.getId());
            runVideo(video);
        } catch (Exception e) {
            logger.error("重置任务失败", e);
            appendLog("重置任务失败：" + message(e));
            AlertUtil.show("提示", "重置失败：" + message(e));
        }
    }

    /**
     * 把选中的步骤标记为完成
     */
    public void onMarkStepDoneClick() {
        markSelectedStep(true);
    }

    /**
     * 把选中的步骤标记为未完成
     */
    public void onMarkStepUndoneClick() {
        markSelectedStep(false);
    }

    /**
     * 打开选中任务对应的文件夹
     */
    public void onOpenFolderClick() {
        DownloadVideo video = selectedVideo();
        if (video == null) {
            AlertUtil.show("提示", "请先在左边的列表里选中一条任务");
            return;
        }
        String path = video.getMergeVideoPath() != null ? video.getMergeVideoPath() : video.getVideoPath();
        if (path == null) {
            AlertUtil.show("提示", "该任务还没有产生文件");
            return;
        }
        File parent = new File(path).getParentFile();
        if (parent == null || !parent.exists()) {
            AlertUtil.show("提示", "目录不存在：\n" + path);
            return;
        }
        try {
            Runtime.getRuntime().exec(new String[]{"explorer.exe", parent.getAbsolutePath()});
        } catch (Exception e) {
            logger.error("打开文件夹失败", e);
            AlertUtil.show("提示", "打开文件夹失败：" + message(e));
        }
    }

    // ==================== 内部工具 ====================

    private void markSelectedStep(boolean succeed) {
        DownloadStep step = stepTable.getSelectionModel().getSelectedItem();
        if (step == null) {
            AlertUtil.show("提示", "请先在步骤表格里点中一行（例如「上传抖音」），再点这个按钮。\n"
                    + "也可以直接双击「状态」那一列，从下拉框里选。");
            return;
        }
        int answer = confirmIndex("修改步骤状态",
                "把步骤「" + step.getStepName() + "」标记为" + (succeed ? "已完成" : "未完成") + "？");
        if (answer != 1) {
            return;
        }
        if (applyStepState(step, succeed)) {
            AlertUtil.show("提示", "已修改：任务 " + step.getMainId() + " 的「" + step.getStepName() + "」→ "
                    + (succeed ? "已完成" : "未完成"));
        }
    }

    /**
     * 把某一步的状态落库并刷新界面。
     *
     * <p>表格里的下拉框、上方的按钮，最终都走这里，保证行为一致；
     * 失败时会把界面上的显示回滚，并且**一定**给出提示，不会静默失败。</p>
     *
     * @return 是否真的发生了改变
     */
    private boolean applyStepState(DownloadStep step, boolean succeed) {
        if (step == null) {
            return false;
        }
        if (Objects.equals(Boolean.TRUE.equals(step.getSucceed()), succeed)) {
            return false;
        }
        try {
            viewModel.markStep(step, succeed);
            syncInMemoryState(step.getMainId());
            appendLog("步骤「" + step.getStepName() + "」(任务 " + step.getMainId() + ") 已改为"
                    + (succeed ? "已完成" : "未完成"));
            // 再和数据库对齐一次
            refresh();
            return true;
        } catch (Exception e) {
            logger.error("修改步骤状态失败", e);
            appendLog("修改步骤状态失败：" + message(e));
            AlertUtil.show("提示", "修改失败：" + message(e));
            // 回滚界面显示
            step.setSucceed(!succeed);
            syncInMemoryState(step.getMainId());
            return false;
        }
    }

    private void runVideo(DownloadVideo video) {
        if (video == null) {
            AlertUtil.show("提示", "请先在左边的列表里选中一条任务");
            return;
        }
        if (running) {
            AlertUtil.show("提示", "已有任务正在执行，请等它结束");
            return;
        }
        setRunning(true);
        appendLog("开始执行任务 id=" + video.getId() + " " + shortTitle(video) + "（未完成步骤）");
        Task<Void> task = ConfigViewModel.getInstance().reExecute(video);
        task.setOnSucceeded(event -> {
            setRunning(false);
            appendLog("任务 id=" + video.getId() + " 执行结束");
            refresh();
            AlertUtil.show("提示", "执行结束！");
        });
        task.setOnFailed(event -> {
            setRunning(false);
            appendLog("任务 id=" + video.getId() + " 执行失败：" + message(task.getException()));
            refresh();
            AlertUtil.show("提示", "执行失败：" + message(task.getException()));
        });
    }

    /**
     * 执行期间禁用会重复触发的按钮，避免并发开浏览器
     */
    private void setRunning(boolean value) {
        running = value;
        if (refreshButton != null) {
            refreshButton.setDisable(value);
        }
        if (continueButton != null) {
            continueButton.setDisable(value);
        }
        if (resetAndRunButton != null) {
            resetAndRunButton.setDisable(value);
        }
        if (markDoneButton != null) {
            markDoneButton.setDisable(value);
        }
        if (markUndoneButton != null) {
            markUndoneButton.setDisable(value);
        }
    }

    private DownloadVideo selectedVideo() {
        TreeItem<DownloadVideo> item = taskTree.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue();
    }

    private Integer selectedVideoId() {
        DownloadVideo video = selectedVideo();
        return video == null ? null : video.getId();
    }

    private Integer selectedStepId() {
        DownloadStep step = stepTable.getSelectionModel().getSelectedItem();
        return step == null ? null : step.getId();
    }

    private void selectVideoById(Integer id) {
        if (id == null) {
            return;
        }
        TreeItem<DownloadVideo> found = findItem(taskTree.getRoot(), id);
        if (found != null) {
            taskTree.getSelectionModel().select(found);
            showSteps(found.getValue());
        }
    }

    private void selectStepById(Integer id) {
        if (id == null) {
            return;
        }
        for (DownloadStep step : stepTable.getItems()) {
            if (Objects.equals(step.getId(), id)) {
                stepTable.getSelectionModel().select(step);
                return;
            }
        }
    }

    private TreeItem<DownloadVideo> findItem(TreeItem<DownloadVideo> node, Integer id) {
        if (node == null) {
            return null;
        }
        DownloadVideo video = node.getValue();
        if (video != null && Objects.equals(video.getId(), id)) {
            return node;
        }
        for (TreeItem<DownloadVideo> child : node.getChildren()) {
            TreeItem<DownloadVideo> result = findItem(child, id);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * 只追加新增的日志内容
     */
    private void appendDelta(String log) {
        if (log == null || logTextArea == null) {
            return;
        }
        if (log.length() < syncedLogLength) {
            syncedLogLength = 0;
            logTextArea.clear();
        }
        if (log.length() > syncedLogLength) {
            logTextArea.appendText(log.substring(syncedLogLength));
            syncedLogLength = log.length();
        }
    }

    private void appendLog(String message) {
        if (logTextArea == null) {
            return;
        }
        logTextArea.appendText("\n[状态页] " + message);
        logTextArea.setScrollTop(Double.MAX_VALUE);
        syncedLogLength = ConfigViewModel.getInstance().getLogProp() == null
                ? syncedLogLength : ConfigViewModel.getInstance().getLogProp().length();
    }

    private boolean confirm(String title, String content) {
        return confirmIndex(title, content) == 1;
    }

    /**
     * 显示确认框，确定返回 1，取消返回 0
     */
    private int confirmIndex(String title, String content) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.getButtonTypes().setAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == javafx.scene.control.ButtonType.OK ? 1 : 0;
    }

    private void startThread(Task<?> task, String name) {
        Thread thread = new Thread(task);
        thread.setName(name);
        thread.setDaemon(true);
        thread.start();
    }

    private String message(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        return throwable.getMessage() == null ? throwable.toString() : throwable.getMessage();
    }
}
