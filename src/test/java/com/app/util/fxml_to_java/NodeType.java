package com.app.util.fxml_to_java;

/**
 * 节点类型
 *
 * @author guofan
 * @date 2023/11/24
 */
public enum NodeType {

    Label("Label", "SimpleStringProperty", "javafx.beans.property.SimpleStringProperty", "String","javafx.scene.control.Label"),
    TextField("TextField", "SimpleStringProperty", "javafx.beans.property.SimpleStringProperty", "String","javafx.scene.control.TextField"),
    TextArea("TextArea", "SimpleStringProperty", "javafx.beans.property.SimpleStringProperty", "String","javafx.scene.control.TextArea"),
    Button("Button", "SimpleBooleanProperty", "javafx.beans.property.SimpleBooleanProperty", "boolean","javafx.scene.control.Button");

    private String typeStr;
    private String propStr;
    private String importProp;
    private String originalProp;
    private String importControl;

    NodeType(String typeStr, String propStr, String importProp, String originalProp, String importControl) {
        this.typeStr = typeStr;
        this.propStr = propStr;
        this.importProp = importProp;
        this.originalProp = originalProp;
        this.importControl = importControl;
    }

    public String getTypeStr() {
        return typeStr;
    }

    public String getPropStr() {
        return propStr;
    }

    public String getImportProp() {
        return importProp;
    }

    public String getOriginalProp() {
        return originalProp;
    }

    public String getImportControl() {
        return importControl;
    }
}
