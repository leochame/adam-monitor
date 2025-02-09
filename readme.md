# Adam 全链路监控系统

```` xml
<appender name="CUSTOM" class="com.adam.appender.CustomAppender">
    <systemName>xxxx</systemName>
    <groupId>com.adam</groupId>
    <host>xxxx</host>
    <port>xxxx</port>
    <pushType>redis<pushType>
</appender>
````
````sql
CREATE TABLE log_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    system_name VARCHAR(255),
    class_name VARCHAR(255),
    method_name VARCHAR(255),
    log_content TEXT
);

````
