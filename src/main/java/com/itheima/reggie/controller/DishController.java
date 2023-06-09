package com.itheima.reggie.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.reggie.dto.DishDto;
import com.itheima.reggie.pojo.*;
import com.itheima.reggie.service.CategoryService;
import com.itheima.reggie.service.DishFlavorService;
import com.itheima.reggie.service.DishService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

@RestController
@Slf4j
@RequestMapping("/dish")
public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private DishFlavorService dishFlavorService;
    @Autowired
    private RedisTemplate redisTemplate;

    @PostMapping
    public R<String> save(@RequestBody DishDto dishDto) {
        log.info("保存菜品{}", dishDto);

        dishService.saveWithFlavor(dishDto);
        //数据库数据发生改变时则需要将缓存数据进行更新，清空缓存，重新加载，避免数据不一致

        //清理所有分类缓存（方便）
        Set keys = redisTemplate.keys("dish_*");
        redisTemplate.delete(keys);

        //清理请求分类缓存（精确）
        //String key = "dish_" + dishDto.getCategoryId() + "_" + dishDto.getStatus();
        //redisTemplate.delete(key);
        return R.success("菜品保存成功");
    }

    @GetMapping("/page")
    public R<Page> page(Integer page, Integer pageSize, String name) {
        log.info("page={},pageSize={},name={}", page, pageSize, name);
        Page<Dish> dishPage = new Page<>(page, pageSize);
        //普通dish实体类中没有分类名,前台界面无法显示分类所以使用其子类dishDto
        Page<DishDto> dishDtoPage = new Page<>();
        BeanUtils.copyProperties(dishPage, dishDtoPage, "records");
        //创建一个新的page对象，将dishPage对象拷贝过去
        // records为查询后封装的对象集合（同时类型不一致需要对其进行处理），因此无需拷贝
        LambdaQueryWrapper<Dish> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.like(name != null, Dish::getName, name);
        lambdaQueryWrapper.orderByDesc(Dish::getUpdateTime);
        //进行dish表的条件查询与排序
        dishService.page(dishPage, lambdaQueryWrapper);
        //会将查询到的数据封装到dishPage中的records集合中
        List<Dish> records = dishPage.getRecords();
        //将集合获取出来进行处理
        List<DishDto> list = records.stream().map(dish -> {
            DishDto dishDto = new DishDto();
            BeanUtils.copyProperties(dish, dishDto);
            //由于dish实体类中没有categoryName属性
            //因此需要二次拷贝，新建dishDto实体类，将数据拷贝到dishDto中
            Category byId = categoryService.getById(dish.getCategoryId());
            //调用dish获取CategoryId，用于在category表中查询分类名，这里获取的数据对象
            if (byId != null) {
                dishDto.setCategoryName(byId.getName());
                //使用数据对象获取分类名，将其设置在dishDto中
            }

            return dishDto;
            //返回

        }).collect(Collectors.toList());
        //生成一个处理后的集合
        dishDtoPage.setRecords(list);
        //将处理后的集合设置在新的page对象中进行返回
        return R.success(dishDtoPage);
    }

    //根据id查询菜品数据
    @GetMapping("/{id}")

    public R<DishDto> getDataById(@PathVariable Long id) {
        DishDto byIdWithFlavor = dishService.getByIdWithFlavor(id);
        return R.success(byIdWithFlavor);
    }

    //修改数据
    @PutMapping
    public R<String> update(@RequestBody DishDto dishDto) {
        log.info("修改菜品{}", dishDto);

        dishService.updateWithFlavor(dishDto);
        //数据库数据发生改变时则需要将缓存数据进行更新，清空缓存，重新加载，避免数据不一致

        //清理所有分类缓存（方便）
        Set keys = redisTemplate.keys("dish_*");
        redisTemplate.delete(keys);

        //清理请求分类缓存（精确）
        //String key = "dish_" + dishDto.getCategoryId() + "_" + dishDto.getStatus();
        //redisTemplate.delete(key);

        return R.success("菜品修改成功");
    }


    @PostMapping("/status/{status}")
    public R<String> updateStatus(@PathVariable Integer status, @RequestParam List<Long> ids) {
        log.info("修改{}状态为{}", ids, status);
        //update dish set status=1/0 where id in(1,2,3)
        LambdaUpdateWrapper<Dish> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        //这里换了一种方法修改
        lambdaUpdateWrapper.in(Dish::getId, ids);
        lambdaUpdateWrapper.set(Dish::getStatus, status);
        dishService.update(lambdaUpdateWrapper);
        //数据库数据发生改变时则需要将缓存数据进行更新，清空缓存，重新加载，避免数据不一致

        //清理所有分类缓存（方便）
        Set keys = redisTemplate.keys("dish_*");
        redisTemplate.delete(keys);

        //清理请求分类缓存（精确）
        //String key = "dish_" + dishDto.getCategoryId() + "_" + dishDto.getStatus();
        //redisTemplate.delete(key);

        return R.success("状态修改成功");
    }


    @DeleteMapping
    public R<String> delete(@RequestParam List<Long> ids) {
        log.info("删除{}", ids);
        dishService.removeWithFlavor(ids);
        //数据库数据发生改变时则需要将缓存数据进行更新，清空缓存，重新加载，避免数据不一致

        //清理所有分类缓存（方便）
        Set keys = redisTemplate.keys("dish_*");
        redisTemplate.delete(keys);

        //清理请求分类缓存（精确）
        //String key = "dish_" + dishDto.getCategoryId() + "_" + dishDto.getStatus();
        //redisTemplate.delete(key);
        return R.success("删除成功");

    }

    //////////////////////////////客户端///////////////////////////////////
    //根据分类id查询菜品
    @GetMapping("/list")
    public R<List<DishDto>> list(Dish dish) {
        List<DishDto> list1 = null;
        //使用redis进行菜品缓存

        String key = "dish_" + dish.getCategoryId() + "_" + dish.getStatus();

        list1 = (List<DishDto>) redisTemplate.opsForValue().get(key);
        //判断缓存中是否有菜品数据，有则返回，没有则去数据库获取

        if (list1 != null) {
            return R.success(list1);
        }


        //构造查询条件
        LambdaQueryWrapper<Dish> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(dish.getCategoryId() != null, Dish::getCategoryId, dish.getCategoryId());
        //状态为1（起售状态）
        lambdaQueryWrapper.eq(Dish::getStatus, 1);
        lambdaQueryWrapper.orderByAsc(Dish::getSort).orderByDesc(Dish::getUpdateTime);
        //排序
        List<Dish> list = dishService.list(lambdaQueryWrapper);
        list1 = list.stream().map(item -> {
            DishDto dishDto = new DishDto();
            BeanUtils.copyProperties(item, dishDto);
            Category byId = categoryService.getById(item.getCategoryId());
            if (byId != null) {
                dishDto.setCategoryName(byId.getName());
            }
            LambdaQueryWrapper<DishFlavor> lambdaQueryWrapper1 = new LambdaQueryWrapper<>();
            lambdaQueryWrapper1.eq(DishFlavor::getDishId, item.getId());

            List<DishFlavor> list2 = dishFlavorService.list(lambdaQueryWrapper1);
            dishDto.setFlavors(list2);
            return dishDto;

        }).collect(Collectors.toList());
        redisTemplate.opsForValue().set(key, list1, 60, TimeUnit.MINUTES);
        //一个小时有效时间
        return R.success(list1);
    }
}
