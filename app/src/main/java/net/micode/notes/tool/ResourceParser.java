/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.tool;

import android.content.Context;
import android.preference.PreferenceManager;

import net.micode.notes.R;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * 资源解析工具类
 *
 * 功能：根据业务常量（颜色 ID、字体 ID）获取对应的 Android 资源 ID
 *
 * 设计模式：静态工厂模式，将业务常量映射为资源 ID
 *
 * 支持的颜色主题（5种）：
 * - YELLOW (0) - 黄色
 * - BLUE (1)   - 蓝色
 * - WHITE (2)  - 白色
 * - GREEN (3)  - 绿色
 * - RED (4)    - 红色
 *
 * 支持的字体大小（4种）：
 * - TEXT_SMALL (0)  - 小号
 * - TEXT_MEDIUM (1) - 中号（默认）
 * - TEXT_LARGE (2)  - 大号
 * - TEXT_SUPER (3)  - 超大
 */
public class ResourceParser {

    // ==================== 颜色常量 ====================
    public static final int YELLOW           = 0;
    public static final int BLUE             = 1;
    public static final int WHITE            = 2;
    public static final int GREEN            = 3;
    public static final int RED              = 4;

    /** 默认背景颜色：黄色 */
    public static final int BG_DEFAULT_COLOR = YELLOW;

    // ==================== 字体大小常量 ====================
    public static final int TEXT_SMALL       = 0;
    public static final int TEXT_MEDIUM      = 1;
    public static final int TEXT_LARGE       = 2;
    public static final int TEXT_SUPER       = 3;

    /** 默认字体大小：中号 */
    public static final int BG_DEFAULT_FONT_SIZE = TEXT_MEDIUM;

    /**
     * 笔记编辑页背景资源
     * 每个颜色对应两套资源：
     * - 内容背景（BG_EDIT_RESOURCES）
     * - 标题栏背景（BG_EDIT_TITLE_RESOURCES）
     */
    public static class NoteBgResources {
        /** 编辑页内容背景资源数组，索引与颜色常量对应 */
        private final static int [] BG_EDIT_RESOURCES = new int [] {
                R.drawable.edit_yellow,
                R.drawable.edit_blue,
                R.drawable.edit_white,
                R.drawable.edit_green,
                R.drawable.edit_red
        };

        /** 编辑页标题栏背景资源数组 */
        private final static int [] BG_EDIT_TITLE_RESOURCES = new int [] {
                R.drawable.edit_title_yellow,
                R.drawable.edit_title_blue,
                R.drawable.edit_title_white,
                R.drawable.edit_title_green,
                R.drawable.edit_title_red
        };

        /**
         * 获取编辑页内容背景资源 ID
         * @param id 颜色 ID（0-4）
         */
        public static int getNoteBgResource(int id) {
            return BG_EDIT_RESOURCES[id];
        }

        /**
         * 获取编辑页标题栏背景资源 ID
         * @param id 颜色 ID（0-4）
         */
        public static int getNoteTitleBgResource(int id) {
            return BG_EDIT_TITLE_RESOURCES[id];
        }
    }

    /**
     * 获取默认背景颜色 ID
     *
     * 根据用户设置决定：
     * - 如果开启了随机背景色，返回随机颜色 ID
     * - 否则返回默认黄色
     *
     * @param context 上下文
     * @return 颜色 ID（0-4）
     */
    public static int getDefaultBgId(Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                NotesPreferenceActivity.PREFERENCE_SET_BG_COLOR_KEY, false)) {
            // 随机背景色：范围 0 到 4
            return (int) (Math.random() * NoteBgResources.BG_EDIT_RESOURCES.length);
        } else {
            return BG_DEFAULT_COLOR;
        }
    }

    /**
     * 笔记列表项背景资源
     *
     * 根据笔记在列表中的位置使用不同的背景资源：
     * - 第一项：圆角顶部
     * - 中间项：无圆角
     * - 最后一项：圆角底部
     * - 唯一一项：全圆角
     */
    public static class NoteItemBgResources {
        /** 列表第一项背景资源 */
        private final static int [] BG_FIRST_RESOURCES = new int [] {
                R.drawable.list_yellow_up,
                R.drawable.list_blue_up,
                R.drawable.list_white_up,
                R.drawable.list_green_up,
                R.drawable.list_red_up
        };

        /** 列表中间项背景资源 */
        private final static int [] BG_NORMAL_RESOURCES = new int [] {
                R.drawable.list_yellow_middle,
                R.drawable.list_blue_middle,
                R.drawable.list_white_middle,
                R.drawable.list_green_middle,
                R.drawable.list_red_middle
        };

        /** 列表最后一项背景资源 */
        private final static int [] BG_LAST_RESOURCES = new int [] {
                R.drawable.list_yellow_down,
                R.drawable.list_blue_down,
                R.drawable.list_white_down,
                R.drawable.list_green_down,
                R.drawable.list_red_down,
        };

        /** 列表唯一一项背景资源 */
        private final static int [] BG_SINGLE_RESOURCES = new int [] {
                R.drawable.list_yellow_single,
                R.drawable.list_blue_single,
                R.drawable.list_white_single,
                R.drawable.list_green_single,
                R.drawable.list_red_single
        };

        public static int getNoteBgFirstRes(int id) {
            return BG_FIRST_RESOURCES[id];
        }

        public static int getNoteBgLastRes(int id) {
            return BG_LAST_RESOURCES[id];
        }

        public static int getNoteBgSingleRes(int id) {
            return BG_SINGLE_RESOURCES[id];
        }

        public static int getNoteBgNormalRes(int id) {
            return BG_NORMAL_RESOURCES[id];
        }

        /** 文件夹列表项背景资源（固定，不随颜色变化） */
        public static int getFolderBgRes() {
            return R.drawable.list_folder;
        }
    }

    /**
     * 桌面小部件背景资源
     * 支持 2x2 和 4x4 两种尺寸
     */
    public static class WidgetBgResources {
        /** 2x2 小部件背景资源数组 */
        private final static int [] BG_2X_RESOURCES = new int [] {
                R.drawable.widget_2x_yellow,
                R.drawable.widget_2x_blue,
                R.drawable.widget_2x_white,
                R.drawable.widget_2x_green,
                R.drawable.widget_2x_red,
        };

        public static int getWidget2xBgResource(int id) {
            return BG_2X_RESOURCES[id];
        }

        /** 4x4 小部件背景资源数组 */
        private final static int [] BG_4X_RESOURCES = new int [] {
                R.drawable.widget_4x_yellow,
                R.drawable.widget_4x_blue,
                R.drawable.widget_4x_white,
                R.drawable.widget_4x_green,
                R.drawable.widget_4x_red
        };

        public static int getWidget4xBgResource(int id) {
            return BG_4X_RESOURCES[id];
        }
    }

    /**
     * 字体样式资源
     * 将字体大小常量映射到 Android Style 资源
     */
    public static class TextAppearanceResources {
        /** 字体样式资源数组 */
        private final static int [] TEXTAPPEARANCE_RESOURCES = new int [] {
                R.style.TextAppearanceNormal,
                R.style.TextAppearanceMedium,
                R.style.TextAppearanceLarge,
                R.style.TextAppearanceSuper
        };

        /**
         * 获取字体样式资源 ID
         *
         * @param id 字体大小常量（0-3）
         * @return Style 资源 ID
         */
        public static int getTexAppearanceResource(int id) {
            /**
             * 边界保护：如果 ID 超出资源数组长度，返回默认字体大小
             * 用于修复 SharedPreferences 中存储的资源 ID 过期的 bug
             */
            if (id >= TEXTAPPEARANCE_RESOURCES.length) {
                return BG_DEFAULT_FONT_SIZE;
            }
            return TEXTAPPEARANCE_RESOURCES[id];
        }

        /**
         * 获取字体样式资源数量
         * 用于 SharedPreferences 的边界检查
         */
        public static int getResourcesSize() {
            return TEXTAPPEARANCE_RESOURCES.length;
        }
    }
}