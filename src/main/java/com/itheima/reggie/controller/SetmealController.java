package com.itheima.reggie.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.reggie.dto.DishDto;
import com.itheima.reggie.dto.SetmealDto;
import com.itheima.reggie.pojo.Category;
import com.itheima.reggie.pojo.R;
import com.itheima.reggie.pojo.Setmeal;
import com.itheima.reggie.service.CategoryService;
import com.itheima.reggie.service.SetmealService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@Slf4j
@RequestMapping("/setmeal")
public class SetmealController {
    @Autowired
    private SetmealService setmealService;
    @Autowired
    private CategoryService categoryService;


    /**
     * redisCache常用注解说明
     *
     * @CachePut：将方法返回值添加到缓存中
     * @CacheEvict： 删除指定分类的数据
     * @Cacheable： 判断指定缓存中是否有数据，有则返回，否则将会执行查询语句，将数据存入指定缓存
     * <p>
     * 参数：
     * value：分类名
     * key：缓存数据key值
     * allEntries：搭配CacheEvict 是否删除全部，默认为false
     * condition/unless：什么情况会执行与什么情况不执行
     */

    @CacheEvict(value = "setmealCache", allEntries = true)
    @PostMapping
    public R<String> save(@RequestBody SetmealDto setmealDto) {
        log.info("保存套餐{}", setmealDto);
        setmealService.saveWithDish(setmealDto);
        return R.success("套餐保存成功");
    }

    @GetMapping("page")
    public R<Page> page(Integer page, Integer pageSize, String name) {
        Page<Setmeal> setmealPage = new Page<>(page, pageSize);
        //排序模糊查询name与降序排列修改时间
        LambdaQueryWrapper<Setmeal> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.like(name != null, Setmeal::getName, name);
        lambdaQueryWrapper.orderByDesc(Setmeal::getUpdateTime);
        setmealService.page(setmealPage, lambdaQueryWrapper);

        //拷贝
        Page<SetmealDto> setmealDtoPage = new Page<>();
        BeanUtils.copyProperties(setmealPage, setmealDtoPage, "records");

        //遍历修改page原集合数据类型并添加到新page
        List<Setmeal> records = setmealPage.getRecords();
        List<SetmealDto> setmealDtoList = records.stream().map((item) -> {
            //创建一个setmealDto对象用于拷贝
            SetmealDto setmealDto = new SetmealDto();
            BeanUtils.copyProperties(item, setmealDto);
            //查询分类名称并添加到新对象中
            Category byId = categoryService.getById(item.getCategoryId());
            setmealDto.setCategoryName(byId.getName());
            return setmealDto;
        }).collect(Collectors.toList());
        setmealDtoPage.setRecords(setmealDtoList);
        return R.success(setmealDtoPage);

    }

    @CacheEvict(value = "setmealCache", allEntries = true)
    //allEntries：是否删除setmealCache分类下所有数据，默认为false
    @DeleteMapping
    public R<String> delete(@RequestParam List<Long> ids) {
        log.info("删除{}", ids);
        setmealService.removeWithDish(ids);

        return R.success("删除成功");
    }

    //根据id查询数据回显
    @GetMapping("/{id}")

    public R<SetmealDto> getDataById(@PathVariable Long id) {
        SetmealDto byIdWithDish = setmealService.getByIdWithDish(id);
        return R.success(byIdWithDish);
    }

    //修改套餐状态
    @CacheEvict(value = "setmealCache", allEntries = true)
    @PostMapping("/status/{status}")
    public R<String> updateStatus(@PathVariable Integer status, @RequestParam List<Long> ids) {
        log.info("修改{}状态为{}", ids, status);
        //update setmeal set status=1 where id in(1,2,3)
        //创建一个setmeal类，将要修改的数据存入
        Setmeal setmeal = new Setmeal();
        setmeal.setStatus(status);
        //条件查询
        LambdaQueryWrapper<Setmeal> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.in(Setmeal::getId, ids);
        setmealService.update(setmeal, lambdaQueryWrapper);
        //要修改的数据以及判断条件
        return R.success("状态修改成功");
    }

    @Cacheable(value = "setmealCache", key = "#setmeal.categoryId+'_'+#setmeal.status")
    @GetMapping("/list")
    public R<List<Setmeal>> list(Setmeal setmeal) {
        log.info("查询套餐信息{}", setmeal);
        LambdaQueryWrapper<Setmeal> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(setmeal.getCategoryId() != null, Setmeal::getCategoryId, setmeal.getCategoryId());
        lambdaQueryWrapper.eq(setmeal.getStatus() != null, Setmeal::getStatus, setmeal.getStatus());
        lambdaQueryWrapper.orderByDesc(Setmeal::getUpdateTime);
        List<Setmeal> list = setmealService.list(lambdaQueryWrapper);

        return R.success(list);

    }

    @CacheEvict(value = "setmealCache", allEntries = true)
    @PutMapping
    public R<String> update(@RequestBody SetmealDto setmealDto) {

        log.info("修改套餐数据{}", setmealDto);

        setmealService.updateWithDish(setmealDto);

        return R.success("套餐修改成功");


    }

}
