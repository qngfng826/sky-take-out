package com.sky.controller.admin;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.constant.RedisKeyConstant;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.utils.CacheClient;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜品管理
 */
@RestController
@RequestMapping("/admin/dish")
@Api(tags = "菜品管理")
@Slf4j
public class DishController {

    @Autowired
    private DishService dishService;
    @Autowired
    private CacheClient cacheClient;

    /**
     * 新增菜品
     * @param dishDTO
     * @return
     */
    @PostMapping
    @ApiOperation("新增菜品")
    public Result save(@RequestBody DishDTO dishDTO){
        log.info("新增菜品：{}", dishDTO);
        dishService.saveWithFlavor(dishDTO);

        // 【改造①】清缓存：新增菜品只影响该分类的 C 端列表缓存，精确删除这一个 key 即可
        cacheClient.evict(RedisKeyConstant.CACHE_DISH_KEY + dishDTO.getCategoryId());

        return Result.success();
    }

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("菜品分页查询")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO){
        log.info("菜品分页查询：{}", dishPageQueryDTO);
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 菜品批量删除
     * @param ids
     * @return
     */
    @DeleteMapping
    @ApiOperation("菜品批量删除")
    public Result<String> delete(@RequestParam List<Long> ids){
        log.info("菜品批量删除：{}", ids);
        dishService.deleteBatch(ids);

        // 【改造①】清缓存：批量/修改/状态操作无法确定涉及哪些分类，按前缀全清（SCAN 代替 KEYS，避免阻塞 Redis）
        cacheClient.evictByPrefix(RedisKeyConstant.CACHE_DISH_KEY);

        return Result.success();
    }

    /**
     * 根据id查询菜品信息
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询菜品信息")
    public Result<DishVO> getById(@PathVariable Long id){
        log.info("根据id查询菜品信息：{}", id);
        DishVO dishVO = dishService.getByIdWithFlavor(id);
        return Result.success(dishVO);
    }

    /**
     * 修改菜品
     * @param dishDTO
     * @return
     */
    @PutMapping
    @ApiOperation("修改菜品")
    public Result update(@RequestBody DishDTO dishDTO){
        log.info("修改菜品：{}", dishDTO);
        dishService.updateWithFlavor(dishDTO);

        // 【改造①】清缓存：批量/修改/状态操作无法确定涉及哪些分类，按前缀全清（SCAN 代替 KEYS，避免阻塞 Redis）
        cacheClient.evictByPrefix(RedisKeyConstant.CACHE_DISH_KEY);

        return Result.success();
    }

    /**
     * 菜品起售停售
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("菜品起售停售")
    public Result<String> startOrStop(@PathVariable Integer status, Long id){
        dishService.startOrStop(status,id);

        // 【改造①】清缓存：批量/修改/状态操作无法确定涉及哪些分类，按前缀全清（SCAN 代替 KEYS，避免阻塞 Redis）
        cacheClient.evictByPrefix(RedisKeyConstant.CACHE_DISH_KEY);

        return Result.success();
    }

    /**
     * 根据分类id查询菜品
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<Dish>> list(Long categoryId){
        List<Dish> list = dishService.list(categoryId);
        return Result.success(list);
    }
}
