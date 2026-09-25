package com.app.view;


import com.app.Main;
import com.app.util.AlertUtil;
import com.app.view_model.ConfigViewModel;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;


public class ConfigView {


    public TextField urlTextField;


    public TextArea titleTextField;


    public TextField beginTextField;


    public TextField beginTitle1TextField;


    public TextField beginTitle2TextField;


    public TextField beginImageTextField;


    public Button choiceBeginImageButton;


    public Button choiceBeginButton;


    public Button generateBeginButton;


    public Button uploadButton;


    public Button loginDouYinButton;


    public TextArea logTextArea;


    /**
     * ViewModel
     */
    private ConfigViewModel viewModel;
    private Logger logger;


    @FXML
    public void initialize() {
        // 数据初始化
        initData();
        // 绑定属性
        binding();
        // 设置默认数据
        setDefaultData();
    }

    private void setDefaultData() {
        viewModel.setDefaultData();
    }


    /**
     * 初始化数据
     */
    private void initData() {
        logger = LoggerFactory.getLogger(getClass());
        viewModel = ConfigViewModel.getInstance();
    }


    private void binding() {
        urlTextField.textProperty().bindBidirectional(viewModel.urlPropProperty());
        titleTextField.textProperty().bindBidirectional(viewModel.titleTextFieldPropProperty());
        beginTextField.textProperty().bindBidirectional(viewModel.beginPropProperty());
        beginTitle1TextField.textProperty().bindBidirectional(viewModel.beginTitle1PropProperty());
        beginTitle2TextField.textProperty().bindBidirectional(viewModel.beginTitle2PropProperty());
        beginImageTextField.textProperty().bindBidirectional(viewModel.beginImagePropProperty());
        logTextArea.textProperty().bindBidirectional(viewModel.logPropProperty());
    }


    public void onChoiceBeginButtonClick() {

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择恶化率文件");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("webm", "*.webm"));
        File file = fileChooser.showOpenDialog(Main.getStage());
        if (file != null) {
            logger.debug("文件位置是：{}", file.getAbsolutePath());
            viewModel.setBeginProp(file.getAbsolutePath());
        }
    }

    /**
     * 选择「开头图片」——合成开头视频用的那张图。
     */
    public void onChoiceBeginImageButtonClick() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择开头图片（合成开头视频用的图片）");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("图片", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.bmp"));
        File file = fileChooser.showOpenDialog(Main.getStage());
        if (file != null) {
            logger.debug("开头图片位置是：{}", file.getAbsolutePath());
            viewModel.setBeginImageProp(file.getAbsolutePath());
        }
    }

    /**
     * 用「开头图片 + 开头主标题 + 开头副标题」合成开头短视频。
     *
     * <p>「开头短视频」和这三项是二选一的：要么直接选现成的 webm，
     * 要么填好这三项让程序合成。</p>
     */
    public void onGenerateBeginButtonClick() {
        // 优先用已填好的「开头图片」；没填就现场让用户选一张，并记到该字段里
        String bgPath = viewModel.getBeginImageProp();
        File image = (bgPath != null && !bgPath.isBlank()) ? new File(bgPath) : null;
        if (image != null && !image.exists()) {
            logger.warn("开头图片不存在，重新选择：{}", bgPath);
            image = null;
        }
        if (image == null) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("选择开头图片（合成开头视频用的图片）");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("图片", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.bmp"));
            image = fileChooser.showOpenDialog(Main.getStage());
            if (image == null) {
                return;
            }
            viewModel.setBeginImageProp(image.getAbsolutePath());
        }

        logger.info("用开头图片合成开头视频：{}", image.getAbsolutePath());
        Task<File> task = viewModel.generateBeginningVideo(image);

        task.setOnSucceeded(event -> {
            File result = task.getValue();
            viewModel.setBeginProp(result.getAbsolutePath());
            AlertUtil.show("提示", "开头视频生成成功：\n" + result.getAbsolutePath());
        });

        task.setOnFailed(event -> {
            logger.error("生成开头视频失败", task.getException());
            AlertUtil.show("提示", "生成失败!" + task.getException().getMessage());
        });
    }


    /**
     * 登录抖音：登录态保存在固定用户目录，只需登录一次；
     * 之后即使登录态过期，「一键搬运」也会在浏览器里等待你重新登录。
     */
    public void onLoginDouYinButtonClick() {
        Task<Void> task = viewModel.loginDouYin();

        task.setOnSucceeded(event -> AlertUtil.show("提示", "抖音登录成功！"));

        task.setOnFailed(event -> {
            logger.error("抖音登录失败", task.getException());
            AlertUtil.show("提示", "登录失败!" + task.getException().getMessage());
        });
    }

    /**
     * 上传列表
     */
    public void onUploadButtonClick() {
        viewModel.saveCurrentData();
        Task<Void> task = viewModel.uploadVideo();

        task.setOnSucceeded(event -> {
            AlertUtil.show("提示", "成功!");
        });

        task.setOnFailed(event -> {
            logger.error("上传视频失败", task.getException());
            AlertUtil.show("提示", "失败!" + task.getException().getMessage());
        });
    }
}
