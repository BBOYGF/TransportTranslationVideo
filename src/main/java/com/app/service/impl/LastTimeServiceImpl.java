package com.app.service.impl;

import com.app.pojo.LastTime;
import com.app.mapper.LastTimeMapper;
import com.app.service.ILastTimeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author guofan
 * @since 2023-11-25
 */
@Service
public class LastTimeServiceImpl extends ServiceImpl<LastTimeMapper, LastTime> implements ILastTimeService {

}
