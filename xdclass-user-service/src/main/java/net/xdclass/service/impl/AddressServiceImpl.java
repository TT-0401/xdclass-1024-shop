package net.xdclass.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.enums.AddressStatusEnum;
import net.xdclass.interceptor.LoginInterceptor;
import net.xdclass.mapper.AddressMapper;
import net.xdclass.model.AddressDO;
import net.xdclass.model.LoginUser;
import net.xdclass.request.AddressAddRequest;
import net.xdclass.service.AddressService;
import net.xdclass.vo.AddressVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AddressServiceImpl implements AddressService {

    @Resource
    private AddressMapper addressMapper;


    /**
     * 新增收货地址
     *  1.是否设置为默认收货地址
     *  2.将该地址存到数据库中
     * @param addressAddRequest
     */
    @Override
    public void add(AddressAddRequest addressAddRequest) {

        // 获取当前登录用户对象
        LoginUser loginUser = LoginInterceptor.threadLocal.get();

        // 赋值地址对象的属性
        // 根据当前登录用户的信息和新增地址的信息，创建地址的数据库对象 addressDO
        AddressDO addressDO = new AddressDO();

        addressDO.setCreateTime(new Date());
        addressDO.setUserId(loginUser.getId());
        BeanUtils.copyProperties(addressAddRequest,addressDO);

        // 该新增地址是否被设置为默认收货地址
        if(addressDO.getDefaultStatus() == AddressStatusEnum.DEFAULT_STATUS.getStatus()){
            // 查询该用户是否已有默认收货地址
            AddressDO defaultAddressDO = addressMapper.selectOne(new QueryWrapper<AddressDO>()
                    .eq("user_id",loginUser.getId())
                    .eq("default_status",AddressStatusEnum.DEFAULT_STATUS.getStatus()));

            // 如果已有默认收货地址
            if(defaultAddressDO != null){
                // 将旧的默认收货地址修改为非默认收货地址
                defaultAddressDO.setDefaultStatus(AddressStatusEnum.COMMON_STATUS.getStatus());
                addressMapper.update(defaultAddressDO,new QueryWrapper<AddressDO>().eq("id",defaultAddressDO.getId()));
            }

            // 如果没有默认收货地址，直接入库
        }

        // 将该新增地址存到数据库中
        int rows = addressMapper.insert(addressDO);

        log.info("新增收货地址:rows={},data={}",rows,addressDO);
    }


    /**
     * 根据地址ID查找地址
     *
     * @param id
     */
    @Override
    public AddressVO detail(Long id) {

        // 【防范水平越权】
        // 建立用户和可操作资源的绑定关系，用户对任何资源进行操作时，通过该绑定关系确保该资源是属于该用户所有的
        // 如查询收货地址时，要确保地址对应的用户ID与当前登录用户ID相匹配！
        LoginUser loginUser = LoginInterceptor.threadLocal.get();
        AddressDO addressDO = addressMapper.selectOne(new QueryWrapper<AddressDO>().eq("id",id).eq("user_id",loginUser.getId()));

        if(addressDO == null){
            return null;
        }

        AddressVO addressVO = new AddressVO();
        BeanUtils.copyProperties(addressDO,addressVO);

        return addressVO;
    }


    /**
     * 根据地址ID删除地址
     * @param addressId
     * @return
     */
    @Override
    public int del(int addressId) {
        // 【防范水平越权】
        LoginUser loginUser = LoginInterceptor.threadLocal.get();
        int rows = addressMapper.delete(new QueryWrapper<AddressDO>().eq("id",addressId).eq("user_id",loginUser.getId()));
        return rows;
    }


    /**
     * 查询用户的全部收货地址
     * @return
     */
    @Override
    public List<AddressVO> listUserAllAddress() {
        LoginUser loginUser = LoginInterceptor.threadLocal.get();
        List<AddressDO> list = addressMapper.selectList(new QueryWrapper<AddressDO>().eq("user_id",loginUser.getId()));

        List<AddressVO> addressVOList =  list.stream().map(obj -> {
            AddressVO addressVO = new AddressVO();
            BeanUtils.copyProperties(obj, addressVO);
            return addressVO;
        }).collect(Collectors.toList());

        return addressVOList;
    }

}
