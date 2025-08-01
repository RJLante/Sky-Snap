package com.rd.backend.manager.sharding;

import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import com.rd.backend.model.entity.Space;
import com.rd.backend.model.enums.SpaceLevelEnum;
import com.rd.backend.model.enums.SpaceTypeEnum;
import com.rd.backend.service.SpaceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.driver.jdbc.core.connection.ShardingSphereConnection;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.infra.metadata.database.rule.ShardingSphereRuleMetaData;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.rule.ShardingRule;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

//@Component
@Slf4j
public class DynamicShardingManager {

    @Resource
    private DataSource dataSource;

    @Resource
    private SpaceService spaceService;

    private static final String LOGIC_TABLE_NAME = "picture";

    private static final String DATABASE_NAME = "logic_db"; // 配置文件中的数据库名称

    @PostConstruct
    public void initialize() {
        log.info("初始化动态分表配置...");
        updateShardingTableNodes();
    }

    /**
     * 获取所有动态表名，包括初始表 picture 和分表 picture_{spaceId}
     */
    private Set<String> fetchAllPictureTableNames() {
        // 为了测试方便，直接对所有团队空间分表（实际上线改为仅对旗舰版生效）
        Set<Long> spaceIds = spaceService.lambdaQuery()
                .eq(Space::getSpaceType, SpaceTypeEnum.TEAM.getValue())
                .list()
                .stream()
                .map(Space::getId)
                .collect(Collectors.toSet());
        Set<String> tableNames = spaceIds.stream()
                .map(spaceId -> LOGIC_TABLE_NAME + "_" + spaceId)
                .collect(Collectors.toSet());
        tableNames.add(LOGIC_TABLE_NAME); // 添加初始逻辑表
        return tableNames;
    }

    /**
     * 更新 ShardingSphere 的 actual-data-nodes 动态表名配置
     */
    private void updateShardingTableNodes() {
        Set<String> tableNames = fetchAllPictureTableNames();
        String newActualDataNodes = tableNames.stream()
                .map(tableName -> "picture." + tableName) // 确保前缀合法
                .collect(Collectors.joining(","));
        log.info("动态分表 actual-data-nodes 配置: {}", newActualDataNodes);

        ContextManager contextManager = getContextManager();
//        ShardingSphereRuleMetaData ruleMetaData = contextManager.getMetaDataContexts()
//                .getMetaData()
//                .getDatabases()
//                .get(DATABASE_NAME)
//                .getRuleMetaData();
        // 兼容不同环境下逻辑数据库名称不一致的情况，优先使用配置中的数据库名称，
        // 若未找到则回退至可用的第一个数据库，避免空指针异常
        Map<String, ? extends ShardingSphereDatabase> databaseMap =
                contextManager.getMetaDataContexts().getMetaData().getDatabases();
        String databaseName = DATABASE_NAME;
        if (!databaseMap.containsKey(databaseName)) {
            if (!databaseMap.isEmpty()) {
                databaseName = databaseMap.keySet().iterator().next();
                log.warn("未找到数据库 '{}', 回退使用 '{}' 进行分表配置", DATABASE_NAME, databaseName);
            } else {
                log.error("ShardingSphere 未初始化任何数据库，动态分表更新失败");
                return;
            }
        }

        ShardingSphereRuleMetaData ruleMetaData = databaseMap.get(databaseName).getRuleMetaData();

        Optional<ShardingRule> shardingRule = ruleMetaData.findSingleRule(ShardingRule.class);
        if (shardingRule.isPresent()) {
            ShardingRuleConfiguration ruleConfig = (ShardingRuleConfiguration) shardingRule.get().getConfiguration();
            List<ShardingTableRuleConfiguration> updatedRules = ruleConfig.getTables()
                    .stream()
                    .map(oldTableRule -> {
                        if (LOGIC_TABLE_NAME.equals(oldTableRule.getLogicTable())) {
                            ShardingTableRuleConfiguration newTableRuleConfig = new ShardingTableRuleConfiguration(LOGIC_TABLE_NAME, newActualDataNodes);
                            newTableRuleConfig.setDatabaseShardingStrategy(oldTableRule.getDatabaseShardingStrategy());
                            newTableRuleConfig.setTableShardingStrategy(oldTableRule.getTableShardingStrategy());
                            newTableRuleConfig.setKeyGenerateStrategy(oldTableRule.getKeyGenerateStrategy());
                            newTableRuleConfig.setAuditStrategy(oldTableRule.getAuditStrategy());
                            return newTableRuleConfig;
                        }
                        return oldTableRule;
                    })
                    .collect(Collectors.toList());
            ruleConfig.setTables(updatedRules);
//            contextManager.alterRuleConfiguration(DATABASE_NAME, Collections.singleton(ruleConfig));
//            contextManager.reloadDatabase(DATABASE_NAME);
            contextManager.alterRuleConfiguration(databaseName, Collections.singleton(ruleConfig));
            contextManager.reloadDatabase(databaseName);
            log.info("动态分表规则更新成功！");
        } else {
            log.error("未找到 ShardingSphere 的分片规则配置，动态分表更新失败。");
        }
    }

    /**
     * 获取 ShardingSphere ContextManager
     */
    private ContextManager getContextManager() {
        try (ShardingSphereConnection connection = dataSource.getConnection().unwrap(ShardingSphereConnection.class)) {
            return connection.getContextManager();
        } catch (SQLException e) {
            throw new RuntimeException("获取 ShardingSphere ContextManager 失败", e);
        }
    }


    /**
     * 动态创建空间图片分表
     * @param space
     */
    public void createSpacePictureTable(Space space) {
        // 动态创建分表
        // 仅为旗舰版团队空间创建分表
        if (space.getSpaceType() == SpaceTypeEnum.TEAM.getValue() && space.getSpaceLevel() == SpaceLevelEnum.FLAGSHIP.getValue()) {
            Long spaceId = space.getId();
            String tableName = LOGIC_TABLE_NAME + "_" + spaceId;

            // 创建新表
            String createTableSql = "CREATE TABLE " + tableName + " LIKE " + LOGIC_TABLE_NAME;
            try {
                SqlRunner.db().update(createTableSql);
                // 更新分表
                updateShardingTableNodes();
            } catch (Exception e) {
                log.error("创建图片空间分表失败，空间 id = {}", space.getId());
            }
        }
    }

}
