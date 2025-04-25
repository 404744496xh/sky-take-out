package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    SetmealMapper setmealMapper;

    @Autowired
    SetmealDishMapper setmealDishMapper;

    @Autowired
    DishMapper dishMapper;

    /**
     * 保存套餐和套餐菜品关系
     * @param setmealDTO
     */
    @Override
    public void save(SetmealDTO setmealDTO) {
        // 保存套餐基本信息
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        setmealMapper.insert(setmeal);

        // 保存套餐菜品关系
        Long id = setmeal.getId();//获取套餐id
        SetmealDish setmealDish = new SetmealDish();
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        setmealDishes.forEach(item -> {
            item.setSetmealId(id);
        });
        setmealDishMapper.insertBatchs(setmealDishes);
    }

    /**
     * 分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(),setmealPageQueryDTO.getPageSize());
        Page<Setmeal> page = setmealMapper.pageQeury(setmealPageQueryDTO);
        return new PageResult(page.getTotal(),page.getResult());
    }

    /**
     * 根据id查询套餐
     * @param id
     * @return
     */
    @Override
    public SetmealVO getById(Long id) {
        SetmealVO setmealVO = new SetmealVO();

        // 查询套餐基本信息
        BeanUtils.copyProperties(setmealMapper.getById(id),setmealVO);

        // 查询套餐菜品信息
        setmealVO.setSetmealDishes(setmealDishMapper.getBySetmealId(id));

        return setmealVO;
    }

    /**
     * 批量删除套餐
     * @param ids
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        // 判断当前菜品能否被删除
        for (Long id : ids){
            Setmeal setmeal = setmealMapper.getById(id);
            if (setmeal.getStatus() == 1){
                //起售中的菜品不能删除
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }

//        ids.forEach(id -> {
//            // 删除套餐表中的数据
//            setmealMapper.deleteById(id);
//            // 删除套餐菜品关系表中的数据
//            setmealDishMapper.deleteBySetmealId(id);
//        });

        //批量删除套餐菜品关系
        setmealDishMapper.deleteBySetmealIds(ids);

        // 批量删除套餐
        setmealMapper.deleteByIds(ids);
    }

    /**
     * 修改套餐起售停售
     * @param status
     * @param id
     */
    @Override
    public void updateStatus(Integer status, Long id) {
        // 修改套餐状态为起售时要查看该套餐是否关联了未起售的菜品
        if(status == StatusConstant.ENABLE){
            dishMapper.getBySetmealId(id).forEach(dish -> {
                if(dish.getStatus() == StatusConstant.DISABLE){
                    throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
                }
            });
        }
        setmealMapper.updateStatus(status,id);
    }

    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }
}
