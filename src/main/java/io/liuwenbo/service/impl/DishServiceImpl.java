package io.liuwenbo.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.liuwenbo.entity.Dish;
import io.liuwenbo.entity.Stall;
import io.liuwenbo.mapper.DishMapper;
import io.liuwenbo.service.DishService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements DishService {
    /**
     * 根据档口id查询菜品
     * @param stallId
     * @return
     */
    @Override
    public List<Dish> listByStallId(Long stallId) {
        return lambdaQuery()
                .eq(Dish::getStallId, stallId)
                .list();
    }
}
