package com.app.view;

import de.felixroske.jfxsupport.FXMLController;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;

/**
 * 主窗口容器
 *
 * @Author guofan
 * @Create 2022/1/14
 */
@FXMLController
public class MainViewController {

    @FXML
    private BorderPane borderBane;

    private HashMap<String, Node> viewMap;

    @FXML
    public void initialize() throws IOException {
        initData();
        initLeftButton();
        setDefaultView();
    }

    /**
     *
     */
    private void initData() {
        viewMap = new HashMap<>(20);
    }

    /**
     * 设置默认显示页
     */
    private void setDefaultView() throws IOException {
        URL resource = getClass().getResource("configView.fxml");
        FXMLLoader fxmlLoader = new FXMLLoader(resource);
        Node load = fxmlLoader.load();
        borderBane.setCenter(load);
        viewMap.put("SpeakWords", load);
    }

    /**
     * 初始化左边按钮
     */
    private void initLeftButton() {
        VBox left = (VBox) borderBane.getLeft();
        ObservableList<Node> leftButton = left.getChildren();
        for (Node node : leftButton) {
            Button button = (Button) node;
            button.setOnAction(event -> {
                try {
                    String pageId = button.getId();
                    Node currentView = viewMap.get(pageId);
                    if (currentView == null) {
                        URL resource = getClass().getResource(pageId + ".fxml");
                        FXMLLoader fxmlLoader = new FXMLLoader(resource);
                        currentView = fxmlLoader.load();
                        viewMap.put(pageId, currentView);
                    }
                    borderBane.setCenter(currentView);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

    }
}
