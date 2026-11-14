package com.sky.controller.user;

import com.sky.constant.RedisKeyConstant;
import com.sky.constant.StatusConstant;
import com.sky.entity.Setmeal;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.utils.CacheClient;
import com.sky.vo.DishItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController("userSetmealController")
@RequestMapping("/user/setmeal")
@Api(tags = "C端-套餐浏览接口")
public class SetmealController {

    /** 套餐缓存过期时间（分钟） */
    private static final long CACHE_SETMEAL_TTL = 30;

    @Autowired
    private SetmealService setmealService;
    @Autowired
    private CacheClient cacheClient;

    /**
     * 条件查询
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询套餐")
    public Result<List<Setmeal>> list(Long categoryId) {
        // 【改造①】原版用 Spring Cache 的 @Cacheable 缓存套餐，两个问题：
        //   1) 默认的 RedisCacheConfiguration 没有过期时间，缓存写进去永不失效
        //   2) 注解模型做不了穿透/击穿防护（无法缓存空值、拿不到互斥锁）
        // 改为统一走 CacheClient：空值缓存 + 互斥锁，且 key 与管理端清理逻辑对齐
        List<Setmeal> list = cacheClient.queryWithPassThroughList(
                RedisKeyConstant.CACHE_SETMEAL_KEY, categoryId, Setmeal.class,
                id -> {
                    Setmeal setmeal = new Setmeal();
                    setmeal.setCategoryId(id);
                    setmeal.setStatus(StatusConstant.ENABLE);
                    return setmealService.list(setmeal);
                },
                CACHE_SETMEAL_TTL, TimeUnit.MINUTES);
        return Result.success(list);
    }

    /**
     * 根据套餐id查询包含的菜品列表
     *
     * @param id
     * @return
     */
    @GetMapping("/dish/{id}")
    @ApiOperation("根据套餐id查询包含的菜品列表")
    public Result<List<DishItemVO>> dishList(@PathVariable("id") Long id) {
        List<DishItemVO> list = setmealService.getDishItemById(id);
        return Result.success(list);
    }
}
