package com.ordererp.backend.system.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MenuRouterDto {
    private String name;
    private String path;
    private String component;
    private MenuMetaDto meta;
    private List<MenuRouterDto> children = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public MenuMetaDto getMeta() {
        return meta;
    }

    public void setMeta(MenuMetaDto meta) {
        this.meta = meta;
    }

    public List<MenuRouterDto> getChildren() {
        return children;
    }

    public void setChildren(List<MenuRouterDto> children) {
        this.children = children;
    }
}
