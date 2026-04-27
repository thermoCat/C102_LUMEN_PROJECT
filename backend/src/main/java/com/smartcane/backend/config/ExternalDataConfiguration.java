package com.smartcane.backend.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class ExternalDataConfiguration {

    @Bean
    @Primary
    @Qualifier("postgresDataSource")
    @ConditionalOnExpression("'${app.datasource.postgres.host:}' != ''")
    public DataSource postgresDataSource(PostgresProperties properties) {
        return buildDataSource(
            "org.postgresql.Driver",
            "jdbc:postgresql://%s:%d/%s".formatted(
                properties.getHost(),
                properties.getPort(),
                properties.getDatabase()
            ),
            properties.getUsername(),
            properties.getPassword()
        );
    }

    @Bean
    @Qualifier("mysqlDataSource")
    @ConditionalOnExpression("'${app.datasource.mysql.host:}' != ''")
    public DataSource mysqlDataSource(MysqlProperties properties) {
        return buildDataSource(
            "com.mysql.cj.jdbc.Driver",
            "jdbc:mysql://%s:%d/%s".formatted(
                properties.getHost(),
                properties.getPort(),
                properties.getDatabase()
            ),
            properties.getUsername(),
            properties.getPassword()
        );
    }

    @Bean
    @ConditionalOnExpression("'${app.redis.host:}' != ''")
    public LettuceConnectionFactory redisConnectionFactory(RedisProperties properties) {
        RedisStandaloneConfiguration configuration =
            new RedisStandaloneConfiguration(properties.getHost(), properties.getPort());

        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            configuration.setPassword(RedisPassword.of(properties.getPassword()));
        }

        return new LettuceConnectionFactory(configuration);
    }

    @Bean
    @ConditionalOnExpression(
        "'${app.aws.access-key-id:}' != '' and '${app.aws.secret-access-key:}' != ''"
    )
    public S3Client s3Client(AwsProperties properties) {
        return S3Client.builder()
            .region(Region.of(properties.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        properties.getAccessKeyId(),
                        properties.getSecretAccessKey()
                    )
                )
            )
            .build();
    }

    @Bean
    @ConditionalOnExpression(
        "'${app.aws.access-key-id:}' != '' and '${app.aws.secret-access-key:}' != ''"
    )
    public S3Presigner s3Presigner(AwsProperties properties) {
        return S3Presigner.builder()
            .region(Region.of(properties.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        properties.getAccessKeyId(),
                        properties.getSecretAccessKey()
                    )
                )
            )
            .build();
    }

    private DataSource buildDataSource(String driverClassName, String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driverClassName);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(0);
        config.setInitializationFailTimeout(-1);
        config.setPoolName(driverClassName + "-pool");
        return new HikariDataSource(config);
    }
}
