package com.app;

import com.app.view.MainView;
import com.app.view.SplashView;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.generator.AutoGenerator;
import com.baomidou.mybatisplus.generator.InjectionConfig;
import com.baomidou.mybatisplus.generator.config.*;
import com.baomidou.mybatisplus.generator.config.po.TableInfo;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import de.felixroske.jfxsupport.AbstractJavaFxApplicationSupport;
import javafx.stage.Stage;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 主窗口
 *
 * @Author guofan
 * @Create 2022/1/13
 */
@SpringBootApplication
@AutoConfigurationPackage
@MapperScan("com.app.mapper")
public class Main extends AbstractJavaFxApplicationSupport {

    public static void main(String[] args) {
        //自动生成sql代码
//        generateSQLCode();
        launch(Main.class, MainView.class, new SplashView(), args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        super.start(stage);
    }


    @Override
    public void beforeInitialView(Stage stage, ConfigurableApplicationContext ctx) {
        super.beforeInitialView(stage, ctx);
        AppContext.applicationContext = ctx;
    }

    private static void generateSQLCode() {
        AutoGenerator generator = new AutoGenerator();
        String projectPath = System.getProperty("user.dir");
        // 设置FreemarkerTemplateEngine模板引擎
        generator.setTemplateEngine(new FreemarkerTemplateEngine());
        //全局设置
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setOutputDir(System.getProperty("user.dir") + "/src/main/java");
        //作者
        globalConfig.setAuthor("guofan");
        //打开输入目录
        globalConfig.setOpen(false);
        //xml开启BaseResultMap
        globalConfig.setBaseResultMap(true);
        //xml开启BaseColumnList
        globalConfig.setBaseColumnList(true);
        //实体属性Swagger2注解
        globalConfig.setSwagger2(true);
        generator.setGlobalConfig(globalConfig);
        //配置数据源
        DataSourceConfig dataSourceConfig = new DataSourceConfig();
        dataSourceConfig.setUrl("jdbc:sqlite:./data/Database");
        dataSourceConfig.setDriverName("org.sqlite.JDBC");
        dataSourceConfig.setUsername("");
        dataSourceConfig.setPassword("");
        //添加数据源
        generator.setDataSource(dataSourceConfig);
        //包配置
        PackageConfig pc = new PackageConfig();
        pc.setParent("com.app")
                .setEntity("pojo")
                .setMapper("mapper")
                .setService("service")
                .setServiceImpl("service.impl");
//                .setController("controller");
        //设置包
        generator.setPackageInfo(pc);
        //自定义配置
        InjectionConfig cfg = new InjectionConfig() {
            @Override
            public void initMap() {
            }
        };
        //如果模板引擎是freemarker
        String templatePath = "/templates/mapper.xml.ftl";
        //自定义输出配置
        List focList = new ArrayList<>();
        //自定义配置会被优先输出
        focList.add(new FileOutConfig(templatePath) {
            @Override
            public String outputFile(TableInfo tableInfo) {
                return projectPath + "/src/main/resources/mapper/" + tableInfo.getEntityName() + "Mapper" + StringPool.DOT_XML;
            }
        });
        cfg.setFileOutConfigList(focList);
        generator.setCfg(cfg);
        TemplateConfig templateConfig = new TemplateConfig();
        templateConfig.setXml(null);
        generator.setTemplate(templateConfig);
        //策略设置
        StrategyConfig strategy = new StrategyConfig();
        //数据库表映射到实体的名字策略  下划线转换成驼峰
        strategy.setNaming(NamingStrategy.underline_to_camel);
        //lombok模型
        strategy.setEntityLombokModel(true);
        //生成@RestController控制器
        strategy.setRestControllerStyle(true);
        strategy.setInclude(scanner("表名，多个英文逗号分隔").split(","));
        strategy.setControllerMappingHyphenStyle(true);
        //表前缀
        strategy.setTablePrefix("t_");
        //添加策略
        generator.setStrategy(strategy);
        generator.execute();
    }

    private static String scanner(String tip) {
        Scanner scanner = new Scanner(System.in);
        StringBuilder help = new StringBuilder();
        help.append("请输入" + tip + "：");
        System.out.println(help.toString());
        if (scanner.hasNext()) {
            String ipt = scanner.next();
            if (ipt != null || ipt.equals("")) {
                return ipt;
            }
        }
        throw new MybatisPlusException("请输入正确的" + tip + "!");
    }


}
