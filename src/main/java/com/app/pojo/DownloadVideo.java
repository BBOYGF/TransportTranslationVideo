package com.app.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 *
 * </p>
 *
 * @author guofan
 * @since 2022-10-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DownloadVideo implements Serializable {
    public DownloadVideo() {
    }

    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String url;

    private String title;

    private String path;

    private String videoUrl;

    private String videoTitle;

    private String videoPath;

    private String ccPath;

    private String ccVideoPath;

    private String mergeVideoPath;

    private Boolean succeed;
    private Boolean uploadDy;
    private Boolean uploadWx;
    private Boolean uploadTranslated;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

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

    public Boolean getSucceed() {
        return succeed;
    }

    public void setSucceed(Boolean succeed) {
        this.succeed = succeed;
    }

    public String getPath() {
        return path;
    }

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getVideoTitle() {
        return videoTitle;
    }

    public void setVideoTitle(String videoTitle) {
        this.videoTitle = videoTitle;
    }

    public String getCcPath() {
        return ccPath;
    }

    public void setCcPath(String ccPath) {
        this.ccPath = ccPath;
    }

    public String getCcVideoPath() {
        return ccVideoPath;
    }

    public void setCcVideoPath(String ccVideoPath) {
        this.ccVideoPath = ccVideoPath;
    }

    public String getMergeVideoPath() {
        return mergeVideoPath;
    }

    public void setMergeVideoPath(String mergeVideoPath) {
        this.mergeVideoPath = mergeVideoPath;
    }

    public Boolean getUploadDy() {
        return uploadDy;
    }

    public void setUploadDy(Boolean uploadDy) {
        this.uploadDy = uploadDy;
    }

    public Boolean getUploadWx() {
        return uploadWx;
    }

    public void setUploadWx(Boolean uploadWx) {
        this.uploadWx = uploadWx;
    }

    public Boolean getUploadTranslated() {
        return uploadTranslated;
    }

    public void setUploadTranslated(Boolean uploadTranslated) {
        this.uploadTranslated = uploadTranslated;
    }
}
