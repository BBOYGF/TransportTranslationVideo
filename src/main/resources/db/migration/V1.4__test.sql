-- 开头视频模板里的两行标题，选图片自动生成开头视频时使用
ALTER TABLE last_time
    ADD COLUMN begin_title1 TEXT;
ALTER TABLE last_time
    ADD COLUMN begin_title2 TEXT;
