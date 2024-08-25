package com.app.util.fxml_to_java;


import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 根据Fxml生成javaFx view 、ViewMode
 *
 * @author guofan
 * @date 2023/11/24
 */

public class GenerateJFXCode {
    /**
     * 日志
     */
    private final Logger logger = LoggerFactory.getLogger(GenerateJFXCode.class);

    /**
     * 模块名字
     */
    private final String baseName = "Config";
    /**
     * 文件保存根目录
     */
    private String rootDir = "E:\\javaProject\\BaseApp\\src\\main\\java\\com\\app";
    /**
     * fxml 路径
     */
    private String xmlFile = "./com/app/view/configView.fxml";
    /**
     * 解析后的节点列表
     */
    private final List<FxmlNodeObj> fxmlNodeObjList = new ArrayList<>();
    /**
     * 属性后缀
     */
    private final String PROP_SUFFIX = "Prop";

//    @Test
    void generateCode() {
        parseFxm(xmlFile);
        // 生成或更新view and viewModel
        generateOrUpdateViewAndViewModel(fxmlNodeObjList, baseName, rootDir);

    }

    void parseFxm(String xmlFile) {
        try {
            // 创建 DocumentBuilderFactory 和 DocumentBuilder
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            // 解析 XML 文件
            Document document = builder.parse(getClass().getClassLoader().getResourceAsStream(xmlFile));

            // 获取根元素
            Element rootElement = document.getDocumentElement();

            // 递归遍历 XML 结构
            traverseXML(rootElement);
            // 打印属性列表
            fxmlNodeObjList.forEach(fxmlNodeObj -> {
                logger.info("解析后的对象是:{}", fxmlNodeObj);
            });


        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 更新或者创建view viewModel 文件
     *
     * @param fxmlNodeObjList 节点列表
     * @param baseName        基础名字
     * @param rootDir         文件根目录
     */
    private void generateOrUpdateViewAndViewModel(List<FxmlNodeObj> fxmlNodeObjList, String baseName, String rootDir) {
        String viewModelPath = rootDir + "\\view_model\\" + baseName + "ViewModel.java";
        String viewPath = rootDir + "\\view\\" + baseName + "View.java";
        final File viewModelFile = new File(viewModelPath);
        final File viewFile = new File(viewPath);
        if (viewModelFile.exists()) {
            // 更新文件 todo
            String viewModelNewPath = rootDir + "\\view_model\\" + baseName + "New" + "ViewModel.java";
            String viewNewPath = rootDir + "\\view\\" + baseName + "New" + "View.java";
            final File viewModelNewFile = new File(viewModelNewPath);
            final File viewNewFile = new File(viewNewPath);
            // 创建viewModel
            try {
                generateViewModelFile(viewModelNewFile, fxmlNodeObjList);
                generateView(viewNewFile, fxmlNodeObjList);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // 创建View
        } else {
            try {
                // 创建viewModel
                generateViewModelFile(viewModelFile, fxmlNodeObjList);
                // 创建View
                generateView(viewFile, fxmlNodeObjList);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }

    /**
     * 创建View
     *
     * @param viewFile        view 文件
     * @param fxmlNodeObjList 节点列表
     */
    private void generateView(File viewFile, List<FxmlNodeObj> fxmlNodeObjList) throws IOException {
        // 生成对象
        List<String> writeCache = new ArrayList<>();
        // 添加第一行
        final String packageName = convertToPackagePath(viewFile.getParent());
        final String baseFilename = viewFile.getName();
        String baseName = baseFilename.substring(0, baseFilename.lastIndexOf("."));
        writeCache.add("package " + packageName + ";\n" +
                "\n");

        writeCache.add("import com.app.view_model." + baseName + "Model;");
        writeCache.add("import javafx.fxml.FXML;");

        writeCache.add("\n");
        // 写入导入
        final Set<String> imports = fxmlNodeObjList.stream().map(fxmlNodeObj -> fxmlNodeObj.getNodeType().getImportControl()).collect(Collectors.toSet());
        imports.forEach(imp -> {
            writeCache.add("import " + imp + ";");
        });
        writeCache.add("\n");

        // 类名
        final String filename = viewFile.getName();
        final int lastIndexOf = filename.lastIndexOf(".");
        final String fileBaseName = filename.substring(0, lastIndexOf);
        writeCache.add("public class " + fileBaseName + " {");
        writeCache.add("\n");

        // 属性类
        fxmlNodeObjList.forEach(fxmlNodeObj -> {

            String propLine = "    public " + fxmlNodeObj.getNodeType().getTypeStr() + " " + fxmlNodeObj.getNodeId() + ";";
            writeCache.add(propLine);
            writeCache.add("\n");
        });

        // 写ViewModel
        writeCache.add("    /**\n" +
                "     * ViewModel\n" +
                "     */\n" +
                "    private " + baseName + "Model" + " viewModel;");
        writeCache.add("\n");
        // 初始化方法
        writeCache.add("    @FXML\n" +
                "    public void initialize() {\n" +
                "        // 数据初始化\n" +
                "        initData();\n" +
                "        // 绑定属性\n" +
                "        binding();\n" +
                "    }");
        writeCache.add("\n");
        // 初始化数据方法

        writeCache.add("    /**\n" +
                "     * 初始化数据\n" +
                "     */\n" +
                "    private void initData() {\n" +
                "        viewModel = " + fileBaseName + "Model.getInstance();\n" +
                "    }");
        writeCache.add("\n");

        // 绑定ViewModel
        writeCache.add("    private void binding() {");
        fxmlNodeObjList.forEach(fxmlNodeObj -> {
            String bindingStr;
            switch (fxmlNodeObj.getNodeType()) {
                case Label: {

                }
                case TextField: {
                    final String propNameProp = fxmlNodeObj.getNodeId().replace(capitalizeFirstLetter(fxmlNodeObj.getNodeType().getTypeStr()), "") + PROP_SUFFIX + "Property()";
                    bindingStr = "        " + fxmlNodeObj.getNodeId() + ".textProperty().bindBidirectional(viewModel." + propNameProp + ");";
                    writeCache.add(bindingStr);
                }

                case Button: {
                    bindingStr = "";
                }
                default: {

                }
            }

        });

        writeCache.add("    }");
        writeCache.add("\n");

        // 如果是按钮如果有方法生成方法
        List<FxmlNodeObj> buttonList = fxmlNodeObjList.stream().filter(fxmlNodeObj -> fxmlNodeObj.getNodeType().equals(NodeType.Button)).collect(Collectors.toList());
        buttonList.forEach(fxmlNodeObj -> {
            if (StringUtils.isNotEmpty(fxmlNodeObj.getAction())) {
                String action = fxmlNodeObj.getAction();

                writeCache.add("    public void " + action.substring(1) + "() {\n" +
                        "\n" +
                        "    }");
                writeCache.add("\n");
            }

        });

        // 结束
        writeCache.add("}");

        // 生成代码
        generateCodeFile(viewFile, writeCache);
    }

    /**
     * 创建ViewModel
     *
     * @param viewModelFile   viewModel文件
     * @param fxmlNodeObjList 节点列表
     */
    private void generateViewModelFile(File viewModelFile, List<FxmlNodeObj> fxmlNodeObjList) throws IOException {
        // 生成对象
        List<String> writeCache = new ArrayList<>();
        // 添加第一行
        final String packageName = convertToPackagePath(viewModelFile.getParent());

        writeCache.add("package " + packageName + ";\n");
        writeCache.add("\n");
        // 写入导入
        final Set<String> imports = fxmlNodeObjList.stream().map(fxmlNodeObj -> fxmlNodeObj.getNodeType().getImportProp()).collect(Collectors.toSet());
        imports.forEach(imp -> {
            writeCache.add("import " + imp + ";");
        });
        writeCache.add("\n");

        // 类名
        final String filename = viewModelFile.getName();
        final int lastIndexOf = filename.lastIndexOf(".");
        final String fileBaseName = filename.substring(0, lastIndexOf);
        writeCache.add("public class " + fileBaseName + " {");
        writeCache.add("\n");
        // 单例方法
        writeCache.add("    private static " + fileBaseName + " viewModel;");
        writeCache.add("\n");

        // 属性类
        fxmlNodeObjList.forEach(fxmlNodeObj -> {
            final String propName = fxmlNodeObj.getNodeId().replace(capitalizeFirstLetter(fxmlNodeObj.getNodeType().getTypeStr()), "") + PROP_SUFFIX;
            String propLine = "    private final " + fxmlNodeObj.getNodeType().getPropStr() + " " + propName + " " + "=" + "new " + fxmlNodeObj.getNodeType().getPropStr() + "();";
            writeCache.add(propLine);
            writeCache.add("\n");
        });


        // 初始化方法
        writeCache.add("    /**\n" +
                "     * 单例模式\n" +
                "     *\n" +
                "     * @return 单例\n" +
                "     */\n" +
                "    public static " + fileBaseName + " getInstance() {\n" +
                "        if (viewModel == null) {\n" +
                "            viewModel = new " + fileBaseName + "();\n" +
                "        }\n" +
                "        return viewModel;\n" +
                "    }");


        writeCache.add("\n");
        // get set
        fxmlNodeObjList.forEach(fxmlNodeObj -> {
            final String propName = fxmlNodeObj.getNodeId().replace(capitalizeFirstLetter(fxmlNodeObj.getNodeType().getTypeStr()), "") + PROP_SUFFIX;
            final String propGetSetStr = generatePropertyCode(propName, fxmlNodeObj.getNodeType());
            writeCache.add(propGetSetStr);
            writeCache.add("\n");
        });

        // 结束
        writeCache.add("}");

        // 生成代码
        generateCodeFile(viewModelFile, writeCache);
    }

    /**
     * 生成代码文件
     *
     * @param file       代码文件
     * @param writeCache 要写入的数据
     * @throws IOException 异常
     */
    private void generateCodeFile(File file, List<String> writeCache) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(file);
             OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8);
             BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter);) {
            for (final String line : writeCache) {
                bufferedWriter.write(line);
                bufferedWriter.newLine();
                bufferedWriter.flush();
            }
        }
    }


    private void traverseXML(Element element) {
        // 输出当前标签的名称
        System.out.println("Tag: " + element.getTagName());
        final FxmlNodeObj nodeObj = new FxmlNodeObj();
        // 获取当前标签的所有属性
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            System.out.println("Attribute: " + attribute.getNodeName() + " = " + attribute.getNodeValue());
            if ("fx:id".equals(attribute.getNodeName())) {
                nodeObj.setNodeId(attribute.getNodeValue());
            }
            if ("text".equals(attribute.getNodeName())) {
                nodeObj.setNodeName(attribute.getNodeValue());
            }
            if ("onAction".equals(attribute.getNodeName())) {
                nodeObj.setAction(attribute.getNodeValue());
            }
        }
        if (StringUtils.isNotEmpty(nodeObj.getNodeId())) {
            nodeObj.setNodeType(NodeType.valueOf(element.getTagName()));
            fxmlNodeObjList.add(nodeObj);
        }
        // 获取当前标签的子节点
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                // 递归遍历子节点
                traverseXML((Element) child);
            }
        }
    }

    /**
     * 将路径转换为包名工具类
     *
     * @param filePath 根路径
     * @return 包名
     */
    private String convertToPackagePath(String filePath) {
        // 替换文件分隔符为点号
        String packagePath = filePath.replace("\\", ".");

        // 使用正则表达式匹配并替换字符串
        packagePath = packagePath.replaceAll("^.*java\\.", "");

        return packagePath;
    }

    private String generatePropertyCode(String methodName, NodeType propertyType) {
        //转换大小写
        String capitalizedMethodName = capitalizeFirstLetter(methodName);

        StringBuilder code = new StringBuilder();
        // 生成get
        code.append("    public ").append(propertyType.getOriginalProp()).append(" get").append(capitalizedMethodName).append("() {\n");
        code.append("        return ").append(methodName).append(".get();\n");
        code.append("    }\n\n");
        // 生成获取属性
        code.append("    public ").append(propertyType.getPropStr()).append(" ").append(methodName).append("Property() {\n");
        code.append("        return ").append(methodName).append(";\n");
        code.append("    }\n\n");
        // 生成set
        code.append("    public void set").append(capitalizedMethodName).append("(").append(propertyType.getOriginalProp()).append(" ").append(methodName).append(") {\n");
        code.append("        this.").append(methodName).append(".set(").append(methodName).append(");\n");
        code.append("    }");

        return code.toString();
    }

    /**
     * 首字母小写
     */
    private String capitalizeFirstLetter(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

}
