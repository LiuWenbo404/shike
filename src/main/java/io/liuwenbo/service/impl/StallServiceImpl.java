package io.liuwenbo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.liuwenbo.entity.Stall;
import io.liuwenbo.mapper.StallMapper;
import io.liuwenbo.service.StallService;
import org.springframework.stereotype.Service;

@Service
public class StallServiceImpl extends ServiceImpl<StallMapper, Stall> implements StallService {
}
