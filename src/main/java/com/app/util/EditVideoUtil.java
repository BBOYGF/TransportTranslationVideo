package com.app.util;

import cn.hutool.core.io.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 自动化编辑视频工具类
 *
 * @Author guofan
 * @Create 2022/8/28
 */
public class EditVideoUtil {
    Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 播放视频
     *
     * @param path 视频地址
     */
    public void playVideo(String path) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        List<String> params = new ArrayList<>();
        params.add("./lib/ffplay.exe");
        params.add(path);
        processBuilder.command(params);
        //将标准输入流和错误流合并
        processBuilder.redirectErrorStream(true);
        try {
            //启动一个进程
            Process process = processBuilder.start();
            //通过标准输入流拿到正常错误的信息
            InputStream inputStream = process.getInputStream();
            //转成字符流输出
            InputStreamReader reader = new InputStreamReader(inputStream, "GBK");
            BufferedReader bufferedReader = new BufferedReader(reader);
            String line;
            //缓冲
            while ((line = bufferedReader.readLine()) != null) {
                logger.info(line);
            }
            //关流
            reader.close();
            inputStream.close();
        } catch (Exception e) {
            logger.error("发生了异常", e);
        }
    }

    /**
     * 压制字幕
     */
    public File encodedSubtitles(File videoFile, File subtitleFile) {
        File tempVideoFile = new File("./temp/video/" + videoFile.getName().replace("(", "").replace(")", "").replace(",", "_").replace("[", "").replace("]", ""));
        File tempCCFile = new File("./temp/video/" + subtitleFile.getName().replace("(", "").replace(")", "").replace(",", "_").replace("[", "").replace("]", ""));
        FileUtil.copy(videoFile, tempVideoFile, true);
        FileUtil.copy(subtitleFile, tempCCFile, true);
        String outPutVideoFile = "Subtitle" + videoFile.getName();
        File resultFile = new File(tempVideoFile.getParent(), outPutVideoFile);
        ProcessBuilder processBuilder = new ProcessBuilder();
        List<String> params = new ArrayList<>();
        params.add("./lib/ffmpeg.exe");
        params.add("-i");
        // 文件路径为/
        params.add(tempVideoFile.getAbsolutePath().split(":")[1].replace("\\", "/"));
        params.add("-vf");
        params.add("\"subtitles=" + tempCCFile.getAbsolutePath().split(":")[1].replace("\\", "/") + ":force_style='fontname=Source Han Sans CN bold,fontSize=40,PrimaryColour=&HFFFF00&,outlineColour=&H00000000,BorderStyle=2'" + "\"");
        params.add("-c:v");
        params.add("libx264");
        params.add("-c:a");
        params.add("copy");
        params.add("-y");
        params.add(resultFile.getAbsolutePath());
        processBuilder.command(params);
        logger.info("执行参数为：{}", params);
        //将标准输入流和错误流合并
        processBuilder.redirectErrorStream(true);
        try {
            //启动一个进程
            Process process = processBuilder.start();
            //通过标准输入流拿到正常错误的信息
            InputStream inputStream = process.getInputStream();
            //转成字符流输出
            InputStreamReader reader = new InputStreamReader(inputStream, "GBK");
            BufferedReader bufferedReader = new BufferedReader(reader);
            String line;
            //缓冲
            while ((line = bufferedReader.readLine()) != null) {
                logger.info(line);
            }
            //关流
            reader.close();
            inputStream.close();
        } catch (Exception e) {
            logger.error("发生了异常", e);
        }
        // 复制到项目目录下并且删除缓存
        if (!resultFile.exists()) {
            return null;
        }
        File ccFile = new File(videoFile.getParent(), resultFile.getName());
        logger.info("未加字幕保存路径:{}", resultFile.getAbsolutePath());
        FileUtil.copy(resultFile, ccFile, true);
        logger.info("加字幕文件保存路径:{}", ccFile.getAbsolutePath());

        resultFile.delete();
        tempVideoFile.delete();
        tempCCFile.delete();
        return ccFile;
    }

    /**
     * 合并视频
     *
     * @param beginVideo   视频开头
     * @param contentVideo 视频内容
     * @return 合并后的视频
     */
    public File mergeVideos(File beginVideo, File contentVideo) {
        File tempBeginVideo = new File("./temp/video/" + beginVideo.getName());
        File tempContentFile = new File("./temp/video/" + contentVideo.getName());
        FileUtil.copy(beginVideo, tempBeginVideo, true);
        FileUtil.copy(contentVideo, tempContentFile, true);
        String fullName = contentVideo.getName();
        String substring = fullName.substring(0, fullName.length() - 2);
        String outPutVideoFile = "Subtitle_Begin" + "_" + substring + "mp4";
        File resultFile = new File(contentVideo.getParent(), outPutVideoFile);
        // 合并视频
        ProcessBuilder builder = new ProcessBuilder();
        List<String> params2 = new ArrayList<>();
        params2.add("./lib/ffmpeg.exe");
        params2.add("-i");
        // 文件路径为/
        params2.add("concat:" + beginVideo.getAbsolutePath() + "|" + contentVideo.getAbsolutePath());
        params2.add("-vcodec");
        params2.add("copy");
        params2.add("-acodec");
        params2.add("copy");
        params2.add("-absf");
        params2.add("aac_adtstoasc");
        params2.add("-y");
        params2.add(resultFile.getAbsolutePath());
        builder.command(params2);
        logger.info("执行参数为：{}", params2);
        //将标准输入流和错误流合并
        builder.redirectErrorStream(true);
        try {
            //启动一个进程
            Process process = builder.start();
            //通过标准输入流拿到正常错误的信息
            InputStream inputStream = process.getInputStream();
            //转成字符流输出
            InputStreamReader reader = new InputStreamReader(inputStream, "GBK");
            BufferedReader bufferedReader = new BufferedReader(reader);
            String line;
            //缓冲
            while ((line = bufferedReader.readLine()) != null) {
                logger.info(line);
            }
            //关流
            reader.close();
            inputStream.close();
        } catch (Exception e) {
            logger.error("发生了异常", e);
        }
        // 复制到项目目录下并且删除缓存
        if (!resultFile.exists()) {
            return null;
        }
        tempBeginVideo.delete();
        tempContentFile.delete();
        return resultFile;
    }

    /**
     * 判断视频长度
     */
    public double getVideoLength(File videoFile) {
        File tempVideoFile = new File("./video/" + videoFile.getName().replace("(", "").replace(")", "").replace(",", "_"));
        FileUtil.copy(videoFile, tempVideoFile, true);
        ProcessBuilder processBuilder = new ProcessBuilder();
        List<String> params = new ArrayList<>();
        params.add("./lib/ffprobe.exe");
        params.add("-v");
        params.add("error");
        params.add("-show_entries");
        params.add("format=duration");
        params.add("-of");
        params.add("default=noprint_wrappers=1:nokey=1");
        params.add("-i");
        params.add(tempVideoFile.getAbsolutePath());
        processBuilder.command(params);
        logger.info("执行参数为：{}", params);
        //将标准输入流和错误流合并
        processBuilder.redirectErrorStream(true);
        String line = null;
        try {
            //启动一个进程
            Process process = processBuilder.start();
            //通过标准输入流拿到正常错误的信息
            InputStream inputStream = process.getInputStream();
            //转成字符流输出
            InputStreamReader reader = new InputStreamReader(inputStream, "GBK");
            BufferedReader bufferedReader = new BufferedReader(reader);
            //缓冲
            line = bufferedReader.readLine();
            logger.info(line);
            //关流
            reader.close();
            inputStream.close();
        } catch (Exception e) {
            logger.error("发生了异常", e);
        }
        tempVideoFile.delete();
        return Double.parseDouble(line);
    }

    /**
     * 拆分视频
     *
     * @param videoPath 视频地址
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 结果文件地址
     */
    public String splitVideo(int number, String videoPath, String startTime, String endTime) throws InterruptedException {
        File videoFile = new File(videoPath);
        File tempVideoFile = new File("./video/" + videoFile.getName().replace("(", "").replace(")", "").replace(",", "_"));
        Thread.sleep(1000);
        FileUtil.copy(videoFile, tempVideoFile, true);
        // 输出文件
        String splitName = tempVideoFile.getName();
        File splitFile = new File(tempVideoFile.getParent(), number + "_" + splitName);
        ProcessBuilder processBuilder = new ProcessBuilder();
        List<String> params = new ArrayList<>();
        params.add("./lib/ffmpeg.exe");
        params.add("-ss");
        params.add(startTime);
        params.add("-to");
        params.add(endTime);
        params.add("-i");
        params.add(tempVideoFile.getAbsolutePath());
        params.add("-c");
        params.add("copy");
        params.add(splitFile.getAbsolutePath());
        params.add("-y");
        processBuilder.command(params);
        logger.info("执行参数为：{}", params);
        //将标准输入流和错误流合并
        processBuilder.redirectErrorStream(true);
        try {
            //启动一个进程
            Process process = processBuilder.start();
            //通过标准输入流拿到正常错误的信息
            InputStream inputStream = process.getInputStream();
            //转成字符流输出
            InputStreamReader reader = new InputStreamReader(inputStream, "GBK");
            BufferedReader bufferedReader = new BufferedReader(reader);
            //缓冲
            String line;
            //缓冲
            while ((line = bufferedReader.readLine()) != null) {
                logger.info(line);
            }
            logger.info(line);
            //关流
            reader.close();
            inputStream.close();
        } catch (Exception e) {
            logger.error("发生了异常", e);
        }
        if (splitFile.exists()) {
            logger.info("文件存在");
        } else {
            logger.error("文件不存在。。。");
        }
        //复制到原来的地方
        File resultFile = new File(videoFile.getParent(), splitFile.getName());
        FileUtil.copy(splitFile, resultFile, true);
        boolean delete1 = tempVideoFile.delete();
        boolean delete = splitFile.delete();
        return resultFile.getAbsolutePath();
    }

    /**
     * 将web转换为mp4
     */
    public String webmToMp4(String path) {
        File videoFile = new File(path);
        File tempVideoFile = new File("./temp/video/" + videoFile.getName().replace("(", "").replace(")", "").replace(",", "_"));
        FileUtil.copy(videoFile, tempVideoFile, true);
        // 输出文件
        String splitName = tempVideoFile.getName();
        File transform = new File(tempVideoFile.getParent(), splitName.split("\\.")[0] + ".mp4");
        ProcessBuilder processBuilder = new ProcessBuilder();
        List<String> params = new ArrayList<>();
        params.add("./lib/ffmpeg.exe");
        params.add("-i");
        params.add(tempVideoFile.getAbsolutePath());
        params.add("-r");
        params.add("29.97");
        params.add(transform.getAbsolutePath());
        params.add("-y");
        processBuilder.command(params);
        logger.info("执行参数为：{}", params);
        //将标准输入流和错误流合并
        processBuilder.redirectErrorStream(true);
        try {
            //启动一个进程
            Process process = processBuilder.start();
            //通过标准输入流拿到正常错误的信息
            InputStream inputStream = process.getInputStream();
            //转成字符流输出
            InputStreamReader reader = new InputStreamReader(inputStream, "GBK");
            BufferedReader bufferedReader = new BufferedReader(reader);
            //缓冲
            String line;
            //缓冲
            while ((line = bufferedReader.readLine()) != null) {
                logger.info(line);
            }
            logger.info(line);
            //关流
            reader.close();
            inputStream.close();
        } catch (Exception e) {
            logger.error("发生了异常", e);
        }
        if (transform.exists()) {
            logger.info("文件存在");
        } else {
            logger.error("文件不存在。。。");
        }
        //复制到原来的地方
        File resultFile = new File(videoFile.getParent(), transform.getName());
        FileUtil.copy(transform, resultFile, true);
        boolean delete1 = tempVideoFile.delete();
        boolean delete = transform.delete();
        return resultFile.getAbsolutePath();
    }

    /**
     * 视频添加音频mp3
     */
    public String videoAddAudio(String videoPath, String audioPath) {
        // 视频地址
        File videoFile = new File(videoPath);
        File tempVideoFile = new File("./temp/video/" + videoFile.getName().replace("(", "").replace(")", "").replace(",", "_"));
        FileUtil.copy(videoFile, tempVideoFile, true);
        // 音频地址
        File audioFile = new File(audioPath);
        File tempAudioFile = new File("./temp/video/" + audioFile.getName().replace("(", "").replace(")", "").replace(",", "_"));
        FileUtil.copy(audioFile, tempAudioFile, true);

        // 输出文件
        String outPutName = tempVideoFile.getName();
        File outPutFile = new File(tempVideoFile.getParent(), "audio_" + outPutName);
        ProcessBuilder processBuilder = new ProcessBuilder();
        List<String> params = new ArrayList<>();
        params.add("./lib/ffmpeg.exe");
        params.add("-i");
        params.add(tempVideoFile.getAbsolutePath());
        params.add("-i");
        params.add(tempAudioFile.getAbsolutePath());
        params.add("-c");
        params.add("copy");

        params.add("-map");
        params.add("0:v:0");

        params.add("-map");
        params.add("1:a:0");
        params.add(outPutFile.getAbsolutePath());
        params.add("-y");
        processBuilder.command(params);
        logger.info("执行参数为：{}", params);
        //将标准输入流和错误流合并
        processBuilder.redirectErrorStream(true);
        try {
            //启动一个进程
            Process process = processBuilder.start();
            //通过标准输入流拿到正常错误的信息
            InputStream inputStream = process.getInputStream();
            //转成字符流输出
            InputStreamReader reader = new InputStreamReader(inputStream, "GBK");
            BufferedReader bufferedReader = new BufferedReader(reader);
            //缓冲
            String line;
            //缓冲
            while ((line = bufferedReader.readLine()) != null) {
                logger.info(line);
            }
            logger.info(line);
            //关流
            reader.close();
            inputStream.close();
        } catch (Exception e) {
            logger.error("发生了异常", e);
        }
        if (outPutFile.exists()) {
            logger.info("文件存在");
        } else {
            logger.error("文件不存在。。。");
        }
        //复制到原来的地方
        File resultFile = new File(videoFile.getParent(), outPutFile.getName());
        FileUtil.copy(outPutFile, resultFile, true);
        boolean delete1 = tempVideoFile.delete();
        boolean delete = outPutFile.delete();
        boolean delete2 = tempAudioFile.delete();
        return resultFile.getAbsolutePath();
    }

    /**
     * 将mp4转换为ts
     */
    public String mp4ToTs(String path) {
        File videoFile = new File(path);
        File tempVideoFile = new File("./temp/video/" + videoFile.getName().replace("(", "").replace(")", "").replace(",", "_").replace(" ", ""));
        FileUtil.copy(videoFile, tempVideoFile, true);
        // 输出文件
        String splitName = tempVideoFile.getName();
        File transform = new File(tempVideoFile.getParent(), splitName.split("\\.")[0] + ".ts");
        logger.info("tempVideoFile:{} transform:{}", tempVideoFile, transform);
        ProcessBuilder processBuilder = new ProcessBuilder();
        List<String> params = new ArrayList<>();
        params.add("./lib/ffmpeg.exe");
        params.add("-i");
        params.add(tempVideoFile.getAbsolutePath());
        params.add("-vcodec");
        params.add("copy");
        params.add("-acodec");
        params.add("aac");
        params.add("-vbsf");
        params.add("h264_mp4toannexb ");
        params.add(transform.getAbsolutePath());
        params.add("-y");
        processBuilder.command(params);
        logger.info("执行参数为：{}", params);
        //将标准输入流和错误流合并
        processBuilder.redirectErrorStream(true);
        try {
            //启动一个进程
            Process process = processBuilder.start();
            //通过标准输入流拿到正常错误的信息
            InputStream inputStream = process.getInputStream();
            //转成字符流输出
            InputStreamReader reader = new InputStreamReader(inputStream, "GBK");
            BufferedReader bufferedReader = new BufferedReader(reader);
            //缓冲
            String line;
            //缓冲
            while ((line = bufferedReader.readLine()) != null) {
                logger.info(line);
            }
            logger.info(line);
            //关流
            reader.close();
            inputStream.close();
        } catch (Exception e) {
            logger.error("发生了异常", e);
        }
        if (transform.exists()) {
            logger.info("文件存在");
        } else {
            logger.error("文件不存在。。。");
        }
        //复制到原来的地方
        File resultFile = new File(videoFile.getParent(), transform.getName());
        FileUtil.copy(transform, resultFile, true);
        logger.info("resultFile:{} transform:{}", resultFile, transform);
        boolean delete1 = tempVideoFile.delete();
        boolean delete = transform.delete();
        return resultFile.getAbsolutePath();
    }

    public void genCCFile(String videoFile, String ccFile) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(new File("./temp"));
        processBuilder.redirectErrorStream(true);
        logger.info("参数：{}|{}", videoFile, ccFile);
        processBuilder.command("./whiper/test.exe", videoFile, ccFile);
        try {
            final Process process = processBuilder.start();
            final InputStream inputStream = process.getInputStream();
            final InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            final BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                logger.info("{}", line);
            }
        } catch (Exception e) {
            logger.info("产生异常:", e);
            throw new Exception("翻译字幕异常",e);
        }
    }
}
