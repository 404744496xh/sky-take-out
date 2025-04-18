package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.enumeration.OperationType;
import com.sky.vo.DishVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DishMapper {

    /**
     * 根据分类id查询菜品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    /**
     * 新增菜品
     * @param dish
     */
    @AutoFill(value = OperationType.INSERT)
    void insert(Dish dish);

    /**
     * 分页查询菜品
     * @param dishPageQueryDTO
     * @return
     */
    Page<DishVO> pageQuery(DishPageQueryDTO dishPageQueryDTO);

    /**
     * 根据id查询菜品
     * @param id
     * @return
     */
    @Select("select * from dish where id = #{id}")
    Dish getById(Long id);


    /**
     * 根据id删除菜品
     * @param id
     */
    @Delete("delete from dish where id in (#{id})")
    void deleteById(Long id);

    /**
     * 根据id批量删除菜品
     * @param ids
     */
    void deleteByIds(List<Long> ids);

    /**
     * 修改菜品
     * @param dish
     */
    @AutoFill(value = OperationType.UPDATE)
    void update(Dish dish);

    /**
     * 根据分类id查询菜品
     * @param dish
     * @return
     */
    List<Dish> getByCategoryId(Dish dish);

    /**
     * 根据套餐id查询菜品
     * @param setmealId
     * @return
     */
    @Select("select dish.* from dish left join setmeal_dish as sd on sd.dish_id=dish.id where sd.setmeal_id=#{setmealId}")
    List<Dish> getBySetmealId(Long setmealId);

    /**
     * 修改菜品起售停售
     * @param status
     * @param id
     */
    @Update("update dish set status=#{status} where id=#{id}")
    void updateStatus(Integer status, Long id);
}
