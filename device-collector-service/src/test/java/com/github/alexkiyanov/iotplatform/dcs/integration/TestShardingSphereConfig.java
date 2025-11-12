package com.github.alexkiyanov.iotplatform.dcs.integration;

import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.config.rule.RuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.apache.shardingsphere.readwritesplitting.api.ReadwriteSplittingRuleConfiguration;
import org.apache.shardingsphere.readwritesplitting.api.rule.ReadwriteSplittingDataSourceRuleConfiguration;
import org.apache.shardingsphere.readwritesplitting.api.strategy.StaticReadwriteSplittingStrategyConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.util.*;

@TestConfiguration
@Profile("integration-test")
public class TestShardingSphereConfig {

    @Bean
    @Primary
    public DataSource shardingSphereDataSource() throws SQLException {
        Map<String, DataSource> dataSourceMap = new HashMap<>();
        
        // Создаем data sources для шардов и реплик
        dataSourceMap.put("ds_0", createHikari("jdbc:postgresql://localhost:" + AbstractBaseTest.postgresShard1.getMappedPort(5432) + "/dcs_shard1"));
        dataSourceMap.put("ds_0_replica", createHikari("jdbc:postgresql://localhost:" + AbstractBaseTest.postgresShard1Replica.getMappedPort(5432) + "/dcs_shard1_replica"));
        dataSourceMap.put("ds_1", createHikari("jdbc:postgresql://localhost:" + AbstractBaseTest.postgresShard2.getMappedPort(5432) + "/dcs_shard2"));
        dataSourceMap.put("ds_1_replica", createHikari("jdbc:postgresql://localhost:" + AbstractBaseTest.postgresShard2Replica.getMappedPort(5432) + "/dcs_shard2_replica"));

        // Конфигурация шардирования
        ShardingRuleConfiguration shardingRule = createShardingRuleConfiguration();
        
        // Конфигурация read-write splitting
        ReadwriteSplittingRuleConfiguration readwriteSplittingRule = createReadwriteSplittingRuleConfiguration();
        
        // Список правил
        Collection<RuleConfiguration> rules = new ArrayList<>();
        rules.add(shardingRule);
        rules.add(readwriteSplittingRule);
        
        // Свойства ShardingSphere
        Properties props = new Properties();
        props.setProperty("sql-show", "true");
        
        // Создаем ShardingSphere DataSource
        return ShardingSphereDataSourceFactory.createDataSource(dataSourceMap, rules, props);
    }

    private ShardingRuleConfiguration createShardingRuleConfiguration() {
        ShardingRuleConfiguration shardingRule = new ShardingRuleConfiguration();
        
        // Конфигурация таблицы device_info
        ShardingTableRuleConfiguration deviceInfoTable = new ShardingTableRuleConfiguration(
                "device_info",
                "readwrite_ds_${0..1}.device_info"
        );
        
        // Стратегия шардирования по device_id
        deviceInfoTable.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration(
                "device_id",
                "device_id_hash_mod"
        ));
        
        shardingRule.getTables().add(deviceInfoTable);
        
        // Алгоритм шардирования
        Properties hashProps = new Properties();
        hashProps.setProperty("sharding-count", "2");
        shardingRule.getShardingAlgorithms().put("device_id_hash_mod", 
                new AlgorithmConfiguration("HASH_MOD", hashProps));
        
        // Привязываем группы readwrite к шардам
        shardingRule.setBindingTableGroups(Collections.singletonList("device_info"));
        
        return shardingRule;
    }
    
    private ReadwriteSplittingRuleConfiguration createReadwriteSplittingRuleConfiguration() {
        // Алгоритм балансировки нагрузки
        Map<String, AlgorithmConfiguration> loadBalancers = new HashMap<>();
        loadBalancers.put("round_robin", new AlgorithmConfiguration("ROUND_ROBIN", new Properties()));
        
        // Конфигурация для первого шарда
        StaticReadwriteSplittingStrategyConfiguration strategy0 = 
                new StaticReadwriteSplittingStrategyConfiguration(
                        "ds_0",
                        Arrays.asList("ds_0_replica")
                );
        
        ReadwriteSplittingDataSourceRuleConfiguration dataSourceRule0 = 
                new ReadwriteSplittingDataSourceRuleConfiguration(
                        "readwrite_ds_0",
                        strategy0,
                        null,
                        "round_robin"
                );
        
        // Конфигурация для второго шарда
        StaticReadwriteSplittingStrategyConfiguration strategy1 = 
                new StaticReadwriteSplittingStrategyConfiguration(
                        "ds_1",
                        Arrays.asList("ds_1_replica")
                );
        
        ReadwriteSplittingDataSourceRuleConfiguration dataSourceRule1 = 
                new ReadwriteSplittingDataSourceRuleConfiguration(
                        "readwrite_ds_1",
                        strategy1,
                        null,
                        "round_robin"
                );
        
        Collection<ReadwriteSplittingDataSourceRuleConfiguration> dataSources = 
                Arrays.asList(dataSourceRule0, dataSourceRule1);
        
        return new ReadwriteSplittingRuleConfiguration(dataSources, loadBalancers);
    }
    
    private static DataSource createHikari(String jdbcUrl) {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername("postgres");
        ds.setPassword("postgres");
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(1);
        ds.setConnectionTimeout(30000);
        ds.setIdleTimeout(600000);
        ds.setMaxLifetime(1800000);
        return ds;
    }
}