package com.rd.backend.untils;

import java.awt.*;

public class ColorTransformUtils {

    private ColorTransformUtils() {

    }

    /**
     * 获取标准颜色（将数据万象的 5 位色值转为 6 位）
     * @return
     */
    public static String getStandardColor(String color) {
        // 0x080e0 => 0x0800e0
        if (color.length() == 7) {
            color = color.substring(0, 4) + "0" + color.substring(4, 7);
        }
        return color;
    }
}
