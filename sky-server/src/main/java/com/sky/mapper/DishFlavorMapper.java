package com.sky.mapper;


import com.sky.entity.DishFlavor;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;


@Mapper
public interface DishFlavorMapper {
    /**
     * 批量插入
     * @param flavors
     */
    void insertBatch(List<DishFlavor> flavors);

    /**
     * 根据菜品id查询删除菜品口味数据
     * @param dishId
     * @return
     */
    @Delete("delete from dish_flavor where dish_id = #{dishId}")
    void deleteByDishIds(Long dishId);

    @Select("select * from dish_flavor where dish_id = #{dishId}")
    List<DishFlavor> getByDishId(Long dishId);
}
