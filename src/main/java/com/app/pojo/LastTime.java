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
