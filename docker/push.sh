#登录阿里云镜像仓
docker login --username=java教父 registry.cn-hangzhou.aliyuncs.com --password=tt1.131619


#构建整个项目，或者单独构建common项目，避免依赖未被构建上去
cd ../xdclass-common
mvn install


#构建网关
cd ../xdclass-gateway
mvn install -Dmaven.test.skip=true dockerfile:build
docker tag xdclass-cloud/xdclass-gateway:latest registry.cn-hangzhou.aliyuncs.com/tt-xdclass-cloud/api-gateway:v1.1
docker push registry.cn-hangzhou.aliyuncs.com/tt-xdclass-cloud/api-gateway:v1.1
echo "网关构建推送成功"


#用户服务
cd ../xdclass-user-service
mvn install -Dmaven.test.skip=true dockerfile:build
docker tag xdclass-cloud/xdclass-user-service:latest registry.cn-hangzhou.aliyuncs.com/tt-xdclass-cloud/xdclass-user-service:v1.1
docker push registry.cn-hangzhou.aliyuncs.com/tt-xdclass-cloud/xdclass-user-service:v1.1
echo "用户服务构建推送成功"


#商品服务
cd ../xdclass-product-service
mvn install -Dmaven.test.skip=true dockerfile:build
docker tag xdclass-cloud/xdclass-product-service:latest registry.cn-hangzhou.aliyuncs.com/tt-xdclass-cloud/xdclass-product-service:v1.1
docker push registry.cn-hangzhou.aliyuncs.com/tt-xdclass-cloud/xdclass-product-service:v1.1
echo "商品服务构建推送成功"


#订单服务
cd ../xdclass-order-service
mvn install -Dmaven.test.skip=true dockerfile:build
docker tag xdclass-cloud/xdclass-order-service:latest registry.cn-hangzhou.aliyuncs.com/tt-xdclass-cloud/xdclass-order-service:v1.1
docker push registry.cn-hangzhou.aliyuncs.com/tt-xdclass-cloud/xdclass-order-service:v1.1
echo "订单服务构建推送成功"


#优惠券服务
cd ../xdclass-coupon-service
mvn install -Dmaven.test.skip=true dockerfile:build
docker tag xdclass-cloud/xdclass-coupon-service:latest registry.cn-hangzhou.aliyuncs.com/tt-xdclass-cloud/xdclass-coupon-service:v1.1
docker push registry.cn-hangzhou.aliyuncs.com/tt-xdclass-cloud/xdclass-coupon-service:v1.1
echo "优惠券服务构建推送成功"


echo "=======构建脚本执行完毕====="