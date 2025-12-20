package com.app.util;

public class FileNameUtils {
    /**
     * 清洗文件名，去除操作系统不允许的特殊字符
     * @param name 原始字符串（例如视频标题）
     * @return 安全的文件名
     */
    public static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed_file_" + System.currentTimeMillis();
        }

        // 1. 替换 Windows/Linux/Mac 文件系统非法字符
        // 非法字符包括：\ / : * ? " < > |
        // 我们把它们替换成下划线 "_"
        String safeName = name.replaceAll("[\\\\/:*?\"<>|]", "_");

        // 2. 去除控制字符 (如换行符 \n, 制表符 \t 等)
        safeName = safeName.replaceAll("[\\x00-\\x1f]", "");

        // 3. 处理文件名过长 (Windows 路径总长度限制通常为 260，单独文件名建议限制在 200 以内)
        if (safeName.length() > 200) {
            safeName = safeName.substring(0, 200);
        }

        // 4. 去除首尾的空格和点 (Windows 不喜欢文件名以点或空格结尾)
        safeName = safeName.trim();
        while (safeName.endsWith(".") || safeName.endsWith(" ")) {
            safeName = safeName.substring(0, safeName.length() - 1);
        }

        // 5. 再次检查是否为空 (防止标题全是特殊字符被替换光了)
        if (safeName.isEmpty()) {
            return "video_" + System.currentTimeMillis();
        }

        return safeName;
    }
}
