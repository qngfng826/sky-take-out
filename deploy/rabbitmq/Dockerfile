# 基于 RabbitMQ 官方镜像加装“延迟消息插件”（改造② 依赖 x-delayed-message 交换机）
# 插件版本必须与 Broker 版本匹配：3.13.0 ↔ rabbitmq:3.13-management
FROM rabbitmq:3.13-management

COPY rabbitmq_delayed_message_exchange-3.13.0.ez /opt/rabbitmq/plugins/

# 离线启用：容器启动即自带插件，无需再 docker exec 手动 enable
RUN rabbitmq-plugins enable --offline rabbitmq_delayed_message_exchange
