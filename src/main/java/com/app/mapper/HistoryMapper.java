package com.app.mapper;

import com.app.po.History;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 黑名单mapper
 *
 * @Author guofan
 * @Create 2022/2/20
 */
@Mapper
public interface HistoryMapper extends BaseMapper<History> {
}
