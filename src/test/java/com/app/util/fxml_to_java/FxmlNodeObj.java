package com.app.util.fxml_to_java;

/**
 * fxml 节点对象
 *
 * @author guofan
 * @date 2023/11/24
 */
public class FxmlNodeObj {
    /**
     * 节点类型
     */
    private NodeType nodeType;
    /**
     * 节点id
     */
    private String nodeId;

    /**
     * 节点名称
     */
    private String nodeName;

    /**
     * 方法
     */
    private String action;


    public NodeType getNodeType() {
        return nodeType;
    }

    public void setNodeType(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    @Override
    public String toString() {
        return "FxmlNodeObj{" +
                "nodeType=" + nodeType +
                ", nodeId='" + nodeId + '\'' +
                ", nodeName='" + nodeName + '\'' +
                '}';
    }
}
