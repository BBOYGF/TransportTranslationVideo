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


    public Button choiceBeginButton;


    public Button uploadButton;


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
