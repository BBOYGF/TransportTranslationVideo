package com.app.util.downlod_video;

import cn.hutool.crypto.digest.DigestAlgorithm;
import cn.hutool.crypto.digest.Digester;
import com.app.util.downlod_video.pojo.ParseResultBean;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 下载视频工具
 * <p>输入网址自动下载视频</p>
 *
 * @Author guofan
 * @Create 2022/5/28
 */
public class DownloadUtil {
    Logger log = LoggerFactory.getLogger(getClass());
    /**
     * iiiLab视频解析接口地址
     */
    private static final String iiiLabVideoDownloadURL = "http://service.iiilab.com/video/download";

    /**
     * iiiLab分配的客户ID
     */
    private static final String client = "ce171ea5a317521g";

    /**
     * iiiLab分配的客户密钥
     */
    private static final String clientSecretKey = "729f7cb453fd6c8eea139e1ca07262d5";

    /**
     * 实例化HttpClient，发送http请求使用，可根据需要自行调参
     *
     * @param url 请求地址
     */
    public ParseResultBean parseVideoResource(String url) {
        Long timestamp = System.currentTimeMillis();
        Digester md5 = new Digester(DigestAlgorithm.MD5);

        String sign = md5.digestHex(url + timestamp + clientSecretKey);
//        String sign = DigestUtils.md5Hex(url + timestamp + clientSecretKey);

        OkHttpClient okHttpClient = new OkHttpClient();
        RequestBody requestBody = new FormBody.Builder()
                .add("link", url)
                .add("timestamp", String.valueOf(timestamp))
                .add("sign", sign)
                .add("client", client)
                .build();

        final Request request = new Request.Builder()
                .url(iiiLabVideoDownloadURL)
                .post(requestBody)
                .build();
        Call call = okHttpClient.newCall(request);
        Response response = null;
        try {
            response = call.execute();
        } catch (IOException e) {
            log.error("请求地址{}发生了异常：", url, e);
            e.printStackTrace();
        }
        ParseResultBean resultBean = null;
        try {
            String string = response.body().string();
            Gson gson = new Gson();
            resultBean = gson.fromJson(string, ParseResultBean.class);
            log.info("返回的结果为：{}", string);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return resultBean;
    }

    /**
     * 下载视频资源
     *
     * @param videoUrl  视频地址
     * @param videoName 视频名称
     * @return 视频文件
     */
    public File loadVideo(String videoUrl, String videoName) {
        OkHttpClient okHttpClient = null;
        // 设从请求到结束用时最长不超10分钟
        if (isRun()) {
            log.info("使用代理下载...");
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 10809));
            okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .proxy(proxy)
                    .build();
        } else {
            log.info("没使用代理下载...");
            okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .build();
        }

        final Request request = new Request.Builder()
                .url(videoUrl)
                .build();
        Call call = okHttpClient.newCall(request);
        Response response = null;
        long startTime = System.currentTimeMillis();
        try {
            response = call.execute();
        } catch (Exception e) {
            log.info("用时{}毫秒", System.currentTimeMillis() - startTime);
            log.error("请求地址{}发生了异常：", videoUrl, e);
            e.printStackTrace();
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        Date date = new Date();
        String dateString = format.format(date);
        File downloadVideo = new File("C:\\视频\\项目\\" + dateString + videoName);
        if (!downloadVideo.exists()) {
            downloadVideo.mkdirs();
        }
        File videoFile = new File(downloadVideo, videoName + ".mp4");
        try {
            InputStream inputStream = response.body().byteStream();
            long length = response.body().contentLength();
            double len = (double) length / (double) (1024 * 1024);
            byte[] bytes = new byte[1024 * 1024];
            BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
            FileOutputStream fileOutputStream = new FileOutputStream(videoFile);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
            int readLine;
            int contentLength = 1;
            while ((readLine = bufferedInputStream.read(bytes)) != -1) {
                bufferedOutputStream.write(bytes, 0, readLine);
                bufferedOutputStream.flush();
                log.info("已下载内容{}%...", contentLength / len);
                contentLength++;
            }
            bufferedOutputStream.close();
            fileOutputStream.close();
            bufferedInputStream.close();
            inputStream.close();
        } catch (IOException e) {
            log.info("用时{}毫秒", System.currentTimeMillis() - startTime);
            log.error("下载视频发生异常：", e);
        }
        log.info("用时{}毫秒", System.currentTimeMillis() - startTime);
        return videoFile;
    }

    /**
     * 判断某个程序是否在运行
     *
     * @return 成功返回true
     */
    private boolean isRun() {
        String processName = "shadowsocksr-dotnet4.0.ex";
        String processName2 = "v2ray";
        Runtime commandLine = Runtime.getRuntime();
        try {
            Process dataFlow = commandLine.exec("cmd /c Tasklist");
            BufferedReader arrayOfStrings = new BufferedReader(new InputStreamReader(dataFlow.getInputStream()));
            String oneLine;
            while ((oneLine = arrayOfStrings.readLine()) != null) {
                oneLine = oneLine.toLowerCase();
                //这里不用完全匹配，而是匹配前缀，只要前缀满足要求即可
                if (oneLine.contains(processName) || oneLine.contains(processName2)) {
                    System.out.println(">找到了目标：" + processName);
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 重新修改名字 去掉导致一次的字符串
     *
     * @return
     */
    public String rename(String name) {
        return name.replace(":", "").replace("|", "").replaceAll("\\\\", "")
                .replace("?", "").replace("*", "").replace("<", "")
                .replace(">", "").replace("\"", "").replace("-", "")
                .replace("(", "").replace(")", "").replace(" ", "_")
                .replace(".", "").replace("'", "").replace("/", "");
    }
}
