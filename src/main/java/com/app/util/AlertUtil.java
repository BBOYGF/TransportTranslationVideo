package com.app.util;

import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;

/**
 * 弹窗工具类
 *
 * @Author guofan
 * @Create 2022/1/22
 */
public class AlertUtil {
    public static void show(String title, String Content) {
        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle(title);
        alert.setContentText(Content);
        ObservableList<ButtonType> buttonTypes = alert.getButtonTypes();
        buttonTypes.add(ButtonType.CLOSE);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.CLOSE) {
            alert.close();
        }
    }

    public static void error(String title, String Content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(Content);
        ObservableList<ButtonType> buttonTypes = alert.getButtonTypes();
        buttonTypes.add(ButtonType.CLOSE);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.CLOSE) {
            alert.close();
        }
    }
}
