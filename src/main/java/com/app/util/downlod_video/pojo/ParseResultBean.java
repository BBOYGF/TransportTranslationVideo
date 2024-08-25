package com.app.util.downlod_video.pojo;

/**
 * 下载视频返回结果
 *
 * @Author guofan
 * @Create 2022/5/28
 */
public class ParseResultBean {
    /**
     * 成功返回200
     * 非200代表失败，如果失败，retDesc为失败原因。
     * 如果解析成功，data为解析结果数据，存在两种可能：
     * 如果是视频，其中video为视频地址，一定有；cover为视频封面地址，可能为空；text为视频标题，可能为空。
     * 如果是全民K歌的链接，返回结果里还有另外两个字段：videoType(音视频类型，两种可能值：audio, video)，songName(歌曲名称)
     * 如果是图集，则imgs为图片地址数组。（目前小红书、Instagram、淘宝天猫支持解析图集）
     * 如果请求失败，retCode为错误代码，retDesc为错误原因，retCode和retDesc是一对多关系，可能出现的错误原因：
     */
    public int retCode;
    public String retDesc;
    public Data data;
    public boolean succ;

    public int getRetCode() {
        return retCode;
    }

    public void setRetCode(int retCode) {
        this.retCode = retCode;
    }

    public String getRetDesc() {
        return retDesc;
    }

    public void setRetDesc(String retDesc) {
        this.retDesc = retDesc;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    public boolean isSucc() {
        return succ;
    }

    public void setSucc(boolean succ) {
        this.succ = succ;
    }

    @Override
    public String toString() {
        return "ResultBean{" +
                "retCode=" + retCode +
                ", retDesc='" + retDesc + '\'' +
                ", data=" + data +
                ", succ=" + succ +
                '}';
    }

    public static class Data {
        String cover;
        String text;
        String video;

        public String getCover() {
            return cover;
        }

        public void setCover(String cover) {
            this.cover = cover;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getVideo() {
            return video;
        }

        public void setVideo(String video) {
            this.video = video;
        }
    }
}
