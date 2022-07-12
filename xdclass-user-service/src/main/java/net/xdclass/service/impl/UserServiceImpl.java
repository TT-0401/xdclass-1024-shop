package net.xdclass.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.enums.SendCodeEnum;
import net.xdclass.fegin.CouponFeignService;
import net.xdclass.interceptor.LoginInterceptor;
import net.xdclass.mapper.UserMapper;
import net.xdclass.model.LoginUser;
import net.xdclass.model.UserDO;
import net.xdclass.request.NewUserCouponRequest;
import net.xdclass.request.UserLoginRequest;
import net.xdclass.request.UserRegisterRequest;
import net.xdclass.service.NotifyService;
import net.xdclass.service.UserService;
import net.xdclass.util.CommonUtil;
import net.xdclass.util.JWTUtil;
import net.xdclass.util.JsonData;
import net.xdclass.vo.UserVO;
import org.apache.commons.codec.digest.Md5Crypt;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private NotifyService notifyService;

    @Resource
    private UserMapper userMapper;

    @Autowired
    private CouponFeignService couponFeignService;

    /**
     * 用户注册方法
     *
     * * 校验邮箱验证码
     * * 密码加密
     * * 账号唯一性检查（自定义私有方法）
     * * 添加到数据库
     * * 新注册用户福利发放（自定义私有方法）
     *
     * @param registerRequest
     */
    @Override
    @Transactional(rollbackFor=Exception.class, propagation=Propagation.REQUIRED)
    //@GlobalTransactional
    public JsonData register(UserRegisterRequest registerRequest) {

        boolean checkCode = false;
        // 校验注册邮箱验证码
        if (StringUtils.isNotBlank(registerRequest.getMail())) {
            checkCode = notifyService.checkCode(SendCodeEnum.USER_REGISTER, registerRequest.getMail(), registerRequest.getCode());
        }

        // 验证码不一致，返回错误信息
        if (!checkCode) {
            return JsonData.buildResult(BizCodeEnum.CODE_ERROR);
        }

        // 用户对象
        UserDO userDO = new UserDO();

        // 将源对象的属性拷贝到目标对象中
        BeanUtils.copyProperties(registerRequest, userDO);

        // 补足仍需设置的对象属性
        userDO.setCreateTime(new Date());
        userDO.setSlogan("人生需要动态规划，学习需要贪心算法");

        // 先生成密码所加的盐
        userDO.setSecret("$1$" + CommonUtil.getStringNumRandom(8));

        // 再 密码 + 盐 处理
        String cryptPwd = Md5Crypt.md5Crypt(registerRequest.getPwd().getBytes(), userDO.getSecret());
        userDO.setPwd(cryptPwd);

        // 高并发下账号唯一性保证：数据库唯一索引(建表的时间已经添加) ==> ALTER TABLE user ADD unique(`mail`)
        // 账号唯一性检查
        if (checkUnique(userDO.getMail())) {
            // 将用户对象添加到数据库
            int rows = userMapper.insert(userDO);
            log.info("rows:{},注册成功:{}", rows, userDO);

            // 新用户注册成功，初始化信息，发放福利等
            userRegisterInitTask(userDO);


            // 模拟异常，触发分布式事务回滚
            // int b = 1/0;


            return JsonData.buildSuccess();
        } else {
            return JsonData.buildResult(BizCodeEnum.ACCOUNT_REPEAT);
        }
    }

    /**
     * 用户登录方法
     *  1、根据邮箱去找有没有这条记录
     *  2、有的话，则对 秘钥 + 用户传递的明文密码 进行加密，再和数据库中的密文进行匹配
     *
     * @param userLoginRequest
     */
    @Override
    public JsonData login(UserLoginRequest userLoginRequest) {

        // 根据用户登录请求对象中的邮箱在数据库中查找对应的用户对象
        List<UserDO> userDOList = userMapper.selectList(new QueryWrapper<UserDO>().eq("mail",userLoginRequest.getMail()));

        // 是否存在该用户
        if(userDOList!=null && userDOList.size()==1){
            // 已经注册
            UserDO userDO = userDOList.get(0);
            // 对用户登录请求传来的密码加盐后MD5加密
            String cryptPwd = Md5Crypt.md5Crypt(userLoginRequest.getPwd().getBytes(),userDO.getSecret());
            // 是否与数据库中的密文匹配
            if(cryptPwd.equals(userDO.getPwd())){
                // 密码正确
                // 登录成功，生成token
                LoginUser loginUser = LoginUser.builder().build();
                BeanUtils.copyProperties(userDO,loginUser);

                String accessToken = JWTUtil.geneJsonWebToken(loginUser);

                // accessToken
                // accessToken的过期时间
                // UUID生成一个token
                // String refreshToken = CommonUtil.generateUUID();
                // redisTemplate.opsForValue().set(refreshToken,"1",1000*60*60*24*30);

                return JsonData.buildSuccess(accessToken);

            }else {
                // 密码错误
                return JsonData.buildResult(BizCodeEnum.ACCOUNT_PWD_ERROR);
            }
        }else {
            // 账号未注册
            return JsonData.buildResult(BizCodeEnum.ACCOUNT_UNREGISTER);
        }
    }


    /**
     * 查询用户详细信息
     */
    @Override
    public UserVO findUserDetail() {

        // 从登录拦截器的threadLocal中获取到对应的登录用户信息
        LoginUser loginUser = LoginInterceptor.threadLocal.get();

        // 从数据库获取用户详细信息
        UserDO userDO = userMapper.selectOne(new QueryWrapper<UserDO>().eq("id", loginUser.getId()));

        // 将脱敏信息传给前端：用户数据库对象 -> 用户表现层对象
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(userDO, userVO);
        return userVO;
    }


    // 校验用户账号唯一性
    private boolean checkUnique(String mail) {
        List<UserDO> list = userMapper.selectList(new QueryWrapper<UserDO>().eq("mail", mail));
        return list.size() <= 0;
    }


    // 新用户注册，发放新人优惠券
    private void userRegisterInitTask(UserDO userDO) {
        NewUserCouponRequest request = new NewUserCouponRequest();
        request.setName(userDO.getName());
        request.setUserId(userDO.getId());
        JsonData jsonData = couponFeignService.addNewUserCoupon(request);
        // 服务A调用服务B， 服务B发生异常，由于全局异常处理的存在（@ControllerAdvice）, seata 无法拦截到B服务的异常，从而导致分布式事务未生效
        // 解决方法：业务代码各自判断RPC响应码是否正常，再抛出异常
//        if(jsonData.getCode() != 0){
//            throw new RuntimeException("发放优惠券异常");
//        }
        log.info("发放新用户注册优惠券:{},结果:{}", request, jsonData.toString());
    }


}
