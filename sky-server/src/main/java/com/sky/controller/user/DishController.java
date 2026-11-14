package com.sky.controller.user;

import com.sky.constant.RedisKeyConstant;
import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.utils.CacheClient;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {

    /** 菜品缓存过期时间（分钟）。注：固定 TTL 存在"同一时刻集中失效"的雪崩隐患，
     * 可在此基础上加随机偏移量打散，黑马点评课程会展开讲 */
    private static final long CACHE_DISH_TTL = 30;

    @Autowired
    private DishService dishService;
    @Autowired
    private CacheClient cacheClient;

    /**
     * 根据分类id查询菜品
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {

        // 【改造①】原版用裸 RedisTemplate 读写缓存（key 为 dish_categoryId），有三个问题：
        //   1) 缓存穿透：恶意请求不存在的分类 id，每次都穿透缓存打到数据库
        //   2) 缓存击穿：热点 key 过期瞬间，大量并发同时查库重建
        //   3) 空结果不缓存：list.size()==0 时下次还会查库
        // 改为统一走 CacheClient：空值缓存防穿透 + 互斥锁防击穿，空结果也会缓存
        List<DishVO> list = cacheClient.queryWithPassThroughList(
                RedisKeyConstant.CACHE_DISH_KEY, categoryId, DishVO.class,
                id -> {
                    Dish dish = new Dish();
                    dish.setCategoryId(id);
                    dish.setStatus(StatusConstant.ENABLE);// 查询起售中的菜品
                    return dishService.listWithFlavor(dish);
                },
                CACHE_DISH_TTL, TimeUnit.MINUTES);

        return Result.success(list);
    }

}
