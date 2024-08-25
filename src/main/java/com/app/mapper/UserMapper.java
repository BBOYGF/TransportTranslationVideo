package com.app.mapper;

import com.app.po.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @Author guofan
 * @Create 2022/1/22
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    List<User> getUserList();
}
