package com.app.view;

import de.felixroske.jfxsupport.SplashScreen;
import javafx.scene.Parent;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * 闪屏界面
 *
 * @Author guofan
 * @Create 2022/1/17
 */
public class SplashView extends SplashScreen {
    @Override
    public String getImagePath() {
        return "/image/马达加斯加的企鹅.bmp";

    }

    @Override
    public Parent getParent() {
        final ImageView imageView = new ImageView(Objects.requireNonNull(getClass().getResource(getImagePath())).toExternalForm());
        final ProgressBar splashProgressBar = new ProgressBar();
        splashProgressBar.setPrefWidth(imageView.getImage().getWidth());
        final VBox vbox = new VBox();
        vbox.getChildren().addAll(imageView, splashProgressBar);
        return vbox;
    }
}
