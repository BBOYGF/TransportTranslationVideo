package com.app.util.downlod_video;

import cn.hutool.crypto.digest.DigestAlgorithm;
import cn.hutool.crypto.digest.Digester;
import com.app.util.downlod_video.pojo.ParseResultBean;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.util.*;
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
    private static final String API_URL = "https://service.iiilab.com/openapi/extract";

    /**
     * iiiLab分配的客户ID
     */
    private static final String client = "ce171ea5a317521g";

    /**
     * iiiLab分配的客户密钥
     */
    private static final String clientSecretKey = "729f7cb453fd6c8eea139e1ca07262d5";
    /**
     * 时间格式化
     */
    private final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");

    /**
     * yt-dlp 可执行文件（相对程序运行目录）
     */
    private static final String YT_DLP = "./lib/yt-dlp.exe";

    /**
     * 代理地址，与 loadVideo 中保持一致
     */
    private static final String PROXY_URL = "http://127.0.0.1:10808";

    /**
     * 本次运行是否已经尝试过自动更新 yt-dlp
     */
    private static volatile boolean updateTried = false;

    /**
     * yt-dlp 是否支持 --js-runtimes（探测结果缓存）
     */
    private static volatile Boolean supportsJsRuntimes = null;

    Gson gson = new Gson();
    private static final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS) // 建议设置超时时间
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * 实例化HttpClient，发送http请求使用，可根据需要自行调参
     *
     * @param url 请求地址
     */
    public ParseResultBean parseVideoResource(String url) {
        // 1. 准备数据：构建一个 Map 并转为 JSON 字符串
        Map<String, String> params = new HashMap<>();
        params.put("url", url);
        String jsonString = gson.toJson(params);
        // 2. 创建 MediaType，指定为 application/json
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        // 3. 创建 RequestBody，传入 JSON 字符串 (而不是 FormBody)
        RequestBody requestBody = RequestBody.create(mediaType, jsonString);
        final Request request = new Request.Builder()
                .url(API_URL)
                .header("Content-Type", "application/json")
                .header("x-client-id", client)       // 替换为你的iiiLab客户端ID
                .header("x-client-secret", clientSecretKey)  // 替换为你的iiiLab客户密钥
                .post(requestBody)
                .build();
        try (
                Response response = okHttpClient.newCall(request).execute();
        ) {
            if (!response.isSuccessful()) {
                log.error("请求失败，HTTP状态码: {}", response.code());
                // 如果需要，这里可以读取 body 里的错误信息
                return null;
            }
            // 检查 body 是否为空
            if (response.body() == null) {
                log.error("请求成功但响应体为空");
                return null;
            }
            // 5. 解析结果
            String string = response.body().string();
            log.info("API返回结果：{}", string);
            ParseResultBean resultBean = gson.fromJson(string, ParseResultBean.class);
            return resultBean;
        } catch (IOException e) {
            log.error("请求地址 [{}] 发生网络异常：", url, e);
            return null;
        } catch (Exception e) {
            log.error("JSON解析或其他异常：", e);
            return null;
        }
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
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 10808));
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
     * 下载视频。
     *
     * <p>失败时会自动尝试升级 yt-dlp 后重试一次（YouTube 的
     * "SABR-only / HTTP Error 403" 基本都是 yt-dlp 版本过旧导致）。</p>
     *
     * @param url      视频地址
     * @param fileName 视频名称
     * @return 下载好的视频文件
     * @throws IllegalStateException 最终下载失败
     */
    public File downloadVideo(String url, String fileName) {
        // 1. 格式化日期
        Date date = new Date();
        String dateString = format.format(date);

        // 2. 构建完整的文件路径字符串
        String outputFilePath = "C:\\视频\\项目\\" + dateString + fileName;
        File parentFile = new File(outputFilePath);
        // 【关键修复 1】获取父目录，只创建目录，不创建文件
        if (!parentFile.exists()) {
            log.info("创建目录: {}", parentFile.getAbsolutePath());
            boolean mkdirResult = parentFile.mkdirs();
            log.info("目录创建结果: {}", mkdirResult);
        }
        File videoFile = new File(parentFile, fileName + ".mp4");
        log.info("目标视频文件路径: {}", videoFile.getAbsolutePath());
        if (videoFile.exists()) {
            log.info("视频文件已存在，直接返回");
            return videoFile;
        }

        if (runYtDlp(url, videoFile)) {
            return videoFile;
        }

        // 失败兜底：升级 yt-dlp 后重试一次
        log.warn("首次下载失败，尝试升级 yt-dlp 后重试一次");
        updateYtDlp();
        if (runYtDlp(url, videoFile)) {
            return videoFile;
        }
        throw new IllegalStateException("视频下载失败：" + url
                + "，请查看日志中的 yt-dlp 输出（常见原因：代理未开启、yt-dlp 过旧、视频需要登录）");
    }

    /**
     * 执行一次 yt-dlp 下载
     *
     * @return 是否下载成功
     */
    private boolean runYtDlp(String url, File videoFile) {
        List<String> command = new ArrayList<>();
        command.add(YT_DLP);
        command.add("--proxy");
        command.add(PROXY_URL); // 请确保端口正确
        command.add("--force-ipv4");
        // 是否升级由程序在失败时统一控制，避免每次启动都提示版本过旧
        command.add("--no-update");
        command.add("--newline");
        // 合并音视频需要 ffmpeg，程序自带 ./lib/ffmpeg.exe
        command.add("--ffmpeg-location");
        command.add("./lib");
        // YouTube 现在需要 JS 运行时才能解出部分直链，否则会缺格式甚至 403
        String nodePath = findNodePath();
        if (nodePath != null && supportsJsRuntimes()) {
            command.add("--js-runtimes");
            command.add("node:" + nodePath);
        }
        command.add("--retries");
        command.add("3");
        command.add("-f");
        command.add("bv*+ba/b");
        command.add("--merge-output-format");
        command.add("mp4");
        command.add("-o");
        command.add(videoFile.getAbsolutePath()); // 设置输出路径
        command.add(url);

        log.info("执行 yt-dlp：{}", command);
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            // 【关键修复 3】解决乱码：指定编码读取流
            // Windows CMD 默认通常是 GBK，如果乱码依然存在，请改为 "UTF-8"
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "GBK"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("yt-dlp输出: {}", line);
                }
            }
            log.info("yt-dlp退出码: {}", process.waitFor());
        } catch (Exception e) {
            log.error("下载过程发生异常，URL: {}, 文件名: {}", url, videoFile.getName(), e);
        }

        boolean success = videoFile.exists();
        log.info("检查文件是否存在: {}", success);
        if (success) {
            log.info("下载成功，文件大小: {} bytes", videoFile.length());
        }
        return success;
    }

    /**
     * 自动升级 yt-dlp，本次运行只尝试一次
     */
    public void updateYtDlp() {
        if (updateTried) {
            return;
        }
        updateTried = true;
        List<String> command = new ArrayList<>();
        command.add(YT_DLP);
        command.add("-U");
        command.add("--proxy");
        command.add(PROXY_URL);
        log.info("开始更新 yt-dlp：{}", command);
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "GBK"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("yt-dlp更新输出: {}", line);
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.warn("更新 yt-dlp 失败，可手动执行 {} -U", YT_DLP, e);
        }
    }

    /**
     * 查找本机 node 路径，供 yt-dlp 作为 JS 运行时使用
     */
    private String findNodePath() {
        String[] candidates = {
                "C:\\tool\\nodejs\\node.exe",
                "C:\\Program Files\\nodejs\\node.exe",
                "C:\\Program Files (x86)\\nodejs\\node.exe"
        };
        for (String candidate : candidates) {
            if (new File(candidate).exists()) {
                return candidate;
            }
        }
        try {
            Process process = new ProcessBuilder("where", "node").redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank() && new File(line.trim()).exists()) {
                    return line.trim();
                }
            }
        } catch (Exception e) {
            log.debug("查找 node 失败：{}", e.getMessage());
        }
        log.warn("未找到 node，YouTube 部分格式可能无法解析，建议安装 Node.js");
        return null;
    }

    /**
     * 探测 yt-dlp 是否支持 --js-runtimes（旧版本没有该参数，直接传会报错）
     */
    private boolean supportsJsRuntimes() {
        if (supportsJsRuntimes != null) {
            return supportsJsRuntimes;
        }
        boolean supported = false;
        try {
            Process process = new ProcessBuilder(YT_DLP, "--help").redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("--js-runtimes")) {
                        supported = true;
                        break;
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.debug("探测 --js-runtimes 失败：{}", e.getMessage());
        }
        supportsJsRuntimes = supported;
        return supported;
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
                .replace(".", "").replace("'", "").replace("/", "")
                .replace("$", "_").replace(",", "_")
                ;
    }
}
