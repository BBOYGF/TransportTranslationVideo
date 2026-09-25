package com.app.pojo;

import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 *
 * </p>
 *
 * @author guofan
 * @since 2023-11-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class LastTime implements Serializable {

    private static final long serialVersionUID = 1L;

    private String url;

    private String title;

    private String beginVideoPath;

    /**
     * 开头视频主标题
     */
    private String beginTitle1;

    /**
     * 开头视频副标题
     */
    private String beginTitle2;

    /**
     * 开头视频的背景图路径（可选）
     */
    private String beginImagePath;

    private Integer newColumn;

    private Integer id;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBeginVideoPath() {
        return beginVideoPath;
    }

    public void setBeginVideoPath(String beginVideoPath) {
        this.beginVideoPath = beginVideoPath;
    }

    public String getBeginTitle1() {
        return beginTitle1;
    }

    public void setBeginTitle1(String beginTitle1) {
        this.beginTitle1 = beginTitle1;
    }

    public String getBeginTitle2() {
        return beginTitle2;
    }

    public void setBeginTitle2(String beginTitle2) {
        this.beginTitle2 = beginTitle2;
    }

    public String getBeginImagePath() {
        return beginImagePath;
    }

    public void setBeginImagePath(String beginImagePath) {
        this.beginImagePath = beginImagePath;
    }

    public Integer getNewColumn() {
        return newColumn;
    }

    public void setNewColumn(Integer newColumn) {
        this.newColumn = newColumn;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}
