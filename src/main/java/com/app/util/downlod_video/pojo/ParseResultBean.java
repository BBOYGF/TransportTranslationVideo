package com.app.util.downlod_video.pojo;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;


/**
 * iiiLab 视频解析接口响应实体类
 * 文档地址: https://github.com/iiilab-dev/post/wiki/iiiLab%E8%A7%86%E9%A2%91%E5%9B%BE%E7%89%87%E8%A7%A3%E6%9E%90%E6%8E%A5%E5%8F%A3
 */
public class ParseResultBean {

    // --- 基础状态字段 (如果接口返回包含状态码) ---

    @SerializedName(value = "retCode", alternate = {"ret_code", "code"})
    private Integer retCode;

    @SerializedName(value = "retMsg", alternate = {"ret_msg", "msg", "message"})
    private String retMsg;

    // --- 核心数据字段 ---

    /**
     * 视频/帖子的文案内容
     */
    @SerializedName("text")
    private String text;

    /**
     * 解析后的媒体资源列表 (视频、图片、音频)
     */
    @SerializedName("medias")
    private List<Media> medias;

    // --- Getters & Setters ---

    public Integer getRetCode() {
        return retCode;
    }

    public void setRetCode(Integer retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<Media> getMedias() {
        return medias;
    }

    public void setMedias(List<Media> medias) {
        this.medias = medias;
    }


    /**
     * 单个媒体资源对象
     */
    public static class Media {

        /**
         * 媒体类型: video, image, audio
         */
        @SerializedName("media_type")
        private String mediaType;

        /**
         * 默认下载地址 (通常是最高清晰度或默认清晰度)
         */
        @SerializedName("resource_url")
        private String resourceUrl;

        /**
         * 预览图/封面图地址
         */
        @SerializedName("preview_url")
        private String previewUrl;

        /**
         * 视频时长 (秒/毫秒，视具体接口而定，可选)
         */
        @SerializedName("duration")
        private Long duration;

        /**
         * 视频宽度 (可选)
         */
        @SerializedName("width")
        private Integer width;

        /**
         * 视频高度 (可选)
         */
        @SerializedName("height")
        private Integer height;

        /**
         * 多清晰度列表 (仅视频有效)
         */
        @SerializedName("formats")
        private List<Format> formats;

        /**
         * 下载时必须携带的请求头 (如 Referer, User-Agent)
         */
        @SerializedName("headers")
        private Map<String, String> headers;

        // Getters & Setters
        public String getMediaType() {
            return mediaType;
        }

        public void setMediaType(String mediaType) {
            this.mediaType = mediaType;
        }

        public String getResourceUrl() {
            return resourceUrl;
        }

        public void setResourceUrl(String resourceUrl) {
            this.resourceUrl = resourceUrl;
        }

        public String getPreviewUrl() {
            return previewUrl;
        }

        public void setPreviewUrl(String previewUrl) {
            this.previewUrl = previewUrl;
        }

        public List<Format> getFormats() {
            return formats;
        }

        public void setFormats(List<Format> formats) {
            this.formats = formats;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }


        /**
         * 3. 提供一个辅助方法，把 String 转为 Enum
         * 这样业务代码调用时用 getMediaTypeEnum()，非常安全
         */
        public Type getMediaTypeEnum() {
            if (mediaType == null) {
                return Type.UNKNOWN;
            }
            // 忽略大小写比较
            switch (mediaType.toLowerCase()) {
                case "video": return Type.VIDEO;
                case "image": return Type.IMAGE;
                case "audio": return Type.AUDIO;
                default:      return Type.UNKNOWN; // 遇到新类型归为 UNKNOWN，程序不会崩
            }
        }
    }

    /**
     * 视频清晰度格式对象
     */
    public static class Format {

        /**
         * 清晰度描述: "1080P", "超清", "标清" 等
         */
        @SerializedName(value = "name", alternate = {"quality", "format_id"})
        private String name;

        /**
         * 该清晰度的下载地址
         */
        @SerializedName("url")
        private String url;

        /**
         * 文件大小 (字节)
         */
        @SerializedName("size")
        private Long size;

        // Getters & Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Long getSize() {
            return size;
        }

        public void setSize(Long size) {
            this.size = size;
        }

        @Override
        public String toString() {
            return name + ": " + url;
        }
    }
    public enum Type {
        VIDEO, IMAGE, AUDIO, UNKNOWN
    }

}