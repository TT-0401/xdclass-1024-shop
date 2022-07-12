package net.xdclass.config;

import feign.RequestInterceptor;
import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

@Configuration
@Data
public class AppConfig {

    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.port}")
    private String redisPort;

    @Value("${spring.redis.password}")
    private String redisPwd;


    /**
     * 配置分布式锁的redisson
     * @return
     */
    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();

        //单机方式
        config.useSingleServer().setPassword(redisPwd).setAddress("redis://" + redisHost + ":" + redisPort);

        //集群方式
        //config.useClusterServers().addNodeAddress("redis://192.31.21.1:6379", "redis://192.31.21.2:6379")

        RedissonClient redissonClient = Redisson.create(config);
        return redissonClient;
    }


    /**
     * feign调用丢失token解决方式，新增拦截器
     * @return
     */
    @Bean
    public RequestInterceptor requestInterceptor() {

        return template -> {

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {

                HttpServletRequest httpServletRequest = attributes.getRequest();

                String token = httpServletRequest.getHeader("token");
                template.header("token", token);
            }
        };
    }
}
